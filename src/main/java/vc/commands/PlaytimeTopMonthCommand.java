package vc.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import vc.openapi.handler.PlaytimeApi;
import vc.openapi.model.PlaytimeMonthResponse;

import java.text.DecimalFormat;

import static org.slf4j.LoggerFactory.getLogger;
import static vc.util.DiscordMarkdownEscape.escape;

@Component
public class PlaytimeTopMonthCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(PlaytimeTopMonthCommand.class);
    private final PlaytimeApi playtimeApi;
    private static final DecimalFormat DF = new DecimalFormat("0.00");

    public PlaytimeTopMonthCommand(final PlaytimeApi playtimeApi) {
        this.playtimeApi = playtimeApi;
    }

    @Override
    public String getName() {
        return "playtimemonth";
    }

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        PlaytimeMonthResponse response = null;
        try {
            response = playtimeApi.playtimeTopMonth();
        } catch (final Exception e) {
            LOGGER.error("Failed to get playtime top month", e);
        }
        if (response == null || response.getPlayers() == null || response.getPlayers().isEmpty()) {
            return error(event, "Unable to resolve playtime list");
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0, ptListSize = Math.min(50, response.getPlayers().size()); i < ptListSize; i++) {
            var player = response.getPlayers().get(i);
            var s = "**" + escape(player.getPlayerName()) + "**: " + DF.format(player.getPlaytimeDays()) + "d";
            if (result.length() + s.length() > 4090) {
                break;
            }
            result.append("*#").append(i + 1).append(":* ").append(s).append("\n");
        }
        if (!result.isEmpty()) {
            result.deleteCharAt(result.length() - 1);
        }
        return event.getHook().sendMessageEmbeds(embed(event)
            .setTitle("Top Playtime (30 days)")
            .setColor(Color.CYAN)
            .setDescription(result.toString())
            .build());
    }
}
