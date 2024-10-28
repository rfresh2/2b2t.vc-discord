package vc.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.commands.buttons.ButtonCommand;
import vc.commands.buttons.PaginatedButtonHandler;
import vc.commands.options.ChatInteractionOptionResolver;
import vc.commands.options.PaginatedOption;
import vc.commands.options.PlayerLookupOption;
import vc.commands.options.TimeRangeOption;
import vc.openapi.handler.ApiException;
import vc.openapi.handler.ConnectionsApi;
import vc.openapi.model.ConnectionsResponse;
import vc.util.PlayerLookup;

import java.time.LocalDate;
import java.util.List;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class ConnectionsCommand implements SlashCommand, ButtonCommand {
    private static final Logger LOGGER = getLogger(ConnectionsCommand.class);
    private final ConnectionsApi connectionsApi;
    private final ObjectMapper objectMapper;
    private final ChatInteractionOptionResolver resolver;
    private final PlayerLookup playerLookup;
    private final PaginatedButtonHandler buttonHandler;

    public ConnectionsCommand(final ConnectionsApi connectionsApi, final PlayerLookup playerLookup, final ObjectMapper objectMapper, final PaginatedButtonHandler buttonHandler) {
        this.connectionsApi = connectionsApi;
        this.objectMapper = objectMapper;
        this.playerLookup = playerLookup;
        this.resolver = new ChatInteractionOptionResolver()
            .registerTrait(new PaginatedOption())
            .registerTrait(new PlayerLookupOption(playerLookup))
            .registerTrait(new TimeRangeOption());
        this.buttonHandler = buttonHandler;
    }

    @Override
    public String getName() {
        return "connections";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        var ctx = this.resolver.execute(event);
        if (ctx.errorSet) return error(event, ctx.errorMessage);
        return resolveConnections(event, ctx.profileData, ctx.page, ctx.startDate, ctx.endDate);
    }

    private Mono<Message> resolveConnections(final DeferrableInteractionEvent event, final ProfileData identity, int page, LocalDate startDate, LocalDate endDate) {
        ConnectionsResponse connectionsResponse = null;
        try {
            connectionsResponse = connectionsApi.connections(identity.uuid(), null, startDate, endDate, 25, page);
        } catch (final Exception e) {
            if (e instanceof ApiException apiException
                && (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204)) {
                // fall through
            } else {
                LOGGER.error("Error processing connections response", e);
            }
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
                                .thumbnail(identity.getAvatarURL())
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
                            .thumbnail(identity.getAvatarURL())
                            .build())
            .withComponents(buttonHandler.getButtonRow(objectMapper, getName(), connectionsResponse.getPageCount(), page, identity, startDate, endDate));
    }

    @Override
    public Mono<Message> handleButton(final ButtonInteractionEvent event) {
        return buttonHandler.defaultButtonHandler(event, objectMapper, getName(), playerLookup, this::resolveConnections, this::error);
    }
}
