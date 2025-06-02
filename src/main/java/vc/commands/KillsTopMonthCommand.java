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
        StringBuilder result = new StringBuilder();
        for (int i = 0, deathListSize = Math.min(50, response.getPlayers().size()); i < deathListSize; i++) {
            var player = response.getPlayers().get(i);
            var s = "**" + escape(player.getPlayerName()) + "**: " + player.getCount();
            if (result.length() + s.length() > 4090) {
                break;
            }
            result.append("*#").append(i + 1).append(":* ").append(s).append("\n");
        }
        if (!result.isEmpty()) {
            result.deleteCharAt(result.length() - 1);
        }
        return event.createFollowup()
            .withEmbeds(EmbedCreateSpec.builder()
                .title("Top Kills Count (30 days)")
                .color(Color.CYAN)
                .description(result.toString())
                .build());
    }
}
