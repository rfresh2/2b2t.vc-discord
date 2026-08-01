package vc.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import vc.api.model.ProfileData;
import vc.commands.buttons.ButtonCommand;
import vc.commands.buttons.PaginatedButtonHandler;
import vc.commands.options.ChatInteractionOptionResolver;
import vc.commands.options.PaginatedOption;
import vc.commands.options.PlayerLookupOption;
import vc.commands.options.TimeRangeOption;
import vc.openapi.handler.ApiException;
import vc.openapi.handler.DeathsApi;
import vc.openapi.model.KillsResponse;
import vc.util.PlayerLookup;

import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.slf4j.LoggerFactory.getLogger;
import static vc.discord.DiscordTimestampFormat.SHORT_DATE_TIME;
import static vc.util.DiscordMarkdownEscape.escape;

@Component
public class KillsCommand implements SlashCommand, ButtonCommand {
    private static final Logger LOGGER = getLogger(KillsCommand.class);
    private final DeathsApi deathsApi;
    private final ObjectMapper objectMapper;
    private final ChatInteractionOptionResolver resolver;
    private final PlayerLookup playerLookup;
    private final PaginatedButtonHandler buttonHandler;

    public KillsCommand(final DeathsApi deathsApi, final PlayerLookup playerLookup, final ObjectMapper objectMapper, final PaginatedButtonHandler buttonHandler) {
        this.deathsApi = deathsApi;
        this.playerLookup = playerLookup;
        this.objectMapper = objectMapper;
        this.resolver = new ChatInteractionOptionResolver()
            .registerOption(new PaginatedOption())
            .registerOption(new PlayerLookupOption(playerLookup))
            .registerOption(new TimeRangeOption());
        this.buttonHandler = buttonHandler;
    }

    @Override
    public String getName() {
        return "kills";
    }

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        var ctx = resolver.resolveOptions(event);
        if (ctx.isErrorSet()) return error(event, ctx.getErrorMessage());
        return resolveKills(event.getHook(), ctx.profileData, ctx.page, ctx.startDate, ctx.endDate);
    }

    private WebhookMessageCreateAction<Message> resolveKills(final InteractionHook hook, final ProfileData identity, int page, LocalDate startDate, LocalDate endDate) {
        KillsResponse killsResponse = null;
        try {
            killsResponse = deathsApi.kills(identity.uuid(), null, startDate, endDate, "desc", 25, page);
        } catch (final Exception e) {
            if (e instanceof ApiException apiException) {
                if (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204) {
                    return hook.sendMessageEmbeds(populateIdentity(embed(hook), identity)
                        .setColor(Color.RUBY)
                        .setDescription("No kills found")
                        .setThumbnail(identity.getAvatarURL())
                        .build());
                } else if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                    LOGGER.error("Timeout searching for kills: {}", identity.uuid(), httpTimeoutException);
                    return error(hook, "Timeout searching for kills. Try again in a minute");
                }
            }
            LOGGER.error("Error resolving kills", e);
            throw new RuntimeException(e);
        }
        if (killsResponse == null || killsResponse.getKills() == null || killsResponse.getKills().isEmpty()) {
            return hook.sendMessageEmbeds(populateIdentity(embed(hook), identity)
                .setColor(Color.RUBY)
                .setDescription("No kills found")
                .setThumbnail(identity.getAvatarURL())
                .build());
        }

        final StringBuilder result = new StringBuilder();
        final AtomicBoolean truncated = new AtomicBoolean(false);
        killsResponse.getKills().stream()
            .map(k -> SHORT_DATE_TIME.format(k.getTime().toInstant()) + " " + escape(k.getDeathMessage()))
            .forEachOrdered(s -> {
                if (result.length() + s.length() + 1 > 4090) {
                    truncated.set(true);
                    return;
                }
                result.append(s).append("\n");
            });
        if (!result.isEmpty()) result.deleteCharAt(result.length() - 1);
        if (truncated.get()) LOGGER.warn("Truncated kills response");
        return hook.sendMessageEmbeds(populateIdentity(embed(hook), identity)
                .setColor(Color.CYAN)
                .setDescription(result.toString())
                .addField("Total", String.valueOf(killsResponse.getTotal()), true)
                .addField("Page", page + " / " + killsResponse.getPageCount(), true)
                .addField("\u200B", "\u200B", true)
                .setThumbnail(identity.getAvatarURL())
                .build())
            .setComponents(buttonHandler.getButtonRow(objectMapper, getName(), killsResponse.getPageCount(), page, identity, startDate, endDate));
    }

    @Override
    public WebhookMessageCreateAction<Message> handleButton(final ButtonInteractionEvent event) {
        return buttonHandler.defaultButtonHandler(event, objectMapper, getName(), playerLookup, this::resolveKills, this::error);
    }

    @Override
    public WebhookMessageCreateAction<Message> handleModal(final ModalInteractionEvent event) {
        return buttonHandler.defaultModalHandler(event, objectMapper, getName(), playerLookup, this::resolveKills, this::error);
    }
}
