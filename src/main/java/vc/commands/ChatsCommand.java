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
import vc.commands.options.*;
import vc.openapi.handler.ApiException;
import vc.openapi.handler.ChatsApi;
import vc.openapi.model.ChatsResponse;
import vc.util.PlayerLookup;

import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class ChatsCommand implements SlashCommand, ButtonCommand {
    private static final Logger LOGGER = getLogger(ChatsCommand.class);
    private final ChatsApi chatsApi;
    private final ObjectMapper objectMapper;
    private final ChatInteractionOptionResolver resolver;
    private final PlayerLookup playerLookup;
    private final PaginatedButtonHandler buttonHandler;

    public ChatsCommand(final ChatsApi chatsApi, final PlayerLookup playerLookup, final ObjectMapper objectMapper, final PaginatedButtonHandler buttonHandler) {
        this.chatsApi = chatsApi;
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
        return "chats";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        ChatInteractionOptionContext ctx = resolver.resolveOptions(event);
        if (ctx.isErrorSet()) return error(event, ctx.getErrorMessage());
        return resolveChats(event, ctx.profileData, ctx.page, ctx.startDate, ctx.endDate);
    }

    private Mono<Message> resolveChats(final DeferrableInteractionEvent event, final ProfileData identity, int page, LocalDate startDate, LocalDate endDate) {
        ChatsResponse chatsResponse = null;
        try {
            chatsResponse = chatsApi.chats(identity.uuid(), null, startDate, endDate, 25, page);
        } catch (final Exception e) {
            if (e instanceof ApiException apiException) {
                if (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204) {
                    return event.createFollowup()
                        .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                                        .color(Color.RUBY)
                                        .description("No chats found")
                                        .thumbnail(identity.getAvatarURL())
                                        .build());
                } else if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                    LOGGER.error("Timeout searching for chats: {}", identity.uuid(), httpTimeoutException);
                    return error(event, "Timeout searching for chats. Try again in a minute");
                }
            } else {
                LOGGER.error("Error searching for chats: {}", identity.uuid(), e);
                throw new RuntimeException(e);
            }
        }
        if (chatsResponse == null || chatsResponse.getChats() == null || chatsResponse.getChats().isEmpty())
            return event.createFollowup()
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                                .color(Color.RUBY)
                                .description("No chats found")
                                .thumbnail(identity.getAvatarURL())
                                .build());
        final StringBuilder result = new StringBuilder();
        final AtomicBoolean truncated = new AtomicBoolean(false);
        chatsResponse.getChats().stream()
            .map(c -> SHORT_DATE_TIME.format(c.getTime().toInstant()) + " " + escape(c.getChat()))
            .forEachOrdered(s -> {
                if (result.length() + s.length() + 1 > 4090) {
                    truncated.set(true);
                    return;
                }
                result.append(s).append("\n");
            });
        if (!result.isEmpty()) result.deleteCharAt(result.length() - 1); // cut off the last newline
        if (truncated.get()) LOGGER.warn("Truncated chat response");
        return event.createFollowup()
            .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                            .color(Color.CYAN)
                            .description(result.toString())
                            .addField("Total", ""+chatsResponse.getTotal(), true)
                            .addField("Current Page", ""+page, true)
                            .addField("Total Pages", ""+chatsResponse.getPageCount(), true)
                            .thumbnail(identity.getAvatarURL())
                            .build())
            .withComponents(buttonHandler.getButtonRow(objectMapper, getName(), chatsResponse.getPageCount(), page, identity, startDate, endDate));
    }

    @Override
    public Mono<Message> handleButton(final ButtonInteractionEvent event) {
        return buttonHandler.defaultButtonHandler(event, objectMapper, getName(), playerLookup, this::resolveChats, this::error);
    }
}
