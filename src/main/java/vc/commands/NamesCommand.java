package vc.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import vc.api.LabyRestClient;
import vc.api.model.LabyProfileSearch;
import vc.openapi.handler.ApiException;
import vc.util.DiscordMarkdownEscape;
import vc.util.Validator;

import static org.slf4j.LoggerFactory.getLogger;

@Component
public class NamesCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(NamesCommand.class);
    private final LabyRestClient labyRestClient;

    public NamesCommand(final LabyRestClient labyRestClient) {
        this.labyRestClient = labyRestClient;
    }

    @Override
    public String getName() {
        return "names";
    }

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        var player = event.getOption("player", OptionMapping::getAsString);
        if (player == null) return error(event, "`player` option is required");
        if (!Validator.isValidPlayerName(player)) return error(event, "Invalid player name");

        LabyProfileSearch search;
        try {
            search = labyRestClient.searchProfiles(player);
        } catch (Exception e) {
            if (e instanceof ApiException apiException) {
                LOGGER.error("Timed out searching for names: {}", player);
                return error(event, "Timed out searching. Try again in a minute");
            }
            LOGGER.error("Error searching for names: {}", player, e);
            return error(event, "Error searching. Try again later. Or run the search directly here: https://laby.net/");
        }

        var builder = embed(event)
            .setTitle("Name Search")
            .addField("Searched Name", DiscordMarkdownEscape.escape(player), true)
            .addField("\u200B", "\u200B", true)
            .addField("\u200B", "\u200B", true)
            .addField("Source", "[LabyMod API](https://laby.net/@" + player + ")", true)
            .setColor(Color.CYAN);

        var sb = new StringBuilder();
        var currentProfile = search.currentProfile();
        sb.append("**Current Profile**\n\n");
        if (currentProfile != null) {
            sb.append(currentProfile.toDiscordFieldValue()).append("\n");
            builder.setThumbnail(currentProfile.getAvatarURL());
        } else {
            sb.append("(none)\n");
        }

        var prevUsernames = search.previousUsernames();
        sb.append("\n**Previous Usernames**\n\n");
        if (!prevUsernames.isEmpty()) {
            for (var name : prevUsernames) {
                sb.append(DiscordMarkdownEscape.escape(name)).append("\n");
            }
        } else {
            sb.append("(none)\n");
        }

        var historicalProfiles = search.historicalProfiles();
        sb.append("\n**Previous Accounts**\n\n");
        if (!historicalProfiles.isEmpty()) {
            for (var historicalProfile : historicalProfiles) {
                sb.append(historicalProfile.toDiscordFieldValue()).append("\n");
            }
        } else {
            sb.append("(none)\n");
        }

        var names = search.associatedUsernames();
        sb.append("\n**Previous Account Usernames**\n\n");
        if (!names.isEmpty()) {
            for (var name : names) {
                sb.append(DiscordMarkdownEscape.escape(name)).append("\n");
            }
        } else {
            sb.append("(none)\n");
        }

        builder.setDescription(sb.toString());
        return event.getHook().sendMessageEmbeds(builder.build());
    }
}
