package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

/**
 * A simple interface defining our slash command class contract.
 *  a getName() method to provide the case-sensitive name of the command.
 *  and a handle() method which will house all the logic for processing each command.
 */
public interface SlashCommand {

    String getName();

    Mono<Message> handle(ChatInputInteractionEvent event);

    default Mono<Message> error(ChatInputInteractionEvent event, final String message) {
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("Error")
                        .color(Color.RUBY)
                        .description(message)
                        .build());
    }

    default String escape(String message) {
        return message.replaceAll("_", "\\\\_");
    }
}
