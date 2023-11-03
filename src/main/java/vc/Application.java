package vc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.object.presence.Status;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.RestClient;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import vc.config.GuildConfigDatabase;
import vc.config.GuildConfigManager;
import vc.live.LiveChat;
import vc.live.LiveConnections;
import vc.live.LiveFeedManager;
import vc.live.RedisClient;
import vc.util.PlayerLookup;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.slf4j.LoggerFactory.getLogger;

@SpringBootApplication
public class Application {
    @Value("${BOT_TOKEN}")
    String token;
    @Value("${REDIS_URL}")
    String redisURL;
    @Value("${REDIS_USERNAME}")
    String redisUsername;
    @Value("${REDIS_PASSWORD}")
    String redisPassword;

    private static final Logger LOGGER = getLogger("Application");

    public static void main(String[] args) {
        new SpringApplicationBuilder(Application.class)
            .build()
            .run(args);
    }

    @Bean
    public GatewayDiscordClient gatewayDiscordClient() {
        return DiscordClientBuilder.create(token).build()
                .gateway()
                .setEnabledIntents(IntentSet.none())
                .setInitialPresence(ignore -> ClientPresence.of(Status.ONLINE, ClientActivity.custom("/commands")))
                .login()
                .block();
    }

    @Bean
    public RestClient discordRestClient(GatewayDiscordClient client) {
        return client.getRestClient();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public PlayerLookup playerLookup() {
        return new PlayerLookup();
    }

    @Bean
    public RedisClient redisClient() {
        return new RedisClient(redisURL, redisUsername, redisPassword);
    }

    @Bean
    public GuildConfigManager guildConfigManager(final ScheduledExecutorService scheduledExecutorService) {
        return new GuildConfigManager(new GuildConfigDatabase(), scheduledExecutorService);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(4, new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("scheduled-%d")
                .setUncaughtExceptionHandler((t, e) -> LOGGER.error("Uncaught exception in scheduled thread: {}", t.getName(), e))
            .build());
    }

    @Bean
    public LiveFeedManager liveFeedManager(final LiveChat liveChat, final LiveConnections liveConnections) {
        return new LiveFeedManager(liveChat, liveConnections);
    }
}
