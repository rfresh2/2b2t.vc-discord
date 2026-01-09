package vc.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.entity.Message;
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
import vc.openapi.model.Connection;
import vc.openapi.model.ConnectionsResponse;
import vc.util.PlayerLookup;

import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

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
            .registerOption(new PaginatedOption())
            .registerOption(new PlayerLookupOption(playerLookup))
            .registerOption(new TimeRangeOption());
        this.buttonHandler = buttonHandler;
    }

    @Override
    public String getName() {
        return "connections";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        var ctx = this.resolver.resolveOptions(event);
        if (ctx.isErrorSet()) return error(event, ctx.getErrorMessage());
        return resolveConnections(event, ctx.profileData, ctx.page, ctx.startDate, ctx.endDate);
    }

    private Mono<Message> resolveConnections(final DeferrableInteractionEvent event, final ProfileData identity, int page, LocalDate startDate, LocalDate endDate) {
        ConnectionsResponse connectionsResponse = null;
        try {
            connectionsResponse = connectionsApi.connections(identity.uuid(), null, startDate, endDate, 25, page);
        } catch (final Exception e) {
            if (e instanceof ApiException apiException) {
                if (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204) {
                    return event.createFollowup()
                        .withEmbeds(populateIdentity(embed(event), identity)
                            .color(Color.RUBY)
                            .description("No connections found")
                            .thumbnail(identity.getAvatarURL())
                            .build());
                } else if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                    LOGGER.error("Timeout searching for connections: {}", identity.uuid(), httpTimeoutException);
                    return error(event, "Timeout searching for connections. Try again in a minute");
                }
            } else {
                LOGGER.error("Error processing connections response", e);
                throw new RuntimeException(e);
            }
        }
        if (connectionsResponse == null || connectionsResponse.getConnections() == null || connectionsResponse.getConnections().isEmpty())
            return event.createFollowup()
                .withEmbeds(populateIdentity(embed(event), identity)
                    .color(Color.RUBY)
                    .description("No connections found")
                    .thumbnail(identity.getAvatarURL())
                    .build());
        final StringBuilder result = new StringBuilder();
        final AtomicBoolean truncated = new AtomicBoolean(false);
        connectionsResponse.getConnections().stream()
            .map(c -> (c.getConnection() == Connection.ConnectionEnum.JOIN ? ":green_circle:" : ":red_circle:")  + " " + SHORT_DATE_TIME.format(c.getTime().toInstant()))
            .forEachOrdered(s -> {
                if (result.length() + s.length() + 1 > 4090) {
                    truncated.set(true);
                    return;
                }
                result.append(s).append("\n");
            });
        if (!result.isEmpty()) result.deleteCharAt(result.length() - 1); // cut off the last newline
        if (truncated.get()) LOGGER.warn("Truncated connections response");
        return event.createFollowup()
            .withEmbeds(populateIdentity(embed(event), identity)
                .color(Color.CYAN)
                .description(result.toString())
                .addField("Total", ""+connectionsResponse.getTotal(), true)
                .addField("Page", page + " / " + connectionsResponse.getPageCount(), true)
                .addField("\u200B", "\u200B", true)
                .thumbnail(identity.getAvatarURL())
                .build())
            .withComponents(buttonHandler.getButtonRow(objectMapper, getName(), connectionsResponse.getPageCount(), page, identity, startDate, endDate));
    }

    @Override
    public Mono<Message> handleButton(final ButtonInteractionEvent event) {
        return buttonHandler.defaultButtonHandler(event, objectMapper, getName(), playerLookup, this::resolveConnections, this::error);
    }
}
