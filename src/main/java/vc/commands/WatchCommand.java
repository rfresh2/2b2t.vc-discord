package vc.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vc.api.model.ProfileData;
import vc.api.model.ProfileDataImpl;
import vc.config.watch.UserChatWatchRepository;
import vc.config.watch.UserPlayerWatchRepository;
import vc.config.watch.model.UserChatWatchConfig;
import vc.config.watch.model.UserPlayerWatchConfig;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static vc.util.DiscordMarkdownEscape.escape;

@Component
public class WatchCommand implements SlashCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchCommand.class);
    private final UserChatWatchRepository userChatWatchRepository;
    private final UserPlayerWatchRepository userPlayerWatchRepository;
    private final PlayerLookup playerLookup;

    public WatchCommand(
        final UserChatWatchRepository userChatWatchRepository,
        final UserPlayerWatchRepository userPlayerWatchRepository,
        final PlayerLookup playerLookup
    ) {
        this.userChatWatchRepository = userChatWatchRepository;
        this.userPlayerWatchRepository = userPlayerWatchRepository;
        this.playerLookup = playerLookup;
    }

    @Override
    public String getName() {
        return "watch";
    }

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        var group = event.getSubcommandGroup();
        var sub = event.getSubcommandName();
        if (group == null || sub == null) return error(event, "Unknown command option");
        return switch (group) {
            case "chat" -> handleChatWatch(event, sub);
            case "player" -> handlePlayerWatch(event, sub);
            default -> error(event, "Unknown command option");
        };
    }

    private WebhookMessageCreateAction<Message> handleChatWatch(final SlashCommandInteractionEvent event, String sub) {
        if ("add".equals(sub)) {
            String keyword = Optional.ofNullable(event.getOption("keyword", OptionMapping::getAsString)).orElse("").replace("< >", " ");
            if (keyword.length() < 3 || keyword.length() > 50) return error(event, "Keyword must be between 3 and 50 characters");
            if (!Validator.isValidChat(keyword)) return error(event, "Keyword contains invalid chat characters");
            boolean caseSensitive = Optional.ofNullable(event.getOption("case-sensitive", OptionMapping::getAsBoolean)).orElse(false);
            var watch = new UserChatWatchConfig(
                String.valueOf(Instant.now().toEpochMilli()),
                event.getUser().getId(),
                event.getUser().getName(),
                keyword,
                caseSensitive
            );
            var existingWatches = userChatWatchRepository.getByOwnerId(event.getUser().getId());
            for (var w : existingWatches) if (w.keyword().equals(keyword)) userChatWatchRepository.delete(w);
            userChatWatchRepository.write(watch);
            return event.getHook().sendMessageEmbeds(embed(event)
                .setColor(Color.SEA_GREEN)
                .setTitle("Chat Watch Added")
                .setDescription("You will be DM'd on chats containing `%s`".formatted(keyword))
                .build());
        }
        if ("delete".equals(sub)) {
            String keyword = Optional.ofNullable(event.getOption("keyword", OptionMapping::getAsString)).orElse("").replace("< >", " ");
            if (keyword.length() < 3 || keyword.length() > 50) return error(event, "Keyword must be between 3 and 50 characters");
            var watches = userChatWatchRepository.getByOwnerId(event.getUser().getId());
            for (var watch : watches) {
                if (watch.keyword().equals(keyword)) {
                    userChatWatchRepository.delete(watch);
                    return event.getHook().sendMessageEmbeds(embed(event)
                        .setTitle("Chat Watch Deleted")
                        .setColor(Color.SEA_GREEN)
                        .setDescription("Chat watch `%s` deleted!".formatted(keyword))
                        .build());
                }
            }
            return error(event, "No chat watch found for `%s`".formatted(keyword));
        }
        if ("list".equals(sub)) {
            var watches = userChatWatchRepository.getByOwnerId(event.getUser().getId());
            Collections.sort(watches, (a, b) -> a.keyword().compareToIgnoreCase(b.keyword()));
            StringBuilder builder = new StringBuilder();
            if (watches.isEmpty()) {
                builder.append("None!\n");
            } else {
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
            var watches = userChatWatchRepository.getByOwnerId(event.getUser().getId());
            for (var watch : watches) userChatWatchRepository.delete(watch);
            return event.getHook().sendMessageEmbeds(embed(event)
                .setTitle("All Chat Watches Cleared")
                .setDescription("Removed " + watches.size() + " watches.\n" + watches.stream().map(UserChatWatchConfig::keyword).map("`%s`"::formatted).reduce("", (a, b) -> a + "\n" + b))
                .setColor(Color.CYAN)
                .build());
        }
        return error(event, "Invalid command");
    }

    private WebhookMessageCreateAction<Message> handlePlayerWatch(final SlashCommandInteractionEvent event, String sub) {
        if ("add".equals(sub)) {
            var profile = resolveProfile(event, "player");
            if (profile.isEmpty()) return error(event, "Invalid player name");
            boolean joins = Optional.ofNullable(event.getOption("joins", OptionMapping::getAsBoolean)).orElse(true);
            boolean leaves = Optional.ofNullable(event.getOption("leaves", OptionMapping::getAsBoolean)).orElse(true);
            boolean chats = Optional.ofNullable(event.getOption("chats", OptionMapping::getAsBoolean)).orElse(true);
            boolean deaths = Optional.ofNullable(event.getOption("deaths", OptionMapping::getAsBoolean)).orElse(true);
            boolean kills = Optional.ofNullable(event.getOption("kills", OptionMapping::getAsBoolean)).orElse(true);
            if (!joins && !leaves && !chats && !deaths && !kills) return error(event, "At least one event type must be enabled");

            var p = profile.get();
            var watch = new UserPlayerWatchConfig(
                String.valueOf(Instant.now().toEpochMilli()),
                event.getUser().getId(),
                event.getUser().getName(),
                joins, leaves, chats, deaths, kills,
                p.uuid(), p.name()
            );
            var existingWatches = userPlayerWatchRepository.getByOwnerId(event.getUser().getId());
            for (var w : existingWatches) if (w.targetUuid().equals(p.uuid())) userPlayerWatchRepository.delete(w);
            userPlayerWatchRepository.write(watch);
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
                .setDescription("You will be DM'd on `%s` events for `%s`".formatted(eventsList, p.name()))
                .setThumbnail(p.getAvatarURL())
                .build());
        }
        if ("delete".equals(sub)) {
            var playerName = Optional.ofNullable(event.getOption("player", OptionMapping::getAsString)).map(String::trim).orElse("");
            if (playerName.isEmpty()) return error(event, "Player name required");
            if (!Validator.isValidPlayerName(playerName)) return error(event, "Invalid player name");
            var profile = playerLookup.getPlayerIdentity(playerName).orElse(new ProfileDataImpl(playerName, UUID.randomUUID()));
            var watches = userPlayerWatchRepository.getByOwnerId(event.getUser().getId());
            for (var watch : watches) {
                if (watch.targetName().equalsIgnoreCase(playerName)) {
                    userPlayerWatchRepository.delete(watch);
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
            var watches = userPlayerWatchRepository.getByOwnerId(event.getUser().getId());
            Collections.sort(watches, (a, b) -> a.targetName().compareToIgnoreCase(b.targetName()));
            StringBuilder builder = new StringBuilder();
            if (watches.isEmpty()) {
                builder.append("None!\n");
            } else {
                for (var watch : watches) {
                    builder.append(escape(watch.targetName()));
                    boolean anyEnabled = watch.joins() || watch.leaves() || watch.chats() || watch.deaths() || watch.kills();
                    if (anyEnabled) {
                        builder.append(" - ");
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
            var watches = userPlayerWatchRepository.getByOwnerId(event.getUser().getId());
            for (var watch : watches) userPlayerWatchRepository.delete(watch);
            return event.getHook().sendMessageEmbeds(embed(event)
                .setTitle("All Player Watches Cleared")
                .setDescription("Removed " + watches.size() + " player watches.\n" + watches.stream().map(UserPlayerWatchConfig::targetName).map("`%s`"::formatted).reduce("", (a, b) -> a + "\n" + b))
                .setColor(Color.CYAN)
                .build());
        }
        return error(event, "Invalid command");
    }

    private Optional<ProfileData> resolveProfile(SlashCommandInteractionEvent event, String optionName) {
        var playerNameOptional = Optional.ofNullable(event.getOption(optionName, OptionMapping::getAsString));
        if (playerNameOptional.isEmpty()) return Optional.empty();
        String playerName = playerNameOptional.get().trim();
        if (!Validator.isValidPlayerName(playerName)) return Optional.empty();
        return playerLookup.getPlayerIdentity(playerName);
    }
}
