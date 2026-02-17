package vc.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vc.api.model.ProfileData;
import vc.api.model.ProfileDataImpl;
import vc.config.watch.GuildChatWatchRepository;
import vc.config.watch.GuildPlayerWatchRepository;
import vc.config.watch.model.GuildChatWatchConfig;
import vc.config.watch.model.GuildPlayerWatchConfig;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static vc.util.DiscordMarkdownEscape.escape;

@Component
public class WatchGuildCommand implements SlashCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchGuildCommand.class);
    private final GuildChatWatchRepository guildChatWatchRepository;
    private final GuildPlayerWatchRepository guildPlayerWatchRepository;
    private final PlayerLookup playerLookup;

    public WatchGuildCommand(
        final GuildChatWatchRepository guildChatWatchRepository,
        final GuildPlayerWatchRepository guildPlayerWatchRepository,
        final PlayerLookup playerLookup
    ) {
        this.guildChatWatchRepository = guildChatWatchRepository;
        this.guildPlayerWatchRepository = guildPlayerWatchRepository;
        this.playerLookup = playerLookup;
    }

    @Override
    public String getName() {
        return "watch-guild";
    }

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) return error(event, "This command can only be used inside a discord server");
        if (!validateUserPermissions(event)) return error(event, "You must have permission: " + Permission.MESSAGE_MANAGE + " to use this command");

        var group = event.getSubcommandGroup();
        var sub = event.getSubcommandName();
        if (group == null || sub == null) return error(event, "Unknown command option");
        return switch (group) {
            case "chat" -> handleChatWatch(event, sub);
            case "player" -> handlePlayerWatch(event, sub);
            default -> error(event, "Unknown command option");
        };
    }

    private WebhookMessageCreateAction<Message> handleChatWatch(final SlashCommandInteractionEvent event, final String sub) {
        var guildId = event.getGuild().getId();
        if ("add".equals(sub)) {
            var channel = Optional.ofNullable(event.getOption("channel", OptionMapping::getAsChannel))
                .filter(GuildMessageChannel.class::isInstance)
                .map(GuildMessageChannel.class::cast)
                .orElse(null);
            if (channel == null) return error(event, "Channel option is required to add a watch");
            if (!testPermissions(guildId, channel)) return error(event, "Bot must have permissions to send messages in: " + channel.getAsMention());

            String keyword = Optional.ofNullable(event.getOption("keyword", OptionMapping::getAsString)).orElse("").replace("< >", " ");
            if (keyword.length() < 3 || keyword.length() > 50) return error(event, "Keyword must be between 3 and 50 characters");
            if (!Validator.isValidChat(keyword)) return error(event, "Keyword contains invalid chat characters");

            boolean caseSensitive = Optional.ofNullable(event.getOption("case-sensitive", OptionMapping::getAsBoolean)).orElse(false);
            String mentionUserId = "";
            String mentionRoleId = "";
            IMentionable mentionable = event.getOption("mention", OptionMapping::getAsMentionable);
            if (mentionable != null) {
                if (mentionable instanceof Role) mentionRoleId = mentionable.getId();
                else mentionUserId = mentionable.getId();
            }

            var watch = new GuildChatWatchConfig(
                String.valueOf(Instant.now().toEpochMilli()),
                guildId,
                event.getGuild().getName(),
                channel.getId(),
                keyword,
                caseSensitive,
                mentionUserId,
                mentionRoleId
            );
            var existingWatches = guildChatWatchRepository.getByGuildId(guildId);
            for (var w : existingWatches) if (w.keyword().equals(keyword)) guildChatWatchRepository.delete(w);
            guildChatWatchRepository.write(watch);
            return event.getHook().sendMessageEmbeds(embed(event)
                .setColor(Color.SEA_GREEN)
                .setTitle("Chat Watch Added")
                .setDescription("Notifications on chats containing `%s` will be sent to: %s".formatted(keyword, channel.getAsMention()))
                .build());
        }
        if ("delete".equals(sub)) {
            String keyword = Optional.ofNullable(event.getOption("keyword", OptionMapping::getAsString)).orElse("").replace("< >", " ");
            if (keyword.length() < 3 || keyword.length() > 50) return error(event, "Keyword must be between 3 and 50 characters");
            var watches = guildChatWatchRepository.getByGuildId(guildId);
            for (var watch : watches) {
                if (watch.keyword().equals(keyword)) {
                    guildChatWatchRepository.delete(watch);
                    return event.getHook().sendMessageEmbeds(embed(event)
                        .setTitle("Chat Watch Deleted")
                        .setColor(Color.SEA_GREEN)
                        .setDescription("Chat Watch for `%s` deleted!".formatted(keyword))
                        .build());
                }
            }
            return error(event, "No chat watch found for `%s`".formatted(keyword));
        }
        if ("list".equals(sub)) {
            var watches = guildChatWatchRepository.getByGuildId(guildId);
            Collections.sort(watches, (a, b) -> {
                int c = a.channelId().compareToIgnoreCase(b.channelId());
                if (c != 0) return c;
                return a.keyword().compareTo(b.keyword());
            });
            StringBuilder builder = new StringBuilder();
            if (watches.isEmpty()) builder.append("None!\n");
            else {
                for (var watch : watches) {
                    builder.append("`").append(watch.keyword()).append("`");
                    if (watch.caseSensitive()) builder.append(" (case-sensitive)");
                    builder.append("\n");
                }
            }
            var description = builder.toString();
            if (description.length() > 4000) description = description.substring(0, 4000) + "\n... (truncated)";
            return event.getHook().sendMessageEmbeds(embed(event)
                .setTitle("Chat Watch List")
                .setDescription(description)
                .setColor(Color.CYAN)
                .build());
        }
        if ("clear".equals(sub)) {
            var watches = guildChatWatchRepository.getByGuildId(guildId);
            for (var watch : watches) guildChatWatchRepository.delete(watch);
            return event.getHook().sendMessageEmbeds(embed(event)
                .setTitle("All Chat Watches Cleared")
                .setDescription("Removed %d watches.\n".formatted(watches.size()) + watches.stream().map(GuildChatWatchConfig::keyword).map("`%s`"::formatted).reduce("", (a, b) -> a + "\n" + b))
                .setColor(Color.CYAN)
                .build());
        }
        return error(event, "Unknown command option");
    }

    private WebhookMessageCreateAction<Message> handlePlayerWatch(final SlashCommandInteractionEvent event, final String sub) {
        var guildId = event.getGuild().getId();
        if ("add".equals(sub)) {
            var profile = resolveProfile(event, "player");
            if (profile.isEmpty()) return error(event, "Invalid player name");
            var channel = Optional.ofNullable(event.getOption("channel", OptionMapping::getAsChannel))
                .filter(GuildMessageChannel.class::isInstance)
                .map(GuildMessageChannel.class::cast)
                .orElse(null);
            if (channel == null) return error(event, "Channel option is required to add a watch");
            if (!testPermissions(guildId, channel)) return error(event, "Bot must have permissions to send messages in: " + channel.getAsMention());

            boolean joins = Optional.ofNullable(event.getOption("joins", OptionMapping::getAsBoolean)).orElse(true);
            boolean leaves = Optional.ofNullable(event.getOption("leaves", OptionMapping::getAsBoolean)).orElse(true);
            boolean chats = Optional.ofNullable(event.getOption("chats", OptionMapping::getAsBoolean)).orElse(true);
            boolean deaths = Optional.ofNullable(event.getOption("deaths", OptionMapping::getAsBoolean)).orElse(true);
            boolean kills = Optional.ofNullable(event.getOption("kills", OptionMapping::getAsBoolean)).orElse(true);
            if (!joins && !leaves && !chats && !deaths && !kills) return error(event, "At least one event type must be enabled");

            String mentionUserId = "";
            String mentionRoleId = "";
            IMentionable mentionable = event.getOption("mention", OptionMapping::getAsMentionable);
            if (mentionable != null) {
                if (mentionable instanceof Role) mentionRoleId = mentionable.getId();
                else mentionUserId = mentionable.getId();
            }

            var p = profile.get();
            var watch = new GuildPlayerWatchConfig(
                String.valueOf(Instant.now().toEpochMilli()),
                guildId,
                event.getGuild().getName(),
                channel.getId(),
                joins, leaves, chats, deaths, kills,
                mentionUserId,
                mentionRoleId,
                p.uuid(),
                p.name()
            );
            var existingWatches = guildPlayerWatchRepository.getByGuildId(guildId);
            for (var w : existingWatches) if (w.targetUuid().equals(p.uuid())) guildPlayerWatchRepository.delete(w);
            guildPlayerWatchRepository.write(watch);

            StringBuilder eventsList = new StringBuilder();
            if (joins) eventsList.append("Joins, ");
            if (leaves) eventsList.append("Leaves, ");
            if (chats) eventsList.append("Chats, ");
            if (deaths) eventsList.append("Deaths, ");
            if (kills) eventsList.append("Kills, ");
            if (!eventsList.isEmpty()) eventsList.setLength(eventsList.length() - 2);
            return event.getHook().sendMessageEmbeds(populateIdentity(embed(event), p)
                .setColor(Color.SEA_GREEN)
                .setTitle("Player Watch Added")
                .setDescription("Notifications on `%s` events for `%s` will be sent to: %s".formatted(eventsList, p.name(), channel.getAsMention()))
                .setThumbnail(p.getAvatarURL())
                .build());
        }
        if ("delete".equals(sub)) {
            var playerName = Optional.ofNullable(event.getOption("player", OptionMapping::getAsString)).map(String::trim).orElse("");
            if (playerName.isEmpty()) return error(event, "Player name required");
            if (!Validator.isValidPlayerName(playerName)) return error(event, "Invalid player name");
            var profile = playerLookup.getPlayerIdentity(playerName).orElse(new ProfileDataImpl(playerName, UUID.randomUUID()));
            var watches = guildPlayerWatchRepository.getByGuildId(guildId);
            for (var watch : watches) {
                if (watch.targetName().equalsIgnoreCase(playerName)) {
                    guildPlayerWatchRepository.delete(watch);
                    return event.getHook().sendMessageEmbeds(populateIdentity(embed(event), profile)
                        .setTitle("Player Watch Deleted")
                        .setColor(Color.SEA_GREEN)
                        .setDescription("Player watch for `%s` deleted!".formatted(profile.name()))
                        .setThumbnail(profile.getAvatarURL())
                        .build());
                }
            }
            return error(event, "No player watch found for `%s` (%s)".formatted(profile.name(), profile.uuid()));
        }
        if ("list".equals(sub)) {
            var watches = guildPlayerWatchRepository.getByGuildId(guildId);
            Collections.sort(watches, (a, b) -> {
                int c = a.channelId().compareToIgnoreCase(b.channelId());
                if (c != 0) return c;
                return a.targetName().compareToIgnoreCase(b.targetName());
            });
            StringBuilder builder = new StringBuilder();
            if (watches.isEmpty()) builder.append("None!\n");
            else {
                for (var watch : watches) {
                    builder.append(escape(watch.targetName()));
                    boolean anyEnabled = watch.joins() || watch.leaves() || watch.chats() || watch.deaths() || watch.kills();
                    if (anyEnabled) {
                        builder.append(" - <#").append(watch.channelId()).append("> - ");
                        if (watch.joins()) builder.append("Joins, ");
                        if (watch.leaves()) builder.append("Leaves, ");
                        if (watch.chats()) builder.append("Chats, ");
                        if (watch.deaths()) builder.append("Deaths, ");
                        if (watch.kills()) builder.append("Kills, ");
                        builder.setLength(builder.length() - 2);
                    } else {
                        builder.append(" - Disabled");
                    }
                    builder.append("\n");
                }
            }
            var description = builder.toString();
            if (description.length() > 4000) description = description.substring(0, 4000) + "\n... (truncated)";
            return event.getHook().sendMessageEmbeds(embed(event)
                .setTitle("Player Watch List")
                .setDescription(description)
                .setColor(Color.CYAN)
                .build());
        }
        if ("clear".equals(sub)) {
            var watches = guildPlayerWatchRepository.getByGuildId(guildId);
            for (var watch : watches) guildPlayerWatchRepository.delete(watch);
            return event.getHook().sendMessageEmbeds(embed(event)
                .setTitle("All Player Watches Cleared")
                .setDescription("Removed %d watches.\n".formatted(watches.size()) + watches.stream().map(GuildPlayerWatchConfig::targetName).reduce("", (a, b) -> a + "\n" + b))
                .setColor(Color.CYAN)
                .build());
        }
        return error(event, "Unknown command option");
    }

    private Optional<ProfileData> resolveProfile(SlashCommandInteractionEvent event, String optionName) {
        var playerNameOptional = Optional.ofNullable(event.getOption(optionName, OptionMapping::getAsString));
        if (playerNameOptional.isEmpty()) return Optional.empty();
        String playerName = playerNameOptional.get().trim();
        if (!Validator.isValidPlayerName(playerName)) return Optional.empty();
        return playerLookup.getPlayerIdentity(playerName);
    }

    private boolean testPermissions(final String guildId, final GuildMessageChannel channel) {
        try {
            var test = channel.sendMessageEmbeds(new EmbedBuilder()
                .setDescription("✔ Watch Notifications Permissions Test Success! ✔")
                .setColor(Color.MEDIUM_SEA_GREEN)
                .build()).submit().get();
//            test.delete().queue();
            return true;
        } catch (final Throwable e) {
            LOGGER.warn("Failed testing permissions for guild: {}, in channel: {}", guildId, channel.getId(), e);
            return false;
        }
    }

    private boolean validateUserPermissions(final SlashCommandInteractionEvent event) {
        var member = event.getMember();
        if (member == null) return false;
        return member.hasPermission(Permission.MESSAGE_MANAGE) || member.hasPermission(Permission.ADMINISTRATOR);
    }
}
