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
import vc.api.model.ProfileDataImpl;
import vc.commands.buttons.ButtonCommand;
import vc.commands.buttons.PaginatedButtonHandler;
import vc.commands.options.ChatInteractionOptionResolver;
import vc.commands.options.PaginatedOption;
import vc.commands.options.TimeRangeOption;
import vc.openapi.handler.ApiException;
import vc.openapi.handler.ChatsApi;
import vc.openapi.model.ChatSearchResponse;

import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class ChatSearchCommand implements SlashCommand, ButtonCommand {
    private static final Logger LOGGER = getLogger(ChatSearchCommand.class);
    private final ChatsApi chatsApi;
    private final ObjectMapper objectMapper;
    private final ChatInteractionOptionResolver resolver;
    private final PaginatedButtonHandler buttonHandler;

    public ChatSearchCommand(final ChatsApi chatsApi, final ObjectMapper objectMapper, final PaginatedButtonHandler buttonHandler) {
        this.chatsApi = chatsApi;
        this.objectMapper = objectMapper;
        this.buttonHandler = buttonHandler;
        this.resolver = new ChatInteractionOptionResolver()
            .registerOption(new PaginatedOption())
            .registerOption(new TimeRangeOption());
    }

    @Override
    public String getName() {
        return "search";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Optional<String> wordOptional = event.getOptionAsString("word");
        if (wordOptional.isEmpty()) {
            return error(event, "No word supplied");
        }
        String word = wordOptional.get();
        if (word.length() < 3 || word.length() > 50) {
            return error(event, "Word must be between 3 and 50 characters");
        }
        var ctx = resolver.resolveOptions(event);
        if (ctx.isErrorSet()) return error(event, ctx.getErrorMessage());
        return resolve(event, word, ctx.page, ctx.startDate, ctx.endDate);
    }

    public Mono<Message> resolve(DeferrableInteractionEvent event, String word, int page, LocalDate startDate, LocalDate endDate) {
        return Mono.defer(() -> {
            ChatSearchResponse response = null;
            try {
                response = chatsApi.chatSearch(word, startDate, endDate, null, 25, page);
            } catch (final Exception e) {
                if (e instanceof ApiException apiException) {
                    if (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204) {
                        return event.createFollowup()
                            .withEmbeds(EmbedCreateSpec.builder()
                                .color(Color.RUBY)
                                .description("No chats containing this word were found. That's pretty rare!")
                                .build());
                    } else if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                        LOGGER.error("Timeout searching for word: {}", word, httpTimeoutException);
                        return error(event, "Timeout searching for word. Try again in a minute");
                    }
                } else {
                    LOGGER.error("Error searching for word: {}", word, e);
                    throw new RuntimeException(e);
                }
            }
            if (response == null || response.getChats() == null || response.getChats().isEmpty())
                return event.createFollowup()
                    .withEmbeds(EmbedCreateSpec.builder()
                        .color(Color.RUBY)
                        .description("No chats containing this word were found. That's pretty rare!")
                        .build());

            final StringBuilder result = new StringBuilder();
            final AtomicBoolean truncated = new AtomicBoolean(false);
            response.getChats().stream()
                .map(c -> SHORT_DATE_TIME.format(c.getTime().toInstant()) + " **" + escape(c.getPlayerName()) + "**: " + escape(c.getChat()))
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
                .withEmbeds(EmbedCreateSpec.builder()
                    .color(Color.CYAN)
                    .description(result.toString())
                    .addField("Total", ""+response.getTotal(), true)
                    .addField("Page", page + " / " + response.getPageCount(), true)
                    .addField("\u200B", "\u200B", true)
                    .build())
                .withComponents(buttonHandler.getButtonRow(objectMapper, getName(), response.getPageCount(), page,
                    // stuff the word into the profile data's player name field bc im lazy xdd
                    new ProfileDataImpl(word, null), startDate, endDate));
        });
    }

    @Override
    public Mono<Message> handleButton(final ButtonInteractionEvent event) {
        var args = buttonHandler.decodeButtonId(objectMapper, getName(), event.getCustomId());
        return resolve(event, args.playerName(), args.page(), args.startDate(), args.endDate());
    }
}
