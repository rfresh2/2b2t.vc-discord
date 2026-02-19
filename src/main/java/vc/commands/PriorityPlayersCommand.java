package vc.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import vc.openapi.handler.PriorityPlayersApi;
import vc.openapi.model.PriorityPlayersResponse;

import java.nio.charset.StandardCharsets;

import static org.slf4j.LoggerFactory.getLogger;

@Component
public class PriorityPlayersCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(PriorityPlayersCommand.class);
    private final PriorityPlayersApi priorityPlayersApi;
    private final ObjectMapper objectMapper;

    public PriorityPlayersCommand(PriorityPlayersApi priorityPlayersApi, ObjectMapper objectMapper) {
        this.priorityPlayersApi = priorityPlayersApi;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "priodata";
    }

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        PriorityPlayersResponse response = null;
        try {
            response = priorityPlayersApi.priorityPlayers();
        } catch (final Throwable e) {
            LOGGER.error("Failed to get priority players", e);
        }
        if (response == null || response.getPlayers() == null || response.getPlayers().isEmpty()) {
            return error(event, "Unable to resolve priority players");
        }

        String jsonString = null;
        try {
            jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        } catch (final Throwable e) {
            LOGGER.error("Failed to write priority players to json", e);
        }
        if (jsonString == null || jsonString.isEmpty()) {
            return error(event, "Failed to dump priority players list");
        }
        return event.getHook().sendFiles(FileUpload.fromData(jsonString.getBytes(StandardCharsets.UTF_8), "priority_players.json"))
            .addEmbeds(embed(event)
                .addField("Player Count", String.valueOf(response.getPlayers().size()), true)
                .setDescription("JSON Generated!")
                .setColor(Color.CYAN)
                .build());
    }
}
