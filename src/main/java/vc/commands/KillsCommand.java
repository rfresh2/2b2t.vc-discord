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
import vc.swagger.vc.handler.DeathsApi;
import vc.swagger.vc.model.Deaths;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.isNull;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class KillsCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(KillsCommand.class);
    private final DeathsApi deathsApi;
    private final PlayerLookup playerLookup;

    public KillsCommand(final DeathsApi deathsApi, final PlayerLookup playerLookup) {
        this.deathsApi = deathsApi;
        this.playerLookup = playerLookup;
    }

    @Override
    public String getName() {
        return "kills";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Optional<String> playerNameOptional = event.getOption("playername")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        int page = event.getOption("page")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asLong)
                .map(Long::intValue)
                .orElse(0);
        return playerNameOptional
                .filter(Validator::isValidPlayerName)
                .flatMap(playerLookup::getPlayerIdentity)
                .map(identity -> resolveKills(event, identity, page))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolveKills(final ChatInputInteractionEvent event, final PlayerLookup.PlayerIdentity identity, int page) {
        List<Deaths> kills = deathsApi.kills(identity.uuid(), null, 25, page);
        if (isNull(kills) || kills.isEmpty()) return error(event, "No kills found for player");
        List<String> killStrings = kills.stream()
                .map(k -> "<t:" + k.getTime().toEpochSecond() + ":f>: " + escape(k.getDeathMessage()))
                .toList();
        StringBuilder result = new StringBuilder();
        for (String s : killStrings) {
            if (result.length() + s.length() > 4090) {
                LOGGER.warn("Kills message too long, truncating: {}", s);
                break;
            }
            result.append(s).append("\n");
        }
        if (result.length() > 0) {
            result = new StringBuilder(result.substring(0, result.length() - 1));
        } else {
            return event.createFollowup()
                    .withEmbeds(EmbedCreateSpec.builder()
                            .title("Kills: " + escape(identity.playerName()))
                            .color(Color.CYAN)
                            .description("No kills found")
                            .thumbnail(playerLookup.getAvatarURL(identity.uuid()).toString())
                            .build());
        }
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("Kills: " + escape(identity.playerName()))
                        .color(Color.CYAN)
                        .description(result.toString())
                        .thumbnail(playerLookup.getAvatarURL(identity.uuid()).toString())
                        .build());
    }
}
