package vc.listeners;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteraction;
import discord4j.core.object.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import vc.commands.SlashCommand;
import vc.config.GuildConfigManager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SlashCommandListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("CommandListener");
    private final Collection<SlashCommand> commands;
    private final GuildConfigManager guildConfigManager;

    public SlashCommandListener(List<SlashCommand> slashCommands, GatewayDiscordClient client, final GuildConfigManager guildConfigManager) {
        this.commands = slashCommands;
        this.guildConfigManager = guildConfigManager;
        client.on(ChatInputInteractionEvent.class, this::handle).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    public Mono<Message> handle(ChatInputInteractionEvent event) {
        //Convert our list to a flux that we can iterate through
        return event.deferReply().then(Flux.fromIterable(commands)
            //Filter out all commands that don't match the name this event is for
            .filter(command -> command.getName().equals(event.getCommandName()))
            //Get the first (and only) item in the flux that matches our filter
            .next()
            .doOnSuccess(command -> logMessage(command, event))
            //Have our command class handle all logic related to its specific command.
            .flatMap(command -> command.handle(event)));
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
