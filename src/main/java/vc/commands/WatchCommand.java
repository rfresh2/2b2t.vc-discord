package vc.commands;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.api.model.ProfileDataImpl;
import vc.commands.options.ChatInteractionOptionContext;
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
                .orElse("")
                .replace("< >", " ");
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
            var watch = new UserChatWatchConfig(
                Snowflake.of(Instant.now()).asString(),
                event.getUser().getId().asString(),
                event.getUser().getUsername(),
                keyword,
                caseSensitive
            );
            var existingWatches = userChatWatchRepository.getByOwnerId(event.getUser().getId().asString());
            for (var w : existingWatches) {
                if (w.keyword().equals(keyword)) {
                    userChatWatchRepository.delete(w);
                }
            }
            userChatWatchRepository.write(watch);
            return event.createFollowup()
                .withEmbeds(embed(event)
                    .color(Color.SEA_GREEN)
                    .title("Chat Watch Added")
                    .description("You will be DM'd on chats containing `%s`".formatted(keyword))
                    .build());
        } else if (option.getOption("delete").isPresent()) {
            var deleteOption = option.getOption("delete").get();
            String keyword = deleteOption.getOption("keyword")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("")
                .replace("< >", " ");
            if (keyword.length() < 3 || keyword.length() > 50) {
                return error(event, "Keyword must be between 3 and 50 characters");
            }
            var watches = userChatWatchRepository.getByOwnerId(event.getUser().getId().asString());
            for (var watch : watches) {
                if (watch.keyword().equals(keyword)) {
                    userChatWatchRepository.delete(watch);
                    return event.createFollowup()
                        .withEmbeds(embed(event)
                            .title("Chat Watch Deleted")
                            .color(Color.SEA_GREEN)
                            .description("Chat watch `%s` deleted!".formatted(keyword))
                            .build());
                }
            }
            return error(event, "No chat watch found for `%s`".formatted(keyword));
        } else if (option.getOption("list").isPresent()) {
            var watches = userChatWatchRepository.getByOwnerId(event.getUser().getId().asString());
            Collections.sort(watches, (a, b) -> a.keyword().compareToIgnoreCase(b.keyword()));
            StringBuilder builder = new StringBuilder();
            if (watches.isEmpty()) {
                builder.append("None!\n");
            } else {
                for (var watch : watches) {
                    builder
                        .append("`")
                        .append(watch.keyword())
                        .append("`");
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
                .withEmbeds(embed(event)
                    .title("Chat Watch List")
                    .description(description)
                    .color(Color.CYAN)
                    .build());
        } else if (option.getOption("clear").isPresent()) {
            var watches = userChatWatchRepository.getByOwnerId(event.getUser().getId().asString());
            for (var watch : watches) {
                userChatWatchRepository.delete(watch);
            }
            return event.createFollowup()
                .withEmbeds(embed(event)
                    .title("All Chat Watches Cleared")
                    .description("Removed " + watches.size() + " watches.\n"
                        + watches.stream()
                        .map(UserChatWatchConfig::keyword)
                        .map("`%s`"::formatted)
                        .reduce("", (a, b) -> a + "\n" + b))
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
            var existingWatches = userPlayerWatchRepository.getByOwnerId(event.getUser().getId().asString());
            for (var w : existingWatches) {
                if (w.targetUuid().equals(profile.uuid())) {
                    userPlayerWatchRepository.delete(w);
                }
            }
            userPlayerWatchRepository.write(watch);
            StringBuilder eventsList = new StringBuilder();
            if (joins) eventsList.append("Joins, ");
            if (leaves) eventsList.append("Leaves, ");
            if (chats) eventsList.append("Chats, ");
            if (deaths) eventsList.append("Deaths, ");
            if (kills) eventsList.append("Kills, ");
            // Remove trailing comma and space
            if (!eventsList.isEmpty()) {
                eventsList.setLength(eventsList.length() - 2);
            }
            return event.createFollowup()
                .withEmbeds(populateIdentity(embed(event), profile)
                    .color(Color.SEA_GREEN)
                    .title("Player Watch Added")
                    .description("You will be DM'd on `%s` events for `%s`".formatted(eventsList.toString(), profile.name()))
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
            var watches = userPlayerWatchRepository.getByOwnerId(event.getUser().getId().asString());
            for (var watch : watches) {
                if (watch.targetName().equalsIgnoreCase(playerName)) {
                    userPlayerWatchRepository.delete(watch);
                    return event.createFollowup()
                        .withEmbeds(populateIdentity(embed(event), profile)
                            .title("Player Watch Deleted")
                            .color(Color.SEA_GREEN)
                            .description("Player watch for `%s` deleted!".formatted(profile.name()))
                            .thumbnail(profile.getAvatarURL())
                            .build());
                }
            }
            return error(event, "No player watch found for `%s` (%s)".formatted(profile.name(), profile.uuid()));
        } else if (option.getOption("list").isPresent()) {
            var watches = userPlayerWatchRepository.getByOwnerId(event.getUser().getId().asString());
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
                .withEmbeds(embed(event)
                    .title("Player Watch List")
                    .description(description)
                    .color(Color.CYAN)
                    .build());
        } else if (option.getOption("clear").isPresent()) {
            var watches = userPlayerWatchRepository.getByOwnerId(event.getUser().getId().asString());
            for (var watch : watches) {
                userPlayerWatchRepository.delete(watch);
            }
            return event.createFollowup()
                .withEmbeds(embed(event)
                    .title("All Player Watches Cleared")
                    .description("Removed " + watches.size() + " player watches.\n"
                        + watches.stream()
                        .map(UserPlayerWatchConfig::targetName)
                        .map("`%s`"::formatted)
                        .reduce("", (a, b) -> a + "\n" + b))
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
            ctx.setError("No player named `%s` exists\nCheck for previous accounts with `/names`".formatted(playerName));
            return ctx;
        }
        ctx.profileData = playerIdentity.get();
        return ctx;
    }

}
