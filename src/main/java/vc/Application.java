package vc;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
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
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.threeten.bp.OffsetDateTime;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.slf4j.LoggerFactory.getLogger;

@SpringBootApplication
public class Application {
    @Value("${BOT_TOKEN}")
    String token;
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
    public RestTemplate restTemplate(ObjectMapper objectMapper) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(0, new MappingJackson2HttpMessageConverter(objectMapper));
        return restTemplate;
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        objectMapper.registerModule(javaTimeModule);
        javaTimeModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
        javaTimeModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
//        objectMapper.registerModule(new ThreeTenModule());
        objectMapper.registerModule(javaTimeModule);

        return objectMapper;
    }


    public static class OffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {
        @Override
        public void serialize(OffsetDateTime arg0, JsonGenerator arg1, SerializerProvider arg2) throws IOException {
            arg1.writeString(arg0.toString());
        }
    }

    public static class OffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {
        @Override
        public OffsetDateTime deserialize(JsonParser arg0, DeserializationContext arg1) throws IOException {
            return OffsetDateTime.parse(arg0.getText());
        }
    }

    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(4, new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("scheduled-%d")
                .setUncaughtExceptionHandler((t, e) -> LOGGER.error("Uncaught exception in scheduled thread: {}", t.getName(), e))
            .build());
    }
}
