package vc.commands;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.util.MentionUtil;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.api.model.ProfileDataImpl;
import vc.commands.options.ChatInteractionOptionContext;
import vc.config.watch.GuildChatWatchRepository;
import vc.config.watch.GuildPlayerWatchRepository;
import vc.config.watch.model.GuildChatWatchConfig;
import vc.config.watch.model.GuildPlayerWatchConfig;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.time.Duration;
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
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        if (event.getInteraction().getGuildId().isEmpty()) return error(event, "This command can only be used inside a discord server");
        if (!validateUserPermissions(event)) return error(event, "You must have permission: " + Permission.MANAGE_MESSAGES + " to use this command");

        var playerTypeOption = event.getOption("player");
        if (playerTypeOption.isPresent()) {
            return handlePlayerWatch(event, playerTypeOption.get());
        }
        var chatTypeOption = event.getOption("chat");
        if (chatTypeOption.isPresent()) {
            return handleChatWatch(event, chatTypeOption.get());
        }
        return error(event, "Unknown command option");
    }

    private Mono<Message> handleChatWatch(final ChatInputInteractionEvent event, final ApplicationCommandInteractionOption option) {
        if (option.getOption("add").isPresent()) {
            var addOption = option.getOption("add").get();
            var channelOption = addOption
                .getOption("channel")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asChannel)
                .map(m -> m.block(Duration.ofSeconds(10)));
            if (channelOption.isEmpty()) {
                return error(event, "Channel option is required to add a watch");
            }
            var channel = channelOption.get();
            String keyword = addOption.getOption("keyword")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("");
            if (keyword.length() < 3 || keyword.length() > 50) {
                return error(event, "Keyword must be between 3 and 50 characters");
            }
            if (!Validator.isValidChat(keyword)) {
                return error(event, "Keyword contains invalid chat characters");
            }
            boolean caseSensitive = addOption.getOption("case-sensitive")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asBoolean)
                .orElse(false);
            String mentionUserId = "";
            String mentionRoleId = "";
            var mentionTarget = addOption.getOption("mention")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asSnowflake);
            if (mentionTarget.isPresent()) {
                var snowflake = mentionTarget.get();
                var guild = event.getInteraction().getGuild().block(Duration.ofSeconds(10));
                try {
                    Member member = guild.getMemberById(snowflake).block(Duration.ofSeconds(10));
                    if (member != null) {
                        mentionUserId = snowflake.asString();
                    } else {
                        LOGGER.warn("Mention target is not a member of the guild: {}", snowflake.asString());
                    }
                } catch (Exception e) {
                }
                if (mentionUserId.isEmpty()) {
                    try {
                        var role = guild.getRoleById(snowflake).block(Duration.ofSeconds(10));
                        if (role != null) {
                            mentionRoleId = snowflake.asString();
                        } else {
                            LOGGER.warn("Mention target is not a role in the guild: {}", snowflake.asString());
                        }
                    } catch (Exception e) {
                    }
                }
                if (mentionUserId.isEmpty() && mentionRoleId.isEmpty()) {
                    return error(event, "Mention target must be a valid user or role");
                }
            }

            var watch = new GuildChatWatchConfig(
                Snowflake.of(Instant.now()).asString(),
                event.getInteraction().getGuildId().get().asString(),
                event.getInteraction().getGuild().block(Duration.ofSeconds(10)).getName(),
                channel.getId().asString(),
                keyword,
                caseSensitive,
                mentionUserId,
                mentionRoleId
            );
            var existingWatches = guildChatWatchRepository.getByGuildId(event.getInteraction().getGuildId().get().asString());
            for (var w : existingWatches) {
                if (w.keyword().equals(keyword)) {
                    guildChatWatchRepository.delete(w);
                }
            }
            guildChatWatchRepository.write(watch);
            return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                    .color(Color.SEA_GREEN)
                    .description("""
                         Watch added!
                         
                         Notifications on watched events will be sent to: %s
                         """.formatted(channel.getMention()))
                    .build());
        } else if (option.getOption("delete").isPresent()) {
            var deleteOption = option.getOption("delete").get();
            String keyword = deleteOption.getOption("keyword")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("");
            if (keyword.length() < 3 || keyword.length() > 50) {
                return error(event, "Keyword must be between 3 and 50 characters");
            }
            var watches = guildChatWatchRepository.getByGuildId(event.getInteraction().getGuildId().get().asString());
            for (var watch : watches) {
                if (watch.keyword().equals(keyword)) {
                    guildChatWatchRepository.delete(watch);
                    return event.createFollowup()
                        .withEmbeds(EmbedCreateSpec.builder()
                            .color(Color.SEA_GREEN)
                            .description("Watch deleted!")
                            .build());
                }
            }
            return error(event, "No watch found for `" + keyword + "`");
        } else if (option.getOption("list").isPresent()) {
            var watches = guildChatWatchRepository.getByGuildId(event.getInteraction().getGuildId().get().asString());
            Collections.sort(watches, (a, b) -> {
                int c = a.channelId().compareToIgnoreCase(b.channelId());
                if (c != 0) return c;
                return a.keyword().compareTo(b.keyword());
            });
            StringBuilder builder = new StringBuilder();
            if (watches.isEmpty()) {
                builder.append("None!\n");
            } else {
                for (var watch : watches) {
                    builder
                        .append(escape(watch.keyword()));
                    if (watch.caseSensitive()) {
                        builder.append(" (case-sensitive)");
                    }
                    builder
                        .append("\n");
                }
            }
            var description = builder.toString();
            if (description.length() > 4000) {
                description = description.substring(0, 4000) + "\n... (truncated)";
            }
            return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                    .title("Watch List")
                    .description(description)
                    .color(Color.CYAN)
                    .build());
        } else if (option.getOption("clear").isPresent()) {
            var watches = guildChatWatchRepository.getByGuildId(event.getInteraction().getGuildId().get().asString());
            for (var watch : watches) {
                guildChatWatchRepository.delete(watch);
            }
            return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                    .title("All Watches Cleared")
                    .description("Removed " + watches.size() + " watches.")
                    .color(Color.CYAN)
                    .build());
        }
        return error(event, "Unknown command option");
    }

    private Mono<Message> handlePlayerWatch(final ChatInputInteractionEvent event, final ApplicationCommandInteractionOption option) {
        if (option.getOption("add").isPresent()) {
            var addOption = option.getOption("add").get();
            var ctx = resolveProfileSubOption(new ChatInteractionOptionContext(event), addOption);
            if (ctx.isErrorSet()) return error(event, ctx.getErrorMessage());
            var channelOption = addOption
                .getOption("channel")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asChannel)
                .map(m -> m.block(Duration.ofSeconds(10)));
            if (channelOption.isEmpty()) {
                return error(event, "Channel option is required to add a watch");
            }
            var channel = channelOption.get();
            if (!testPermissions(event.getInteraction().getGuildId().get().asString(), channel)) {
                return error(event, "Bot must have permissions to send messages in: " + channel.getMention());
            }
            boolean joins = addOption.getOption("joins")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asBoolean)
                .orElse(true);
            boolean leaves = addOption.getOption("leaves")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asBoolean)
                .orElse(true);
            boolean chats = addOption.getOption("chats")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asBoolean)
                .orElse(true);
            boolean deaths = addOption.getOption("deaths")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asBoolean)
                .orElse(true);
            boolean kills = addOption.getOption("kills")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asBoolean)
                .orElse(true);
            if (!joins && !leaves && !chats && !deaths && !kills) {
                return error(event, "At least one event type must be enabled");
            }
            String mentionUserId = "";
            String mentionRoleId = "";
            var mentionTarget = addOption.getOption("mention")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asSnowflake);
            if (mentionTarget.isPresent()) {
                var snowflake = mentionTarget.get();
                var guild = event.getInteraction().getGuild().block(Duration.ofSeconds(10));
                try {
                    Member member = guild.getMemberById(snowflake).block(Duration.ofSeconds(10));
                    if (member != null) {
                        mentionUserId = snowflake.asString();
                    } else {
                        LOGGER.warn("Mention target is not a member of the guild: {}", snowflake.asString());
                    }
                } catch (Exception e) {
                }
                if (mentionUserId.isEmpty()) {
                    try {
                        var role = guild.getRoleById(snowflake).block(Duration.ofSeconds(10));
                        if (role != null) {
                            mentionRoleId = snowflake.asString();
                        } else {
                            LOGGER.warn("Mention target is not a role in the guild: {}", snowflake.asString());
                        }
                    } catch (Exception e) {
                    }
                }
                if (mentionUserId.isEmpty() && mentionRoleId.isEmpty()) {
                    return error(event, "Mention target must be a valid user or role");
                }
            }

            var profile = ctx.profileData;
            var watch = new GuildPlayerWatchConfig(
                Snowflake.of(Instant.now()).asString(),
                event.getInteraction().getGuildId().get().asString(),
                event.getInteraction().getGuild().block(Duration.ofSeconds(10)).getName(),
                channel.getId().asString(),
                joins,
                leaves,
                chats,
                deaths,
                kills,
                mentionUserId,
                mentionRoleId,
                profile.uuid(),
                profile.name()
            );
            var existingWatches = guildPlayerWatchRepository.getByGuildId(event.getInteraction().getGuildId().get().asString());
            for (var w : existingWatches) {
                if (w.targetUuid().equals(profile.uuid())) {
                    guildPlayerWatchRepository.delete(w);
                }
            }
            guildPlayerWatchRepository.write(watch);
            return event.createFollowup()
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), profile)
                    .color(Color.SEA_GREEN)
                    .description("""
                         Watch added!
                         
                         Notifications on watched events will be sent to: %s
                         """.formatted(channel.getMention()))
                    .thumbnail(profile.getAvatarURL())
                    .build());
        } else if (option.getOption("delete").isPresent()) {
            var deleteOption = option.getOption("delete").get();
            var playerNameOption = deleteOption.getOption("player")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
            if (playerNameOption.isEmpty()) {
                return error(event, "Player name required");
            }
            var playerName = playerNameOption.get().trim();
            if (!Validator.isValidPlayerName(playerName)) {
                return error(event, "Invalid player name");
            }
            var profile = playerLookup.getPlayerIdentity(playerName)
                // fall back to a random UUID, so we can handle cases where the player's profile was deleted or name changed
                .orElse(new ProfileDataImpl(playerName, UUID.randomUUID()));
            var watches = guildPlayerWatchRepository.getByGuildId(event.getInteraction().getGuildId().get().asString());
            for (var watch : watches) {
                if (watch.targetName().equalsIgnoreCase(playerName)) {
                    guildPlayerWatchRepository.delete(watch);
                    return event.createFollowup()
                        .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), profile)
                            .color(Color.SEA_GREEN)
                            .description("Watch deleted!")
                            .thumbnail(profile.getAvatarURL())
                            .build());
                }
            }
            return error(event, "No watch found for " + profile.name() + " (" + profile.uuid() + ")");
        } else if (option.getOption("list").isPresent()) {
            var watches = guildPlayerWatchRepository.getByGuildId(event.getInteraction().getGuildId().get().asString());
            Collections.sort(watches, (a, b) -> {
                int c = a.channelId().compareToIgnoreCase(b.channelId());
                if (c != 0) return c;
                return a.targetName().compareToIgnoreCase(b.targetName());
            });
            StringBuilder builder = new StringBuilder();
            if (watches.isEmpty()) {
                builder.append("None!\n");
            } else {
                for (var watch : watches) {
                    builder
                        .append(escape(watch.targetName()));
                    boolean anyEnabled = watch.joins() || watch.leaves() || watch.chats() || watch.deaths() || watch.kills();
                    if (anyEnabled) {
                        builder
                            .append(" - ")
                            .append(MentionUtil.forChannel(Snowflake.of(watch.channelId())))
                            .append(" - ");
                        if (watch.joins()) {
                            builder.append("Joins, ");
                        }
                        if (watch.leaves()) {
                            builder.append("Leaves, ");
                        }
                        if (watch.chats()) {
                            builder.append("Chats, ");
                        }
                        if (watch.deaths()) {
                            builder.append("Deaths, ");
                        }
                        if (watch.kills()) {
                            builder.append("Kills, ");
                        }
                        // Remove trailing comma and space
                        builder.setLength(builder.length() - 2);
                    } else {
                        builder.append(" - Disabled");
                    }
                    builder
                        .append("\n");
                }
            }
            var description = builder.toString();
            if (description.length() > 4000) {
                description = description.substring(0, 4000) + "\n... (truncated)";
            }
            return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                    .title("Watch List")
                    .description(description)
                    .color(Color.CYAN)
                    .build());
        } else if (option.getOption("clear").isPresent()) {
            var watches = guildPlayerWatchRepository.getByGuildId(event.getInteraction().getGuildId().get().asString());
            for (var watch : watches) {
                guildPlayerWatchRepository.delete(watch);
            }
            return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                    .title("All Watches Cleared")
                    .description("Removed " + watches.size() + " watches.")
                    .color(Color.CYAN)
                    .build());
        }
        return error(event, "Unknown command option");
    }

    private ChatInteractionOptionContext resolveProfileSubOption(final ChatInteractionOptionContext ctx, final ApplicationCommandInteractionOption parentOption) {
        var playerNameOptional = parentOption.getOption("player").flatMap(ApplicationCommandInteractionOption::getValue);
        if(playerNameOptional.isEmpty()) {
            ctx.setError("Player name required");
            return ctx;
        }
        String playerName = playerNameOptional.get().asString().trim();
        if (!Validator.isValidPlayerName(playerName)) {
            ctx.setError("Invalid player name");
            return ctx;
        }
        Optional<ProfileData> playerIdentity = playerLookup.getPlayerIdentity(playerName);
        if (playerIdentity.isEmpty()) {
            ctx.setError("No player named `" + playerName + "` exists");
            return ctx;
        }
        ctx.profileData = playerIdentity.get();
        return ctx;
    }

    private boolean testPermissions(final String guildId, final Channel channel) {
        try {
            var embed = EmbedCreateSpec.builder()
                .description("✔ Watch Notifications Permissions Test Success! ✔")
                .color(Color.MEDIUM_SEA_GREEN)
                .build();
            var msg = MessageCreateSpec.builder()
                .addEmbed(embed)
                .build()
                .asRequest();
            channel.getRestChannel().createMessage(msg)
                .block();
            return true;
        } catch (final ClientException clientException) {
            if (clientException.getStatus().code() == 403) {
                LOGGER.warn("Missing permissions for guild: {}, in channel: {}", guildId, channel.getId().asString());
                return false;
            } else {
                LOGGER.warn("Failed testing permissions for guild: {}, in channel: {} - [{}] {}", guildId, channel.getId().asString(), clientException.getStatus().code(), clientException.getMessage());
            }
        } catch (final Throwable e) {
            LOGGER.warn("Failed testing permissions for guild: {}, in channel: {}", guildId, channel.getId().asString(), e);
        }
        return false;
    }

    private boolean validateUserPermissions(final ChatInputInteractionEvent event) {
        return event.getInteraction().getMember()
            .map(member -> member.getBasePermissions().block())
            .map(perms -> perms.contains(Permission.MANAGE_MESSAGES) || perms.contains(Permission.ADMINISTRATOR))
            .orElse(false);
    }

}
