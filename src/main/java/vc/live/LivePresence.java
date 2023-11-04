package vc.live;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.object.presence.Status;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import vc.swagger.vc.handler.QueueApi;
import vc.swagger.vc.model.Queuelength;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;

@Component
public class LivePresence {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LivePresence");
    private final ScheduledExecutorService scheduledExecutorService;
    private final GatewayDiscordClient discordClient;
    private final QueueApi queueApi;
    private final Random random = new Random();

    // todo: could be cool to have a secondary list of all 2b2t motd's and randomly select one of those

    private static final List<String> statusMessages = asList(
        "/commands",
        "2b2t Chat! /livechat",
        "2b2t is full"
    );

    public LivePresence(final ScheduledExecutorService scheduledExecutorService, final GatewayDiscordClient discordClient, final QueueApi queueApi) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.discordClient = discordClient;
        this.queueApi = queueApi;
        this.scheduledExecutorService.scheduleWithFixedDelay(this::updatePresence, 1, 1, TimeUnit.MINUTES);
    }

    void updatePresence() {
        try {
            this.discordClient.updatePresence(
                ClientPresence.of(
                    Status.ONLINE,
                    ClientActivity.custom(selectStatusMessage())))
                .block();
        } catch (final Exception e) {
            LOGGER.error("Error updating presence", e);
        }
    }

    private String selectStatusMessage() {
        if (random.nextInt(2) > 0) {
            return getQueueStatus().orElse(randomStaticStatusMessage());
        } else {
            return randomStaticStatusMessage();
        }
    }

    private String randomStaticStatusMessage() {
        return statusMessages.get(random.nextInt(statusMessages.size()));
    }

    Optional<String> getQueueStatus() {
        try {
            Queuelength queuelength = queueApi.queue();
            return Optional.of(String.format("Q: %d | Prio: %d", queuelength.getRegular(), queuelength.getPrio()));
        } catch (final Exception e) {
            LOGGER.error("Error getting queue status", e);
            return Optional.empty();
        }
    }
}
