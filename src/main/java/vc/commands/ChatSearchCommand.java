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
import vc.openapi.model.ChatSearchResponse;

import java.util.Optional;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class ChatSearchCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(ChatSearchCommand.class);
    private final ChatsApi chatsApi;

    public ChatSearchCommand(final ChatsApi chatsApi) {
        this.chatsApi = chatsApi;
    }

    @Override
    public String getName() {
        return "search";
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
        if (word.length() < 4 || word.length() > 50) {
            return error(event, "Word must be between 4 and 50 characters");
        }
        int page = event.getOption("page")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asLong)
            .map(Long::intValue)
            .orElse(1);
        if (page <= 0)
            return error(event, "Page must be greater than 0");
        return Mono.defer(() -> {
            ChatSearchResponse response;
            try {
                response = chatsApi.chatSearch(word, null, null, 25, page);
            } catch (final Exception e) {
                LOGGER.error("Error searching chats for word: {}", word, e);
                return error(event, "Error during search");
            }
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
                                .build());
        });
    }
}
