package vc.commands.buttons;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;

public interface ButtonCommand {
    WebhookMessageCreateAction<Message> handleButton(ButtonInteractionEvent event);

    WebhookMessageCreateAction<Message> handleModal(ModalInteractionEvent event);
}
