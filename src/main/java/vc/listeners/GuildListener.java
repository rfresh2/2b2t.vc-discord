package vc.listeners;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.discordjson.json.UserGuildData;
import discord4j.rest.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Component
public class GuildListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("GuildListener");

    public GuildListener(final GatewayDiscordClient client, final RestClient restClient) {
        client.on(GuildCreateEvent.class, this::handle).subscribe();
        restClient.getGuilds().collectList().subscribe(guilds -> {
            LOGGER.info("Connected to {} guilds", guilds.size());
            LOGGER.info("Connected to guilds: {}", guilds.stream()
                .map(UserGuildData::name)
                .collect(Collectors.joining("'\n'", "\n'", "'")));
        });
    }

    private Mono<Void> handle(final GuildCreateEvent event) {
        LOGGER.info("Joined guild: {}", event.getGuild().getName());
        return Mono.empty();
    }
}
