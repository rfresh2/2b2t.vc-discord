package vc.commands;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.api.model.ProfileDataImpl;
import vc.commands.options.ChatInteractionOptionContext;
import vc.config.watch.WatchConfigStore;
import vc.config.watch.model.UserChatWatchConfig;
import vc.config.watch.model.UserPlayerWatchConfig;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Component
public class WatchCommand implements SlashCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchCommand.class);
    private final WatchConfigStore watchConfigStore;
    private final PlayerLookup playerLookup;

    public WatchCommand(
        final WatchConfigStore watchConfigStore,
        final PlayerLookup playerLookup
    ) {
        this.watchConfigStore = watchConfigStore;
        this.playerLookup = playerLookup;
    }

    @Override
    public String getName() {
        return "watch";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
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
            String keyword = addOption.getOption("keyword")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("");
            if (keyword.length() < 3 || keyword.length() > 50) {
                return error(event, "Keyword must be between 3 and 50 characters");
            }
            boolean caseSensitive = addOption.getOption("case-sensitive")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asBoolean)
                .orElse(false);
            var watch = new UserChatWatchConfig(
                Snowflake.of(Instant.now()).asString(),
                event.getUser().getId().asString(),
                event.getUser().getUsername(),
                keyword,
                caseSensitive
            );
            var existingWatches = watchConfigStore.getUserChatWatchesByOwner(event.getUser().getId().asString());
            for (var w : existingWatches) {
                if (w.keyword().equals(keyword)) {
                    watchConfigStore.removeUserChatWatchConfig(w);
                }
            }
            watchConfigStore.writeUserChatWatchConfig(watch);
            return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                    .color(Color.SEA_GREEN)
                    .description("""
                         Watch added!
                         
                         You will be DM'd on chats containing the keyword
                         """)
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
            var watches = watchConfigStore.getUserChatWatchesByOwner(event.getUser().getId().asString());
            for (var watch : watches) {
                if (watch.keyword().equals(keyword)) {
                    watchConfigStore.removeUserChatWatchConfig(watch);
                    return event.createFollowup()
                        .withEmbeds(EmbedCreateSpec.builder()
                            .color(Color.SEA_GREEN)
                            .description("Watch deleted!")
                            .build());
                }
            }
            return error(event, "No watch found for `" + keyword + "`");
        } else if (option.getOption("list").isPresent()) {
            var watches = watchConfigStore.getUserChatWatchesByOwner(event.getUser().getId().asString());
            Collections.sort(watches, (a, b) -> a.keyword().compareToIgnoreCase(b.keyword()));
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
            var watches = watchConfigStore.getUserChatWatchesByOwner(event.getUser().getId().asString());
            for (var watch : watches) {
                watchConfigStore.removeUserChatWatchConfig(watch);
            }
            return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                    .title("All Watches Cleared")
                    .description("Removed " + watches.size() + " watches.")
                    .color(Color.CYAN)
                    .build());
        }
        return error(event, "Invalid command");
    }

    private Mono<Message> handlePlayerWatch(final ChatInputInteractionEvent event, final ApplicationCommandInteractionOption option) {
        if (option.getOption("add").isPresent()) {
            var addOption = option.getOption("add").get();
            var ctx = resolveProfileSubOption(new ChatInteractionOptionContext(event), addOption);
            if (ctx.isErrorSet()) return error(event, ctx.getErrorMessage());
            var profile = ctx.profileData;
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
            var watch = new UserPlayerWatchConfig(
                Snowflake.of(Instant.now()).asString(),
                event.getUser().getId().asString(),
                event.getUser().getUsername(),
                joins,
                leaves,
                chats,
                deaths,
                kills,
                profile.uuid(),
                profile.name()
            );
            var existingWatches = watchConfigStore.getUserWatchesByOwner(event.getUser().getId().asString());
            for (var w : existingWatches) {
                if (w.targetUuid().equals(profile.uuid())) {
                    watchConfigStore.removeUserPlayerWatchConfig(w);
                }
            }
            watchConfigStore.writeUserPlayerWatchConfig(watch);
            return event.createFollowup()
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), profile)
                    .color(Color.SEA_GREEN)
                    .description("""
                         Watch added!
                         
                         You will be DM'd on watched events
                         """)
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
            var watches = watchConfigStore.getUserWatchesByOwner(event.getUser().getId().asString());
            for (var watch : watches) {
                if (watch.targetName().equalsIgnoreCase(playerName)) {
                    watchConfigStore.removeUserPlayerWatchConfig(watch);
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
            var watches = watchConfigStore.getUserWatchesByOwner(event.getUser().getId().asString());
            Collections.sort(watches, (a, b) -> a.targetName().compareToIgnoreCase(b.targetName()));
            StringBuilder builder = new StringBuilder();
            if (watches.isEmpty()) {
                builder.append("None!\n");
            } else {
                for (var watch : watches) {
                    builder
                        .append(escape(watch.targetName()));
                    boolean anyEnabled = watch.joins() || watch.leaves() || watch.chats() || watch.deaths() || watch.kills();
                    if (anyEnabled) {
                        builder.append(" - ");
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
            var watches = watchConfigStore.getUserWatchesByOwner(event.getUser().getId().asString());
            for (var watch : watches) {
                watchConfigStore.removeUserPlayerWatchConfig(watch);
            }
            return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                    .title("All Watches Cleared")
                    .description("Removed " + watches.size() + " watches.")
                    .color(Color.CYAN)
                    .build());
        }
        return error(event, "Invalid command");
    }

    private ChatInteractionOptionContext resolveProfileSubOption(final ChatInteractionOptionContext ctx, final ApplicationCommandInteractionOption parentOption) {
        var playerNameOptional = parentOption.getOption("player").flatMap(ApplicationCommandInteractionOption::getValue);
        if (playerNameOptional.isEmpty()) {
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

}
