package vc.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vc.openapi.handler.PlaytimeApi;
import vc.openapi.model.PlaytimeAllTimeResponse;

import java.util.ArrayList;
import java.util.List;

import static vc.util.DiscordMarkdownEscape.escape;

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
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        PlaytimeAllTimeResponse response = null;
        try {
            response = playtimeApi.playtimeTopAllTime();
        } catch (final Exception e) {
            LOGGER.error("Failed to get playtime top all time", e);
        }
        if (response == null || response.getPlayers() == null || response.getPlayers().isEmpty()) {
            return error(event, "Unable to resolve playtime top data");
        }
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
        return event.getHook().sendMessageEmbeds(embed(event)
            .setTitle("Top Playtime")
            .setColor(Color.CYAN)
            .setDescription(result.toString())
            .build());
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
