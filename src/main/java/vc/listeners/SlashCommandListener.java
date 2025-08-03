package vc.listeners;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteraction;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.commands.SlashCommand;
import vc.commands.buttons.ButtonCommand;
import vc.commands.buttons.PaginatedButtonHandler;
import vc.config.live_feed.LiveFeedConfigStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SlashCommandListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("CommandListener");
    private final Map<String, SlashCommand> commandMap;
    private final Map<String, ButtonCommand> buttonListenerMap;
    private final LiveFeedConfigStore guildConfigManager;

    public SlashCommandListener(List<SlashCommand> slashCommands, GatewayDiscordClient client, final LiveFeedConfigStore guildConfigManager) {
        this.commandMap = slashCommands.stream().collect(Collectors.toMap(SlashCommand::getName, c -> c));
        this.buttonListenerMap = slashCommands.stream()
            .filter(c -> c instanceof ButtonCommand)
            .collect(Collectors.toMap(SlashCommand::getName, c -> (ButtonCommand) c));
        this.guildConfigManager = guildConfigManager;
        client.on(ChatInputInteractionEvent.class, this::handleChatInteraction).subscribe();
        client.on(ButtonInteractionEvent.class, this::handleButtonInteraction).subscribe();
    }

    public Mono<Message> handleChatInteraction(ChatInputInteractionEvent event) {
        Instant beforeTime = Instant.now();
        var command = commandMap.get(event.getCommandName());
        if (command == null) {
            LOGGER.error("Command not found: {}", event.getCommandName());
            return event.reply("Command not found").dematerialize();
        }
        return event.deferReply()
            .then(Mono.defer(() -> command.handle(event)))
            .doOnSuccess(msg -> logMessage(command, event, beforeTime, null))
            .doOnError(e -> logMessage(command, event, beforeTime, e));
    }

    public Mono<Message> handleButtonInteraction(ButtonInteractionEvent event) {
        Instant beforeTime = Instant.now();
        var listener = buttonListenerMap.get(event.getCustomId().split(PaginatedButtonHandler.ID_PREFIX_DELIMITER)[0]);
        if (listener == null) {
            LOGGER.error("Button handler not found for id: {}", event.getCustomId());
            return event.reply("Button handler not found for id: " + event.getCustomId()).dematerialize();
        }
        return event.deferReply()
            .then(Mono.defer(() -> listener.handleButton(event)))
            .doOnSuccess(v -> logButton(event, beforeTime, null))
            .doOnError(e -> logButton(event, beforeTime, e));
    }

    private void logButton(final ButtonInteractionEvent event, final Instant beforeTime, final Throwable error) {
        try {
            Instant afterTime = Instant.now();
            String username = event.getInteraction().getUser().getTag();
            String guild = event.getInteraction().getGuildId()
                .map(Snowflake::asString)
                .flatMap(guildConfigManager::getLiveFeedConfig)
                .map(config -> "(" + config.guildId() + " - " + config.guildName() + ")")
                .orElse("(?)");
            LOGGER.info("[{}ms] {} {} clicked button: {}",
                afterTime.toEpochMilli() - beforeTime.toEpochMilli(),
                username,
                guild,
                event.getCustomId());
            if (error != null) {
                LOGGER.error("Error handling button", error);
            }
        } catch (final Exception e) {
            LOGGER.warn("failed logging button", e);
        }
    }

    private void logMessage(SlashCommand command, final ChatInputInteractionEvent event, final Instant beforeTime, Throwable error) {
        try {
            Instant afterTime = Instant.now();
            String username = event.getInteraction().getUser().getTag();
            String dataOptions = event.getInteraction().getCommandInteraction()
                .map(ApplicationCommandInteraction::getOptions)
                .orElse(Collections.emptyList())
                .stream()
                .flatMap(o -> {
                    List<ApplicationCommandInteractionOption> result = new ArrayList<>();
                    result.add(o);
                    result.addAll(o.getOptions());
                    for (var sub : o.getOptions()) {
                        result.addAll(sub.getOptions());
                    }
                    return result.stream();
                })
                .map(s -> s.getName() + s.getValue().map(v -> ":" + v.getRaw()).orElse(""))
                .collect(Collectors.joining(" "));
            String guild = event.getInteraction().getGuildId()
                .map(Snowflake::asString)
                .flatMap(guildConfigManager::getLiveFeedConfig)
                .map(config -> "(" + config.guildId() + " - " + config.guildName() + ")")
                .orElse("(?)");
            LOGGER.info("[{}ms] {} {} executed {}{}",
                afterTime.toEpochMilli() - beforeTime.toEpochMilli(),
                username,
                guild,
                command.getName(),
                !dataOptions.isEmpty() ? " : " + dataOptions : "");
            if (error != null) {
                LOGGER.error("Error executing command", error);
            }
        } catch (final Exception e) {
            LOGGER.warn("failed logging command", e);
        }
    }
}
