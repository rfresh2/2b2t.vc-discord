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
import vc.commands.options.ChatInteractionOptionResolver;
import vc.commands.options.PaginatedOption;
import vc.commands.options.TimeRangeOption;
import vc.openapi.handler.ApiException;
import vc.openapi.handler.ChatsApi;
import vc.openapi.model.ChatSearchResponse;

import java.time.LocalDate;
import java.util.Optional;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class ChatSearchCommand implements SlashCommand, PaginatedButtonListener {
    private static final Logger LOGGER = getLogger(ChatSearchCommand.class);
    private final ChatsApi chatsApi;
    private final ObjectMapper objectMapper;
    private final ChatInteractionOptionResolver resolver;

    public ChatSearchCommand(final ChatsApi chatsApi, final ObjectMapper objectMapper) {
        this.chatsApi = chatsApi;
        this.objectMapper = objectMapper;
        this.resolver = new ChatInteractionOptionResolver()
            .registerTrait(new PaginatedOption())
            .registerTrait(new TimeRangeOption());
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
        if (word.length() < 4 || word.length() > 50) {
            return error(event, "Word must be between 4 and 50 characters");
        }
        var ctx = resolver.execute(event);
        if (ctx.errorSet) return error(event, ctx.errorMessage);
        return resolve(event, word, ctx.page, ctx.startDate, ctx.endDate);
    }

    public Mono<Message> resolve(DeferrableInteractionEvent event, String word, int page, LocalDate startDate, LocalDate endDate) {
        return Mono.defer(() -> {
            ChatSearchResponse response = null;
            try {
                response = chatsApi.chatSearch(word, startDate, endDate, 25, page);
            } catch (final Exception e) {
                if (e instanceof ApiException apiException
                    && (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204)) {
                    // fall through
                } else {
                    LOGGER.error("Error searching for word: {}", word, e);
                }
            }
            if (response == null || response.getChats() == null || response.getChats().isEmpty())
                return error(event, "No chats found");
            var chatStrings = response.getChats().stream()
                .map(c -> SHORT_DATE_TIME.format(c.getTime().toInstant()) + " **" + escape(c.getPlayerName()) + ":** " + escape(c.getChat()))
                .toList();
            StringBuilder result = new StringBuilder();
            for (String s : chatStrings) {
                if (result.length() + s.length() > 4090) {
                    LOGGER.warn("Chat message too long, truncating: {}", s);
                    break;
                }
                result.append(s).append("\n");
            }

            if (!result.isEmpty()) {
                result = new StringBuilder(result.substring(0, result.length() - 1));
            } else {
                return event.createFollowup()
                    .withEmbeds(EmbedCreateSpec.builder()
                                    .title("Chats")
                                    .color(Color.CYAN)
                                    .description("No chats found")
                                    .build());
            }
            return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                                .title("Chat Search")
                                .color(Color.CYAN)
                                .description(result.toString())
                                .addField("Total", ""+response.getTotal(), true)
                                .addField("Current Page", ""+page, true)
                                .addField("Total Pages", ""+response.getPageCount(), true)
                                .build())
                .withComponents(getButtonRow(objectMapper, getName(), response.getPageCount(), page,
                                             // stuff the word into the profile data's player name field bc im lazy xdd
                                             new ProfileDataImpl(word, null), startDate, endDate));
        });
    }

    @Override
    public Mono<Message> handleButton(final ButtonInteractionEvent event) {
        var args = decodeButtonId(objectMapper, getName(), event.getCustomId());
        return resolve(event, args.playerName(), args.page(), args.startDate(), args.endDate());
    }
}
