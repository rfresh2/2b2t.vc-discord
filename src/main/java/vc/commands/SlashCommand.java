package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

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

    // throws runtime exception if date is present but format is invalid
    default @Nullable LocalDate getLocalDateIfPresent(ChatInputInteractionEvent event, String argName) {
        var inputOptional = event.getOption(argName)
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asString);
        if (inputOptional.isEmpty()) return null;
        try {
            return LocalDate.parse(inputOptional.get());
        } catch (Exception e) {
            throw new RuntimeException("Failed parsing date: " + inputOptional.get());
        }
    }
}
