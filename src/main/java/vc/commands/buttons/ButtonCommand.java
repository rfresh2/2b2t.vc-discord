package vc.commands.buttons;

import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.entity.Message;
import reactor.core.publisher.Mono;

public interface ButtonCommand {
    Mono<Message> handleButton(ButtonInteractionEvent event);
}
