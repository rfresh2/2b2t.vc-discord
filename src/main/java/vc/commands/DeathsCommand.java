package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.swagger.mojang_api.model.ProfileLookup;
import vc.swagger.vc.handler.DeathsApi;
import vc.swagger.vc.model.Deaths;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.isNull;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class DeathsCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(DeathsCommand.class);
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
        int page = event.getOption("page")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asLong)
                .map(Long::intValue)
                .orElse(0);
        return playerNameOptional
                .filter(Validator::isValidUsername)
                .flatMap(playerLookup::getPlayerProfile)
                .map(profile -> resolveDeaths(event, profile, page))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolveDeaths(final ChatInputInteractionEvent event, final ProfileLookup profile, int page) {
        List<Deaths> deaths = deathsApi.deaths(playerLookup.getProfileUUID(profile), 25, page);
        if (isNull(deaths) || deaths.isEmpty()) return error(event, "No deaths found for player");
        List<String> deathStrings = deaths.stream()
                .map(k -> "<t:" + k.getTime().toEpochSecond() + ":f>: " + escape(k.getDeathMessage()))
                .toList();
        StringBuilder result = new StringBuilder();
        for (String s : deathStrings) {
            if (result.length() + s.length() > 4090) {
                LOGGER.warn("Message too long, truncating: {}", s);
                break;
            }
            result.append(s).append("\n");
        }
        if (result.length() > 0) {
            result = new StringBuilder(result.substring(0, result.length() - 1));
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
                        .description(result.toString())
                        .thumbnail(playerLookup.getAvatarURL(profile.getId()).toString())
                        .build());
    }
}
