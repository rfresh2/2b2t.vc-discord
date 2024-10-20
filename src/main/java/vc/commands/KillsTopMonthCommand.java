package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.openapi.handler.DeathsApi;
import vc.openapi.model.PlayerDeathOrKillCountResponse;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

@Component
public class KillsTopMonthCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(KillsTopMonthCommand.class);
    private final DeathsApi deathsApi;

    public KillsTopMonthCommand(DeathsApi deathsApi) {
        this.deathsApi = deathsApi;
    }

    @Override
    public String getName() {
        return "killsmonth";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        PlayerDeathOrKillCountResponse response = null;
        try {
            response = deathsApi.killsTopMonth();
        } catch (final Throwable e) {
            LOGGER.error("Failed to get kills top month response", e);
        }
        if (response == null || response.getPlayers() == null || response.getPlayers().isEmpty()) {
            return error(event, "Unable to resolve kills list");
        }
        List<String> deathList = response.getPlayers().stream()
                .map(death -> "**" + escape(death.getPlayerName()) + "**: " + death.getCount())
                .toList();
        StringBuilder result = new StringBuilder();
        for (int i = 0, deathListSize = Math.min(50, deathList.size()); i < deathListSize; i++) {
            final String s = deathList.get(i);
            if (result.length() + s.length() > 4090) {
                break;
            }
            result.append("*#" + (i+1) + ":* ").append(s).append("\n");
        }
        String resultString = result.toString();
        if (resultString.length() > 0) {
            resultString = resultString.substring(0, resultString.length() - 1);
        } else {
            return error(event, "No kills data found");
        }
        return event.createFollowup()
            .withEmbeds(EmbedCreateSpec.builder()
                            .title("Top Kills Count (30 days)")
                            .color(Color.CYAN)
                            .description(resultString)
                            .build());
    }
}
