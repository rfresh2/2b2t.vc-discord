package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.swagger.vc.handler.PlaytimeApi;
import vc.swagger.vc.model.PlaytimeTopMonthResponse;

import java.text.DecimalFormat;
import java.util.List;

import static java.util.Objects.isNull;

@Component
public class PlaytimeTopMonthCommand implements SlashCommand {

    private final PlaytimeApi playtimeApi = new PlaytimeApi();
    private static final DecimalFormat df = new DecimalFormat("0.00");

    @Override
    public String getName() {
        return "playtimemonth";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        List<PlaytimeTopMonthResponse> playtimeTopMonthResponses = playtimeApi.playtimeTopMonth();
        if (isNull(playtimeTopMonthResponses) || playtimeTopMonthResponses.isEmpty())
            return error(event, "Unable to resolve playtime list");
        List<String> ptList = playtimeTopMonthResponses.stream()
                .map(pt -> "**" + escape(pt.getPlayerName()) + "**: " + df.format(pt.getPlaytimeDays()) + "d")
                .toList();
        StringBuilder result = new StringBuilder();
        for (int i = 0, ptListSize = Math.min(50, ptList.size()); i < ptListSize; i++) {
            final String s = ptList.get(i);
            if (result.length() + s.length() > 4090) {
                break;
            }
            result.append("*#" + (i+1) + ":* ").append(s).append("\n");
        }
        String resultString = result.toString();
        if (resultString.length() > 0) {
            resultString = resultString.substring(0, resultString.length() - 1);
        } else {
            return error(event, "No playtime data found");
        }
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("Top Playtime (30 days)")
                        .color(Color.CYAN)
                        .description(resultString)
                        .build());
    }
}
