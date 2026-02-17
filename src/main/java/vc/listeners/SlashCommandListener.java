package vc.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vc.commands.SlashCommand;
import vc.commands.buttons.ButtonCommand;
import vc.commands.buttons.PaginatedButtonHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SlashCommandListener extends ListenerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("CommandListener");
    private final Map<String, SlashCommand> commandMap;
    private final Map<String, ButtonCommand> buttonListenerMap;

    public SlashCommandListener(List<SlashCommand> slashCommands, JDA jda) {
        this.commandMap = slashCommands.stream().collect(Collectors.toMap(SlashCommand::getName, c -> c));
        this.buttonListenerMap = slashCommands.stream()
            .filter(c -> c instanceof ButtonCommand)
            .collect(Collectors.toMap(SlashCommand::getName, c -> (ButtonCommand) c));
        jda.addEventListener(this);
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        Instant beforeTime = Instant.now();
        var command = commandMap.get(event.getName());
        if (command == null) {
            LOGGER.error("Command not found: {}", event.getName());
            event.reply("Command not found").setEphemeral(true).queue();
            return;
        }
        event.deferReply().queue(
            ok -> command.handle(event).queue(
                msg -> logMessage(command, event, beforeTime, null),
                error -> {
                    logMessage(command, event, beforeTime, error);
                    command.error(event, "Error executing command").queue();
                }),
            error -> LOGGER.error("Failed to defer slash command", error)
        );
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        Instant beforeTime = Instant.now();
        var split = event.getComponentId().split(PaginatedButtonHandler.ID_PREFIX_DELIMITER, 2);
        var listener = buttonListenerMap.get(split[0]);
        if (listener == null) {
            LOGGER.error("Button handler not found for id: {}", event.getComponentId());
            event.reply("Button handler not found for id: " + event.getComponentId()).setEphemeral(true).queue();
            return;
        }
        event.deferReply().queue(
            ok -> listener.handleButton(event).queue(
                msg -> logButton(event, beforeTime, null),
                error -> {
                    logButton(event, beforeTime, error);
                    event.getHook().sendMessage("Error handling button").queue();
                }),
            error -> LOGGER.error("Failed to defer button interaction", error)
        );
    }

    private void logButton(final ButtonInteractionEvent event, final Instant beforeTime, final Throwable error) {
        try {
            Instant afterTime = Instant.now();
            String username = event.getUser().getName();
            var guild = event.getGuild();
            String guildLog = guild == null ? "(?)" : "(" + guild.getId() + " - " + guild.getName() + ")";
            LOGGER.info("[{}ms] {} {} clicked button: {}",
                afterTime.toEpochMilli() - beforeTime.toEpochMilli(),
                username,
                guildLog,
                event.getComponentId());
            if (error != null) {
                LOGGER.error("Error handling button", error);
            }
        } catch (final Exception e) {
            LOGGER.warn("failed logging button", e);
        }
    }

    private void logMessage(SlashCommand command, final SlashCommandInteractionEvent event, final Instant beforeTime, Throwable error) {
        try {
            Instant afterTime = Instant.now();
            String username = event.getUser().getName();
            String dataOptions = event.getOptions().stream()
                .map(s -> s.getName() + ":" + s.getAsString())
                .collect(Collectors.joining(" "));
            Guild guild = event.getGuild();
            String guildLog = guild == null ? "(?)" : "(" + guild.getId() + " - " + guild.getName() + ")";
            LOGGER.info("[{}ms] {} {} executed {}{}",
                afterTime.toEpochMilli() - beforeTime.toEpochMilli(),
                username,
                guildLog,
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
