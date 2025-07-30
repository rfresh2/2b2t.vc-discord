package vc.listeners;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.object.entity.Guild;
import discord4j.rest.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.config.live_feed.LiveFeedConfigManager;
import vc.config.watch.WatchConfigManager;
import vc.live.LiveFeedManager;
import vc.live.watch.WatchManager;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class GuildListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("GuildListener");
    private final LiveFeedConfigManager guildConfigManager;
    private final LiveFeedManager liveFeedManager;
    private final WatchConfigManager watchConfigManager;
    private final WatchManager watchManager;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public GuildListener(
        final GatewayDiscordClient client,
        final RestClient restClient,
        final LiveFeedConfigManager guildConfigManager,
        final LiveFeedManager liveFeedManager,
        final WatchConfigManager watchConfigManager,
        final WatchManager watchManager
    ) {
        this.guildConfigManager = guildConfigManager;
        this.liveFeedManager = liveFeedManager;
        this.watchConfigManager = watchConfigManager;
        this.watchManager = watchManager;
        client.getEventDispatcher().on(GuildCreateEvent.class, this::handleGuildCreateJoin).subscribe();
        client.getEventDispatcher().on(GuildDeleteEvent.class, this::handleGuildDeleteLeave).subscribe();
        restClient.getGuilds().collectList().subscribe(guilds -> {
            LOGGER.info("Connected to {} guilds", guilds.size());
            guilds.forEach(guildConfigManager::loadGuild);
            guildConfigManager.writeAllLiveFeedConfigs();
            liveFeedManager.onAllGuildsLoaded();
            watchManager.onAllGuildsLoaded(guilds);
            initialized.set(true);
        });
    }

    private Mono<Void> handleGuildCreateJoin(final GuildCreateEvent event) {
        if (initialized.get()) LOGGER.info("Joined guild: {}", event.getGuild().getName());
        guildConfigManager.loadGuild(event.getGuild().getData());
        return Mono.empty();
    }

    private Mono<Void> handleGuildDeleteLeave(final GuildDeleteEvent event) {
        if (event.isUnavailable()) {
            LOGGER.info("Guild unavailable: {}", event.getGuild().map(Guild::getId).orElse(null));
            return Mono.empty();
        }
        var guildName = event.getGuild().map(Guild::getName).orElse("?");
        if (initialized.get()) LOGGER.info("Left guild: ({}) {}", event.getGuildId().asString(), guildName);
        liveFeedManager.disableFeedsInGuild(event.getGuildId().asString());
        watchManager.removeWatchesInGuild(event.getGuildId().asString());
        return Mono.empty();
    }
}
