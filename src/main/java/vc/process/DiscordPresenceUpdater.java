package vc.process;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vc.openapi.handler.QueueApi;
import vc.openapi.handler.TabListApi;
import vc.util.QueueETA;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;

@Component
public class DiscordPresenceUpdater {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LivePresence");
    private final JDA jda;
    private final QueueApi queueApi;
    private final TabListApi tabListApi;
    private final Random random = new Random();

    private static final List<String> statusMessages = asList(
        "/commands",
        "2b2t Chat! /livechat",
        "2b2t is full",
        "Powered by ZenithProxy!"
    );

    public DiscordPresenceUpdater(final JDA jda, final QueueApi queueApi, final TabListApi tabListApi) {
        this.jda = jda;
        this.queueApi = queueApi;
        this.tabListApi = tabListApi;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    void updatePresence() {
        try {
            this.jda.getPresence().setActivity(Activity.playing(selectStatusMessage()));
        } catch (final Exception e) {
            LOGGER.error("Error updating presence", e);
        }
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
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
