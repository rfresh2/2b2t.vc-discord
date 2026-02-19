package vc.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.springframework.stereotype.Component;
import vc.openapi.handler.DeathsApi;
import vc.openapi.model.PlayerDeathOrKillCountResponse;

import static vc.util.DiscordMarkdownEscape.escape;

@Component
public class DeathsTopMonthCommand implements SlashCommand {
    private final DeathsApi deathsApi;

    public DeathsTopMonthCommand(final DeathsApi deathsApi) {
        this.deathsApi = deathsApi;
    }

    @Override
    public String getName() {
        return "deathsmonth";
    }

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        PlayerDeathOrKillCountResponse response;
        try {
            response = deathsApi.deathsTopMonth();
        } catch (Exception e) {
            return error(event, "Unable to resolve deaths top data");
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0, deathListSize = Math.min(50, response.getPlayers().size()); i < deathListSize; i++) {
            var player = response.getPlayers().get(i);
            String s = "**" + escape(player.getPlayerName()) + "**: " + player.getCount();
            if (result.length() + s.length() > 4090) {
                break;
            }
            result.append("*#").append(i + 1).append(":* ").append(s).append("\n");
        }
        if (!result.isEmpty()) {
            result.deleteCharAt(result.length() - 1);
        }
        return event.getHook().sendMessageEmbeds(embed(event)
            .setTitle("Top Deaths Count (30 days)")
            .setDescription(result.toString())
            .setColor(Color.CYAN)
            .build());
    }
}
