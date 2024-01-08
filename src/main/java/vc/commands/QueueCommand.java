package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.openapi.vc.handler.QueueApi;
import vc.openapi.vc.model.Queuelength;

import static java.util.Objects.isNull;

@Component
public class QueueCommand implements SlashCommand {
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
        Queuelength queuelength = queueApi.queue();
        if (isNull(queuelength)) return error(event, "Unable to resolve queue length");
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("2b2t Queue")
                        .color(Color.CYAN)
                        .addField("Prio", queuelength.getPrio().toString(), true)
                        .addField("Regular", queuelength.getRegular().toString(), true)
                        .addField("ETA", getEtaStringFromSeconds(getQueueWaitInSeconds(queuelength.getRegular())), true)
                        .build());
    }

    // probably only valid for regular queue, prio seems to move a lot faster
    // returns double representing seconds until estimated queue completion time.
    public static long getQueueWaitInSeconds(final Integer queuePos) {
        return (long) (12.7 * (Math.pow(queuePos.doubleValue(), 1.28)));
    }

    public static String getEtaStringFromSeconds(final double totalSeconds) {
        final int hour = (int) (totalSeconds / 3600);
        final int minutes = (int) ((totalSeconds / 60) % 60);
        final int seconds = (int) (totalSeconds % 60);
        final String hourStr = hour >= 10 ? "" + hour : "0" + hour;
        final String minutesStr = minutes >= 10 ? "" + minutes : "0" + minutes;
        final String secondsStr = seconds >= 10 ? "" + seconds : "0" + seconds;
        return hourStr + ":" + minutesStr + ":" + secondsStr;
    }
}
