package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.openapi.handler.ConnectionsApi;
import vc.openapi.model.ConnectionsResponse;
import vc.util.PlayerLookup;

import java.util.List;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class ConnectionsCommand extends PlayerLookupCommand {
    private static final Logger LOGGER = getLogger(ConnectionsCommand.class);
    private final ConnectionsApi connectionsApi;

    public ConnectionsCommand(final ConnectionsApi connectionsApi, final PlayerLookup playerLookup) {
        super(playerLookup);
        this.connectionsApi = connectionsApi;
    }

    @Override
    public String getName() {
        return "connections";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        return resolveData(event, this::resolveConnections);
    }

    private Mono<Message> resolveConnections(final ChatInputInteractionEvent event, final ProfileData identity, int page) {
        ConnectionsResponse connectionsResponse = null;
        try {
            connectionsResponse = connectionsApi.connections(identity.uuid(), null, null, null, 25, page);
        } catch (final Exception e){
            LOGGER.error("Error processing connections response", e);
        }
        if (connectionsResponse == null || connectionsResponse.getConnections() == null || connectionsResponse.getConnections().isEmpty())
            return error(event, "No connections found for player");
        List<String> connectionStrings = connectionsResponse.getConnections().stream()
                .map(c -> c.getConnection().getValue() + " " + SHORT_DATE_TIME.format(c.getTime().toInstant()))
                .toList();
        StringBuilder result = new StringBuilder();
        for (String s : connectionStrings) {
            if (result.length() + s.length() > 4090) {
                LOGGER.warn("Message too long, truncating: {}", s);
                break;
            }
            result.append(s).append("\n");
        }
        if (result.length() > 0) {
            result = new StringBuilder(result.substring(0, result.length() - 1));
        } else {
            return event.createFollowup()
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                                .title("Connections")
                                .color(Color.CYAN)
                                .description("No connections found")
                                .thumbnail(playerLookup.getAvatarURL(identity.uuid()).toString())
                                .build());
        }
        return event.createFollowup()
            .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                            .title("Connections")
                            .color(Color.CYAN)
                            .description(result.toString())
                            .addField("Total", ""+connectionsResponse.getTotal(), true)
                            .addField("Current Page", ""+page, true)
                            .addField("Page Count", ""+connectionsResponse.getPageCount(), true)
                            .thumbnail(playerLookup.getAvatarURL(identity.uuid()).toString())
                            .build());
    }
}
