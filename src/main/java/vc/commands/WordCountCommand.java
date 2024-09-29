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
import vc.openapi.handler.ChatsApi;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

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
        Optional<String> wordOptional = event.getOption("word")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asString);
        if (wordOptional.isEmpty()) {
            return error(event, "No word supplied");
        }
        String word = wordOptional.get();
        if (word.length() < 4) {
            return error(event, "Word must be at least 4 characters");
        }
        return Mono.defer(() -> {
            int count;
            try {
                count = chatsApi.wordCount(word).getCount();
            } catch (final Exception e) {
                LOGGER.error("Error getting word count: {}", word, e);
                return error(event, "Error getting word count");
            }
            return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                                .title("Word Count")
                                .color(Color.CYAN)
                                .addField("Count", count+"", false)
                                .addField("Word", escape(word), false)
                                .build());
        });
    }
}
