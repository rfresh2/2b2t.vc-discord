package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.openapi.vc.handler.DeathsApi;
import vc.openapi.vc.model.DeathsResponse;
import vc.util.PlayerLookup;

import java.util.List;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class DeathsCommand extends PlayerLookupCommand {
    private static final Logger LOGGER = getLogger(DeathsCommand.class);
    private final DeathsApi deathsApi;

    public DeathsCommand(final DeathsApi deathsApi, final PlayerLookup playerLookup) {
        super(playerLookup);
        this.deathsApi = deathsApi;
    }

    @Override
    public String getName() {
        return "deaths";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        return resolveData(event, this::resolveDeaths);
    }

    private Mono<Message> resolveDeaths(final ChatInputInteractionEvent event, final ProfileData identity, int page) {
        DeathsResponse deathsResponse = null;
        try {
            deathsResponse = deathsApi.deaths(identity.uuid(), null, 25, page);
        } catch (final Exception e) {
            LOGGER.error("Failed to get deaths", e);
        }
        if (deathsResponse == null || deathsResponse.getDeaths() == null || deathsResponse.getDeaths().isEmpty())
            return error(event, "No deaths found for player");
        List<String> deathStrings = deathsResponse.getDeaths().stream()
                .map(k -> SHORT_DATE_TIME.format(k.getTime().toInstant()) + " " + escape(k.getDeathMessage()))
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
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                                .title("Deaths")
                                .color(Color.CYAN)
                                .description("No deaths found")
                                .thumbnail(playerLookup.getAvatarURL(identity.uuid()).toString())
                                .build());
        }
        return event.createFollowup()
            .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                            .title("Deaths")
                            .color(Color.CYAN)
                            .description(result.toString())
                            .addField("Total", ""+deathsResponse.getTotal(), true)
                            .addField("Page", ""+page, true)
                            .addField("Page Count", ""+deathsResponse.getPageCount(), true)
                            .thumbnail(playerLookup.getAvatarURL(identity.uuid()).toString())
                            .build());
    }
}
