package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.openapi.handler.QueueApi;
import vc.openapi.model.Queuelength;
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
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        return Mono.defer(() -> {
            Queuelength queuelength = null;
            try {
                queuelength = queueApi.queue();
            } catch (final Exception e) {
                LOGGER.error("Failed to get queue length", e);
            }
            if (isNull(queuelength)) return error(event, "Unable to resolve queue length");
            return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                                .title("2b2t Queue")
                                .color(Color.CYAN)
                                .addField("Prio", queuelength.getPrio().toString(), true)
                                .addField("Regular", queuelength.getRegular().toString(), true)
                                .addField("ETA", QueueETA.INSTANCE.getEtaString(queuelength.getRegular()), true)
                                .build());
        });

    }
}
