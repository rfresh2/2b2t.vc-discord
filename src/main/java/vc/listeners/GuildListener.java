package vc.listeners;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.object.entity.Guild;
import discord4j.discordjson.json.UserGuildData;
import discord4j.rest.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.config.GuildConfigManager;
import vc.live.LiveFeedManager;

import java.util.stream.Collectors;

@Component
public class GuildListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("GuildListener");
    private final GuildConfigManager guildConfigManager;
    private final LiveFeedManager liveFeedManager;

    public GuildListener(final GatewayDiscordClient client,
                         final RestClient restClient,
                         final GuildConfigManager guildConfigManager,
                         final LiveFeedManager liveFeedManager) {
        this.guildConfigManager = guildConfigManager;
        this.liveFeedManager = liveFeedManager;
        client.getEventDispatcher().on(GuildCreateEvent.class, this::handleGuildCreateJoin).subscribe();
        client.getEventDispatcher().on(GuildDeleteEvent.class, this::handleGuildDeleteLeave).subscribe();
        restClient.getGuilds().collectList().subscribe(guilds -> {
            LOGGER.info("Connected to {} guilds", guilds.size());
            LOGGER.info("Connected to guilds: {}", guilds.stream()
                .map(UserGuildData::name)
                .collect(Collectors.joining("'\n'", "\n'", "'")));
            guilds.forEach(guildConfigManager::loadGuild);
            guildConfigManager.writeAllGuildConfigs();
            liveFeedManager.onAllGuildsLoaded();
        });
    }

    private Mono<Void> handleGuildCreateJoin(final GuildCreateEvent event) {
        LOGGER.info("Joined guild: {}", event.getGuild().getName());
        guildConfigManager.loadGuild(event.getGuild().getData());
        return Mono.empty();
    }

    private Mono<Void> handleGuildDeleteLeave(final GuildDeleteEvent event) {
        if (event.isUnavailable()) {
            LOGGER.info("Guild unavailable: {}", event.getGuild().map(Guild::getId).orElse(null));
            return Mono.empty();
        }
        var guildName = event.getGuild().map(Guild::getName).orElse("?");
        LOGGER.info("Left guild: ({}) {}", event.getGuildId(), guildName);
        liveFeedManager.disableFeedsInGuild(event.getGuildId().asString());
        return Mono.empty();
    }
}
