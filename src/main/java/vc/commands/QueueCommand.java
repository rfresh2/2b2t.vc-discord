package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.swagger.vc.handler.QueueApi;
import vc.swagger.vc.model.Queuelength;

@Component
public class QueueCommand implements SlashCommand {
    private final QueueApi queueApi = new QueueApi();

    @Override
    public String getName() {
        return "queue";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Queuelength queuelength = queueApi.queue();
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("2b2t Queue")
                        .color(Color.CYAN)
                        .addField("Regular", queuelength.getRegular().toString(), false)
                        .addField("Prio", queuelength.getPrio().toString(), false)
                        .build());
    }
}
