package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.openapi.vc.handler.DeathsApi;
import vc.openapi.vc.model.DeathOrKillTopMonthResponse;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

@Component
public class DeathsTopMonthCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(DeathsTopMonthCommand.class);
    private final DeathsApi deathsApi;

    public DeathsTopMonthCommand(DeathsApi deathsApi) {
        this.deathsApi = deathsApi;
    }

    @Override
    public String getName() {
        return "deathsmonth";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        List<DeathOrKillTopMonthResponse> responses = null;
        try {
            responses = deathsApi.deathsTopMonth();
        } catch (final Throwable e) {
            LOGGER.error("Failed to get deaths top month", e);
        }
        if (responses == null || responses.isEmpty()) {
            return error(event, "Unable to resolve deaths list");
        }
        List<String> deathList = responses.stream()
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
            return error(event, "No deaths data found");
        }
        return event.createFollowup()
            .withEmbeds(EmbedCreateSpec.builder()
                            .title("Top Deaths Count (30 days)")
                            .color(Color.CYAN)
                            .description(resultString)
                            .build());
    }
}
