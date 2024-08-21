package vc.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateFields;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.openapi.vc.handler.PriorityPlayersApi;
import vc.openapi.vc.model.PriorityPlayersResponse;

import java.io.ByteArrayInputStream;
import java.util.List;

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
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        return Mono.defer(() -> {
            List<PriorityPlayersResponse> priorityPlayersResponses = null;
            try {
                priorityPlayersResponses = priorityPlayersApi.priorityPlayers();
            } catch (final Throwable e) {
                LOGGER.error("Failed to get priority players", e);
            }
            if (priorityPlayersResponses == null || priorityPlayersResponses.isEmpty()) {
                return error(event, "Unable to resolve priority players");
            }

            // write players to json
            String jsonString = null;
            try {
                jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(priorityPlayersResponses);
            } catch (final Throwable e) {
                LOGGER.error("Failed to write priority players to json", e);
            }
            if (jsonString == null || jsonString.isEmpty()) {
                return error(event, "Failed to dump priority players list");
            }
            return event.createFollowup()
                .withFiles(MessageCreateFields.File.of("priority_players.json", new ByteArrayInputStream(jsonString.getBytes())))
                .withEmbeds(EmbedCreateSpec.builder()
                                .title("Priority Queue Players Dump")
                                .addField("Player Count", ""+priorityPlayersResponses.size(), true)
                                .description("JSON Generated!")
                                .color(Color.CYAN)
                                .build());
        });
    }
}
