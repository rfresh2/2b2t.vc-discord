package vc.listeners;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteraction;
import discord4j.core.object.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import vc.commands.SlashCommand;
import vc.commands.buttons.ButtonCommand;
import vc.commands.buttons.PaginatedButtonHandler;
import vc.config.GuildConfigManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class SlashCommandListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("CommandListener");
    private final Map<String, SlashCommand> commandMap;
    private final Map<String, ButtonCommand> buttonListenerMap;
    private final GuildConfigManager guildConfigManager;

    public SlashCommandListener(List<SlashCommand> slashCommands, GatewayDiscordClient client, final GuildConfigManager guildConfigManager) {
        this.commandMap = slashCommands.stream().collect(Collectors.toMap(SlashCommand::getName, c -> c));
        this.buttonListenerMap = slashCommands.stream()
            .filter(c -> c instanceof ButtonCommand)
            .collect(Collectors.toMap(SlashCommand::getName, c -> (ButtonCommand) c));
        this.guildConfigManager = guildConfigManager;
        client.on(ChatInputInteractionEvent.class, this::handleChatInteraction).subscribeOn(Schedulers.boundedElastic()).subscribe();
        client.on(ButtonInteractionEvent.class, this::handleButtonInteraction).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    public Mono<Message> handleChatInteraction(ChatInputInteractionEvent event) {
        var command = commandMap.get(event.getCommandName());
        if (command == null) {
            LOGGER.error("Command not found: {}", event.getCommandName());
            return event.reply("Command not found").dematerialize();
        }
        return event.deferReply()
            .doOnSuccess(msg -> logMessage(command, event))
            .then(Mono.defer(() -> command.handle(event)))
            .doOnError(e -> LOGGER.error("Error handling command", e));
    }

    public Mono<Message> handleButtonInteraction(ButtonInteractionEvent event) {
        var listener = buttonListenerMap.get(event.getCustomId().split(PaginatedButtonHandler.ID_PREFIX_DELIMITER)[0]);
        if (listener == null) {
            LOGGER.error("Button handler not found for id: {}", event.getCustomId());
            return event.reply("Button handler not found for id: " + event.getCustomId()).dematerialize();
        }
        return event.deferReply()
            .doOnSuccess(v -> logButton(event))
            .then(Mono.defer(() -> listener.handleButton(event)))
            .doOnError(e -> LOGGER.error("Error handling button", e));
    }

    private void logButton(final ButtonInteractionEvent event) {
        try {
            String username = event.getInteraction().getUser().getTag();
            String guild = event.getInteraction().getGuildId()
                .map(Snowflake::asString)
                .flatMap(guildConfigManager::getGuildConfig)
                .map(config -> "(" + config.guildId() + " - " + config.guildName() + ")")
                .orElse("(?)");
            LOGGER.info("{} {} clicked button: {}", username, guild, event.getCustomId());
        } catch (final Exception e) {
            LOGGER.warn("failed logging button", e);
        }
    }

    private void logMessage(SlashCommand command, final ChatInputInteractionEvent event) {
        try {
            String username = event.getInteraction().getUser().getTag();
            String dataOptions = event.getInteraction().getCommandInteraction()
                .map(ApplicationCommandInteraction::getOptions)
                .orElse(Collections.emptyList())
                .stream()
                .flatMap(o -> Stream.concat(Stream.of(o), o.getOptions().stream()))
                .map(s -> s.getName() + s.getValue().map(v -> ":" + v.getRaw()).orElse(""))
                .collect(Collectors.joining(" "));
            String guild = event.getInteraction().getGuildId()
                .map(Snowflake::asString)
                .flatMap(guildConfigManager::getGuildConfig)
                .map(config -> "(" + config.guildId() + " - " + config.guildName() + ")")
                .orElse("(?)");
            LOGGER.info("{} {} executed {}{}",
                        username,
                        guild,
                        command.getName(),
                        !dataOptions.isEmpty() ? " : " + dataOptions : "");
        } catch (final Exception e) {
            LOGGER.warn("failed logging command", e);
        }

    }
}
