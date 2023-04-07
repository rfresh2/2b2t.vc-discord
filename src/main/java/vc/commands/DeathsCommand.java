package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.swagger.mojang_api.model.ProfileLookup;
import vc.swagger.vc.handler.DeathsApi;
import vc.swagger.vc.model.Deaths;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.List;
import java.util.Optional;

@Component
public class DeathsCommand implements SlashCommand {

    private final DeathsApi deathsApi = new DeathsApi();
    private final PlayerLookup playerLookup;

    public DeathsCommand(final PlayerLookup playerLookup) {
        this.playerLookup = playerLookup;
    }

    @Override
    public String getName() {
        return "deaths";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Optional<String> playerNameOptional = event.getOption("username")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        return playerNameOptional
                .filter(Validator::isValidUsername)
                .flatMap(playerLookup::getPlayerProfile)
                .map(profile -> resolveDeaths(event, profile))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolveDeaths(final ChatInputInteractionEvent event, final ProfileLookup profile) {
        List<Deaths> deaths = deathsApi.deaths(playerLookup.getProfileUUID(profile), 0);
        List<String> deathStrings = deaths.stream()
                .map(k -> "<t:" + k.getTime().toEpochSecond() + ":f>: " + escape(k.getDeathMessage()))
                .toList();
        String result = "";
        for (String s : deathStrings) {
            if (result.length() + s.length() > 4090) {
                break;
            }
            result += s + "\n";
        }
        if (result.length() > 0) {
            result = result.substring(0, result.length() - 1);
        } else {
            return event.createFollowup()
                    .withEmbeds(EmbedCreateSpec.builder()
                            .title("Deaths: " + escape(profile.getName()))
                            .color(Color.CYAN)
                            .description("No deaths found")
                            .thumbnail(playerLookup.getAvatarURL(profile.getId()).toString())
                            .build());
        }
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("Deaths: " + escape(profile.getName()))
                        .color(Color.CYAN)
                        .description(result)
                        .thumbnail(playerLookup.getAvatarURL(profile.getId()).toString())
                        .build());
    }
}
