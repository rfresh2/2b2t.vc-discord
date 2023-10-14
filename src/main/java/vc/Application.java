package vc;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import vc.util.PlayerLookup;

@SpringBootApplication
public class Application implements ApplicationRunner {
    @Value("${BOT_TOKEN}")
    String token;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }


    @Bean
    public GatewayDiscordClient gatewayDiscordClient() {
        return DiscordClientBuilder.create(token).build()
                .gateway()
                .setEnabledIntents(IntentSet.none())
                .setInitialPresence(ignore -> ClientPresence.online(ClientActivity.listening("/commands")))
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

    @Override
    public void run(final ApplicationArguments args) throws Exception {
        System.out.println("Application started");
    }
}
