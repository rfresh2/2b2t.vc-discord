package vc.process;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.object.presence.Status;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import vc.openapi.handler.QueueApi;
import vc.openapi.handler.TabListApi;
import vc.util.QueueETA;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;

@Component
public class DiscordPresenceUpdater {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LivePresence");
    private final ScheduledExecutorService scheduledExecutorService;
    private final GatewayDiscordClient discordClient;
    private final QueueApi queueApi;
    private final TabListApi tabListApi;
    private final Random random = new Random();

    // todo: could be cool to have a secondary list of all 2b2t motd's and randomly select one of those

    private static final List<String> statusMessages = asList(
        "/commands",
        "2b2t Chat! /livechat",
        "2b2t is full",
        "Powered by ZenithProxy!"
    );

    public DiscordPresenceUpdater(final ScheduledExecutorService scheduledExecutorService, final GatewayDiscordClient discordClient, final QueueApi queueApi, final TabListApi tabListApi) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.discordClient = discordClient;
        this.queueApi = queueApi;
        this.tabListApi = tabListApi;
        this.scheduledExecutorService.scheduleWithFixedDelay(this::updatePresence, 1, 1, TimeUnit.MINUTES);
        this.scheduledExecutorService.scheduleWithFixedDelay(this::updateEtaEquation, 0, 1, TimeUnit.HOURS);
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

    void updateEtaEquation() {
        try {
            if (QueueETA.INSTANCE.lastUpdate().isAfter(Instant.now().minusSeconds(30))) return;
            var equation = queueApi.etaEquation();
            if (equation.getFactor() == null) {
                LOGGER.error("Null queue ETA factor: {}", equation);
                return;
            }
            if (equation.getPow() == null) {
                LOGGER.error("Null queue ETA pow: {}", equation);
                return;
            }
            QueueETA.INSTANCE = new QueueETA(equation.getFactor(), equation.getPow(), Instant.now());
        } catch (final Exception e) {
            LOGGER.error("Failed updating queue ETA equation");
            scheduledExecutorService.schedule(this::updateEtaEquation, 1L, TimeUnit.MINUTES);
        }
    }

    private String selectStatusMessage() {
        return switch (random.nextInt(3)) {
            case 0 -> getQueueStatus().orElse(randomStaticStatusMessage());
            case 1 -> getPlayerCount().orElse(randomStaticStatusMessage());
            default -> randomStaticStatusMessage();
        };
    }

    private String randomStaticStatusMessage() {
        return statusMessages.get(random.nextInt(statusMessages.size()));
    }

    Optional<String> getQueueStatus() {
        try {
            var queuelength = queueApi.queue();
            return Optional.of(String.format("Q: %d | Prio: %d | ETA: %s",
                                             queuelength.getRegular(),
                                             queuelength.getPrio(),
                                             QueueETA.INSTANCE.getEtaString(queuelength.getRegular() != null ? queuelength.getRegular() : 0)));
        } catch (final Exception e) {
            LOGGER.error("Error getting queue status", e);
            return Optional.empty();
        }
    }

    Optional<String> getPlayerCount() {
        try {
            return Optional.of(String.format("%d Players Online", tabListApi.onlinePlayers().getPlayers().size()));
        } catch (final Exception e) {
            LOGGER.error("Error getting player count", e);
            return Optional.empty();
        }
    }
}
