package vc.commands;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import vc.api.model.ProfileData;
import vc.commands.options.ChatInteractionOptionResolver;
import vc.commands.options.PlayerLookupOption;
import vc.openapi.handler.ApiException;
import vc.openapi.handler.PlaytimeApi;
import vc.openapi.model.PlaytimeResponse;
import vc.util.PlayerLookup;

import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.isNull;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class PlaytimeCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(PlaytimeCommand.class);
    private final PlaytimeApi playtimeApi;
    private final ChatInteractionOptionResolver resolver;

    public PlaytimeCommand(final PlaytimeApi playtimeApi, final PlayerLookup playerLookup) {
        this.playtimeApi = playtimeApi;
        this.resolver = new ChatInteractionOptionResolver()
            .registerOption(new PlayerLookupOption(playerLookup));
    }

    @Override
    public String getName() {
        return "playtime";
    }

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        var ctx = resolver.resolveOptions(event);
        if (ctx.isErrorSet()) return error(event, ctx.getErrorMessage());
        return resolvePlaytime(event, ctx.profileData);
    }

    private WebhookMessageCreateAction<Message> resolvePlaytime(SlashCommandInteractionEvent event, final ProfileData identity) {
        PlaytimeResponse playtime = null;
        try {
            playtime = playtimeApi.playtime(identity.uuid(), null);
        } catch (final Exception e) {
            if (e instanceof ApiException apiException) {
                if (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204) {
                    return event.getHook().sendMessageEmbeds(populateIdentity(embed(event), identity)
                        .setColor(Color.RUBY)
                        .setDescription("Never Played")
                        .setThumbnail(identity.getAvatarURL())
                        .build());
                } else if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                    LOGGER.error("Timeout searching for playtime: {}", identity.uuid(), httpTimeoutException);
                    return error(event, "Timeout getting playtime. Try again in a minute");
                }
            }
            LOGGER.error("Failed to get playtime for player: {}", identity.uuid(), e);
            throw new RuntimeException(e);
        }

        if (isNull(playtime)) {
            return event.getHook().sendMessageEmbeds(populateIdentity(embed(event), identity)
                .setColor(Color.RUBY)
                .setDescription("Never Played")
                .setThumbnail(identity.getAvatarURL())
                .build());
        }
        Integer playtimeSeconds = playtime.getPlaytimeSeconds();
        String durationStr = formatDuration(playtimeSeconds);
        return event.getHook().sendMessageEmbeds(populateIdentity(embed(event), identity)
            .setColor(Color.CYAN)
            .setDescription(durationStr)
            .setThumbnail(identity.getAvatarURL())
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
        var hours = (durationInSeconds % secondsInDay) / secondsInHour;
        List<String> entries = new ArrayList<>(4);
        if (years > 0) entries.add(years + " year" + (years != 1 ? "s" : ""));
        if (months > 0) entries.add(months + " month" + (months != 1 ? "s" : ""));
        if (days > 0) entries.add(days + " day" + (days != 1 ? "s" : ""));
        if (hours > 0) entries.add(hours + " hour" + (hours != 1 ? "s" : ""));
        if (entries.isEmpty()) {
            var minutes = (double) durationInSeconds / (double)secondsInMinute;
            if (minutes == 0.0) {
                return "0 minutes";
            }
            return String.format("%.2f minutes", minutes);
        }
        return String.join(", ", entries);
    }
}
