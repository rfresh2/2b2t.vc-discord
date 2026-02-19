package vc.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import vc.openapi.handler.QueueApi;
import vc.openapi.model.QueueData;
import vc.util.QueueETA;

import static java.util.Objects.isNull;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class QueueCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(QueueCommand.class);
    private final QueueApi queueApi;

    public QueueCommand(final QueueApi queueApi) {
        this.queueApi = queueApi;
    }

    @Override
    public String getName() {
        return "queue";
    }

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        QueueData queueLength = null;
        try {
            queueLength = queueApi.queue();
        } catch (final Exception e) {
            LOGGER.error("Failed to get queue length", e);
        }
        if (isNull(queueLength)) return error(event, "Unable to resolve queue length");
        return event.getHook().sendMessageEmbeds(embed(event)
            .setColor(Color.CYAN)
            .addField("Prio", queueLength.getPrio().toString(), true)
            .addField("Regular", queueLength.getRegular().toString(), true)
            .addField("ETA", QueueETA.INSTANCE.getEtaString(queueLength.getRegular()), true)
            .build());
    }
}
