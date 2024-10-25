package vc.listeners;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteraction;
import discord4j.core.object.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import vc.commands.SlashCommand;
import vc.config.GuildConfigManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SlashCommandListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("CommandListener");
    private final Map<String, SlashCommand> commandMap;
    private final GuildConfigManager guildConfigManager;

    public SlashCommandListener(List<SlashCommand> slashCommands, GatewayDiscordClient client, final GuildConfigManager guildConfigManager) {
        this.commandMap = slashCommands.stream().collect(Collectors.toMap(SlashCommand::getName, c -> c));
        this.guildConfigManager = guildConfigManager;
        client.on(ChatInputInteractionEvent.class, this::handle).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    public Mono<Message> handle(ChatInputInteractionEvent event) {
        var command = commandMap.get(event.getCommandName());
        if (command == null) {
            return event.reply("Command not found").dematerialize();
        }
        return event.deferReply()
            .doOnSuccess(msg -> logMessage(command, event))
            .then(Mono.defer(() -> command.handle(event)));
    }

    private void logMessage(SlashCommand command, final ChatInputInteractionEvent event) {
        try {
            String username = event.getInteraction().getUser().getTag();
            String dataOptions = event.getInteraction().getCommandInteraction()
                .map(ApplicationCommandInteraction::getOptions)
                .orElse(Collections.emptyList())
                .stream()
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
