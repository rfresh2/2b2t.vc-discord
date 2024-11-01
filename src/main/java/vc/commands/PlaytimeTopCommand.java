package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.openapi.handler.PlaytimeApi;
import vc.openapi.model.PlaytimeAllTimeResponse;

import java.util.ArrayList;
import java.util.List;

@Component
public class PlaytimeTopCommand implements SlashCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlaytimeTopCommand.class);
    private final PlaytimeApi playtimeApi;

    public PlaytimeTopCommand(final PlaytimeApi playtimeApi) {
        this.playtimeApi = playtimeApi;
    }

    @Override
    public String getName() {
        return "playtimetop";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        return Mono.defer(() -> {
            PlaytimeAllTimeResponse response = null;
            try {
                response = playtimeApi.playtimeTopAllTime();
            } catch (final Exception e) {
                LOGGER.error("Failed to get playtime top all time", e);
            }
            if (response == null || response.getPlayers() == null || response.getPlayers().isEmpty())
                return error(event, "Unable to resolve playtime top data");
            StringBuilder result = new StringBuilder();
            for (int i = 0, ptListSize = Math.min(50, response.getPlayers().size()); i < ptListSize; i++) {
                var player = response.getPlayers().get(i);
                var seconds = player.getPlaytimeSeconds();
                var s = "**" + escape(player.getPlayerName()) + "**: " + formatDuration(seconds);
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
                                .title("Top Playtime")
                                .color(Color.CYAN)
                                .description(result.toString())
                                .build());
        });
    }

    private String formatDuration(long durationInSeconds) {
        var secondsInMinute = 60L;
        var secondsInHour = secondsInMinute * 60L;
        var secondsInDay = secondsInHour * 24L;
        var secondsInMonth = secondsInDay * 30L; // assuming 30 days per month
        var secondsInYear = secondsInMonth * 12L;

        var years = durationInSeconds / secondsInYear;
        var months = (durationInSeconds % secondsInYear) / secondsInMonth;
        var days = (durationInSeconds % secondsInMonth) / secondsInDay;
        List<String> entries = new ArrayList<>(3);
        if (years > 0) entries.add(years + " year" + (years != 1 ? "s" : ""));
        if (months > 0) entries.add(months + " month" + (months != 1 ? "s" : ""));
        if (days > 0) entries.add(days + " day" + (days != 1 ? "s" : ""));
        return String.join(", ", entries);
    }
}
