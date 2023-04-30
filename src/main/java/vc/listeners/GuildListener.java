package vc.listeners;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.rest.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GuildListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("GuildListener");

    public GuildListener(final GatewayDiscordClient client, final RestClient restClient) {
        client.on(GuildCreateEvent.class, this::handle).subscribe();
        restClient.getGuilds().collectList().subscribe(guilds -> {
            LOGGER.info("Connected to {} guilds", guilds.size());
            guilds.forEach(guild -> LOGGER.info("Connected to guild: {}", guild));
        });
    }

    private Mono<Void> handle(final GuildCreateEvent event) {
        LOGGER.info("Joined guild: {}", event.getGuild());
        return Mono.empty();
    }
}
