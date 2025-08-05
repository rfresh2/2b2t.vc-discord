package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.openapi.handler.PlaytimeApi;
import vc.openapi.model.PlaytimeMonthResponse;

import java.text.DecimalFormat;

import static org.slf4j.LoggerFactory.getLogger;
import static vc.util.DiscordMarkdownEscape.escape;

@Component
public class PlaytimeTopMonthCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(PlaytimeTopMonthCommand.class);
    private final PlaytimeApi playtimeApi;
    private static final DecimalFormat df = new DecimalFormat("0.00");

    public PlaytimeTopMonthCommand(final PlaytimeApi playtimeApi) {
        this.playtimeApi = playtimeApi;
    }

    @Override
    public String getName() {
        return "playtimemonth";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        return Mono.defer(() -> {
            PlaytimeMonthResponse response = null;
            try {
                response = playtimeApi.playtimeTopMonth();
            } catch (final Exception e) {
                LOGGER.error("Failed to get playtime top month", e);
            }
            if (response == null || response.getPlayers() == null || response.getPlayers().isEmpty())
                return error(event, "Unable to resolve playtime list");
            StringBuilder result = new StringBuilder();
            for (int i = 0, ptListSize = Math.min(50, response.getPlayers().size()); i < ptListSize; i++) {
                var player = response.getPlayers().get(i);
                var s = "**" + escape(player.getPlayerName()) + "**: " + df.format(player.getPlaytimeDays()) + "d";
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
                    .title("Top Playtime (30 days)")
                    .color(Color.CYAN)
                    .description(result.toString())
                    .build());
        });
    }
}
