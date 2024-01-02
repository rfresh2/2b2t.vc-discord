package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.swagger.vc.handler.ConnectionsApi;
import vc.swagger.vc.model.ConnectionsResponse;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.List;
import java.util.Optional;

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
        Optional<String> playerNameOptional = event.getOption("playername")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        int page = event.getOption("page")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asLong)
                .map(Long::intValue)
                .orElse(1);
        if (page <= 0)
            return error(event, "Page must be greater than 0");
        return playerNameOptional
                .filter(Validator::isValidPlayerName)
                .flatMap(playerLookup::getPlayerIdentity)
                .map(identity -> resolveConnections(event, identity, page))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolveConnections(final ChatInputInteractionEvent event, final PlayerLookup.PlayerIdentity identity, int page) {
        ConnectionsResponse connectionsResponse = connectionsApi.connections(identity.uuid(), null, 25, page);
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
