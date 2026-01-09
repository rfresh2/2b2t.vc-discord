package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.LabyRestClient;
import vc.api.model.LabyProfileSearch;
import vc.openapi.handler.ApiException;
import vc.util.DiscordMarkdownEscape;
import vc.util.Validator;

import java.net.http.HttpTimeoutException;

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
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        var player = event.getOptionAsString("player").orElse(null);
        if (player == null) {
            return error(event, "`player` option is required");
        }
        if (!Validator.isValidPlayerName(player)) {
            return error(event, "Invalid player name");
        }
        LabyProfileSearch search;
        try {
            search = labyRestClient.searchProfiles(player);
        } catch (Exception e) {
            if (e instanceof ApiException apiException) {
                if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                    LOGGER.error("Timed out searching for names: {}", player, httpTimeoutException);
                    return error(event, "Timed out searching. Try again in a minute");
                }
            }
            LOGGER.error("Error searching for names: {}", player, e);
            return error(event, "Error searching. Try again later");
        }
        var builder = embed(event)
            .title("Name Search")
            .addField("Searched Name", DiscordMarkdownEscape.escape(player), true)
            .addField("\u200B", "\u200B", true)
            .addField("\u200B", "\u200B", true)
            .addField("Source", "[LabyMod API](https://laby.net/@" + player + ")", true)
            .color(Color.CYAN);
        var sb = new StringBuilder();
        var currentProfile = search.currentProfile();
        sb.append("**Current Profile**\n\n");
        if (currentProfile != null) {
            sb.append(currentProfile.toDiscordFieldValue()).append("\n");
            builder.thumbnail(currentProfile.getAvatarURL());
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
        builder.description(sb.toString());
        return event.createFollowup().withEmbeds(builder.build());
    }
}
