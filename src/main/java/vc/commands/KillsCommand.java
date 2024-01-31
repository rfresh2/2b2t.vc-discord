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
import vc.api.model.ProfileData;
import vc.openapi.vc.handler.DeathsApi;
import vc.openapi.vc.model.KillsResponse;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.List;
import java.util.Optional;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class KillsCommand extends PlayerLookupCommand {
    private static final Logger LOGGER = getLogger(KillsCommand.class);
    private final DeathsApi deathsApi;

    public KillsCommand(final DeathsApi deathsApi, final PlayerLookup playerLookup) {
        super(playerLookup);
        this.deathsApi = deathsApi;
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
                .orElse(1);
        if (page <= 0)
            return error(event, "Page must be greater than 0");
        return playerNameOptional
                .filter(Validator::isValidPlayerName)
                .flatMap(playerLookup::getPlayerIdentity)
                .map(identity -> resolveKills(event, identity, page))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolveKills(final ChatInputInteractionEvent event, final ProfileData identity, int page) {
        KillsResponse killsResponse = null;
        try {
            killsResponse = deathsApi.kills(identity.uuid(), null, 25, page);
        } catch (final Exception e) {
            LOGGER.error("Error resolving kills", e);
        }
        if (killsResponse == null || killsResponse.getKills() == null || killsResponse.getKills().isEmpty())
            return error(event, "No kills found for player");
        List<String> killStrings = killsResponse.getKills().stream()
                .map(k -> SHORT_DATE_TIME.format(k.getTime().toInstant()) + " " + escape(k.getDeathMessage()))
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
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                                .title("Kills")
                                .color(Color.CYAN)
                                .description("No kills found")
                                .thumbnail(playerLookup.getAvatarURL(identity.uuid()).toString())
                                .build());
        }
        return event.createFollowup()
            .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                            .title("Kills")
                            .color(Color.CYAN)
                            .description(result.toString())
                            .addField("Total", ""+killsResponse.getTotal(), true)
                            .addField("Page", ""+page, true)
                            .addField("Page Count", ""+killsResponse.getPageCount(), true)
                            .thumbnail(playerLookup.getAvatarURL(identity.uuid()).toString())
                            .build());
    }
}
