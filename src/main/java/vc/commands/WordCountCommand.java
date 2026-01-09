package vc.commands;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.openapi.handler.ApiException;
import vc.openapi.handler.ChatsApi;
import vc.util.Validator;

import java.net.http.HttpTimeoutException;

import static org.slf4j.LoggerFactory.getLogger;
import static vc.util.DiscordMarkdownEscape.escape;

@Component
public class WordCountCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(WordCountCommand.class);
    private final ChatsApi chatsApi;

    public WordCountCommand(final ChatsApi chatsApi) {
        this.chatsApi = chatsApi;
    }

    @Override
    public String getName() {
        return "wordcount";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        var wordOptional = event.getOptionAsString("word");
        if (wordOptional.isEmpty()) {
            return error(event, "No word supplied");
        }
        String word = wordOptional.get();
        if (word.length() < 3 || word.length() > 50) {
            return error(event, "Word must be between 3 and 50 characters");
        }
        if (!Validator.isValidChat(word)) {
            return error(event, "Word contains invalid chat characters");
        }
        return Mono.defer(() -> {
            Integer count = null;
            try {
                count = chatsApi.wordCount(word).getCount();
            } catch (final Exception e) {
                if (e instanceof ApiException apiException) {
                    if (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204) {
                        return event.createFollowup()
                            .withEmbeds(embed(event)
                                .color(Color.RUBY)
                                .description("No chats containing this word were found. That's pretty rare!")
                                .build());
                    } else if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                        LOGGER.error("Timeout searching for word: {}", word, httpTimeoutException);
                        return error(event, "Timeout searching for word. Try again in a minute");
                    }
                } else {
                    LOGGER.error("Error getting word count: {}", word, e);
                    throw new RuntimeException(e);
                }
            }
            if (count == null) {
                return error(event, "No chats containing this word were found. That's pretty rare!");
            }
            return event.createFollowup()
                .withEmbeds(embed(event)
                    .title("Word Count")
                    .color(Color.CYAN)
                    .addField("Count", count+"", false)
                    .addField("Word", escape(word), false)
                    .build());
        });
    }
}
