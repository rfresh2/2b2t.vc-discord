package vc.commands;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import vc.api.model.ProfileData;
import vc.api.vc.VcDataDumpApi;
import vc.commands.options.ChatInteractionOptionResolver;
import vc.commands.options.PlayerLookupOption;
import vc.openapi.handler.ApiException;
import vc.util.PlayerLookup;

import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.slf4j.LoggerFactory.getLogger;

@Component
public class DataCommand implements SlashCommand {
    private static final Logger LOGGER = getLogger(DataCommand.class);
    private final VcDataDumpApi vcDataDumpApi;
    private final ChatInteractionOptionResolver resolver;

    public DataCommand(final PlayerLookup playerLookup, final VcDataDumpApi vcDataDumpApi) {
        this.vcDataDumpApi = vcDataDumpApi;
        this.resolver = new ChatInteractionOptionResolver()
            .registerOption(new PlayerLookupOption(playerLookup));
    }

    @Override
    public String getName() {
        return "data";
    }

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        var ctx = this.resolver.resolveOptions(event);
        if (ctx.isErrorSet()) return error(event, ctx.getErrorMessage());
        return resolvePlayerDataDump(event, ctx.profileData);
    }

    public WebhookMessageCreateAction<Message> resolvePlayerDataDump(SlashCommandInteractionEvent event, ProfileData identity) {
        String playerDataDump = null;
        try {
            playerDataDump = vcDataDumpApi.getPlayerDataDump(identity.uuid(), null);
        } catch (final Exception e) {
            if (e instanceof ApiException apiException) {
                if (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204) {
                    return event.getHook().sendMessageEmbeds(populateIdentity(embed(event), identity)
                        .setColor(Color.RUBY)
                        .setDescription("No Data")
                        .setThumbnail(identity.getAvatarURL())
                        .build());
                } else if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                    LOGGER.error("Timeout searching for data dump: {}", identity.uuid(), httpTimeoutException);
                    return error(event, "Timeout searching for data. Try again in a minute");
                }
            } else {
                LOGGER.error("Failed to get player data dump", e);
                throw new RuntimeException(e);
            }
        }
        int dataCount = playerDataDump != null ? (int) playerDataDump.lines().count() : 0;
        if (playerDataDump == null || dataCount == 0) {
            return event.getHook().sendMessageEmbeds(populateIdentity(embed(event), identity)
                .setColor(Color.RUBY)
                .setDescription("No Data")
                .setThumbnail(identity.getAvatarURL())
                .build());
        }
        return event.getHook().sendFiles(FileUpload.fromData(playerDataDump.getBytes(StandardCharsets.UTF_8), identity.name() + ".csv"))
            .addEmbeds(populateIdentity(embed(event), identity)
                .addField("Data Count", String.valueOf(dataCount), true)
                .setDescription("CSV Generated!")
                .setColor(Color.CYAN)
                .setThumbnail(identity.getAvatarURL())
                .build());
    }
}
