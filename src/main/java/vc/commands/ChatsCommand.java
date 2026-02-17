package vc.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.Color;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import vc.api.model.ProfileData;
import vc.commands.buttons.ButtonCommand;
import vc.commands.buttons.PaginatedButtonHandler;
import vc.commands.options.*;
import vc.openapi.handler.ApiException;
import vc.openapi.handler.ChatsApi;
import vc.openapi.model.ChatSearchResponse;
import vc.util.PlayerLookup;

import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.slf4j.LoggerFactory.getLogger;
import static vc.discord.DiscordTimestampFormat.SHORT_DATE_TIME;
import static vc.util.DiscordMarkdownEscape.escape;

@Component
public class ChatsCommand implements SlashCommand, ButtonCommand {
    private static final Logger LOGGER = getLogger(ChatsCommand.class);
    private final ChatsApi chatsApi;
    private final ObjectMapper objectMapper;
    private final ChatInteractionOptionResolver resolver;
    private final PlayerLookup playerLookup;
    private final PaginatedButtonHandler buttonHandler;

    public ChatsCommand(final ChatsApi chatsApi, final PlayerLookup playerLookup, final ObjectMapper objectMapper, final PaginatedButtonHandler buttonHandler) {
        this.chatsApi = chatsApi;
        this.objectMapper = objectMapper;
        this.playerLookup = playerLookup;
        this.resolver = new ChatInteractionOptionResolver()
            .registerOption(new PaginatedOption())
            .registerOption(new PlayerLookupOption(playerLookup, true))
            .registerOption(new WordOption())
            .registerOption(new TimeRangeOption());
        this.buttonHandler = buttonHandler;
    }

    @Override
    public String getName() {
        return "chats";
    }

    @Override
    public WebhookMessageCreateAction<Message> handle(final SlashCommandInteractionEvent event) {
        ChatInteractionOptionContext ctx = resolver.resolveOptions(event);
        if (ctx.isErrorSet()) return error(event, ctx.getErrorMessage());
        if (ctx.profileData == null && ctx.word == null) {
            return error(event, "Player or word must be provided");
        }
        return resolveChats(event.getHook(), ctx.profileData, ctx.word, ctx.page, ctx.startDate, ctx.endDate);
    }

    private WebhookMessageCreateAction<Message> resolveChats(final InteractionHook hook, ProfileData identity, String word, int page, LocalDate startDate, LocalDate endDate) {
        String searchTerm = "word: " + (word != null ? word : "(?)") + " player:" + (identity != null ? identity.uuid() : "(?)");
        UUID uuid = identity != null ? identity.uuid() : null;
        ChatSearchResponse chatsResponse = null;
        try {
            chatsResponse = chatsApi.chats(word, null, uuid, startDate, endDate, null, 25, page);
        } catch (final Exception e) {
            if (e instanceof ApiException apiException) {
                if (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204) {
                    return hook.sendMessageEmbeds(populateSearchTerm(embed(hook), identity, word)
                        .setColor(Color.RUBY)
                        .setDescription("No chats found")
                        .build());
                } else if (apiException.getCause() instanceof HttpTimeoutException httpTimeoutException) {
                    LOGGER.error("Timeout searching for chats: {}", searchTerm, httpTimeoutException);
                    return error(hook, "Timeout searching for chats. Try again in a minute");
                }
            }
            LOGGER.error("Error searching for chats: {}", searchTerm, e);
            throw new RuntimeException(e);
        }
        if (chatsResponse == null || chatsResponse.getChats() == null || chatsResponse.getChats().isEmpty()) {
            return hook.sendMessageEmbeds(populateSearchTerm(embed(hook), identity, word)
                .setColor(Color.RUBY)
                .setDescription("No chats found")
                .build());
        }

        final StringBuilder result = new StringBuilder();
        final AtomicBoolean truncated = new AtomicBoolean(false);
        chatsResponse.getChats().stream()
            .map(c -> SHORT_DATE_TIME.format(c.getTime().toInstant())
                + " "
                + (identity == null ? "**" + escape(c.getPlayerName()) + "**: " : "")
                + escape(c.getChat()))
            .forEachOrdered(s -> {
                if (result.length() + s.length() + 1 > 4090) {
                    truncated.set(true);
                    return;
                }
                result.append(s).append("\n");
            });
        if (!result.isEmpty()) result.deleteCharAt(result.length() - 1);
        if (truncated.get()) LOGGER.warn("Truncated chat response");
        return hook.sendMessageEmbeds(populateSearchTerm(embed(hook), identity, word)
                .setColor(Color.CYAN)
                .setDescription(result.toString())
                .addField("Total", String.valueOf(chatsResponse.getTotal()), true)
                .addField("Page", page + " / " + chatsResponse.getPageCount(), true)
                .addField("\u200B", "\u200B", true)
                .build())
            .setComponents(buttonHandler.getButtonRow(objectMapper, getName(), chatsResponse.getPageCount(), page, identity, word, startDate, endDate));
    }

    @Override
    public WebhookMessageCreateAction<Message> handleButton(final ButtonInteractionEvent event) {
        return buttonHandler(event, objectMapper, getName(), playerLookup, this::resolveChats, this::error);
    }

    @FunctionalInterface
    public interface ChatsCommandResolver {
        WebhookMessageCreateAction<Message> resolve(InteractionHook hook, ProfileData identity, String word, int page, LocalDate startDate, LocalDate endDate);
    }

    public WebhookMessageCreateAction<Message> buttonHandler(
        ButtonInteractionEvent event,
        ObjectMapper objectMapper,
        String commandName,
        PlayerLookup playerLookup,
        ChatsCommandResolver resolver,
        PaginatedButtonHandler.ErrorResolver errorResolver
    ) {
        var args = buttonHandler.decodeButtonId(objectMapper, commandName, event.getComponentId());
        ProfileData identity = null;
        if (args.playerName() != null) {
            Optional<ProfileData> playerIdentityOptional = playerLookup.getPlayerIdentity(args.playerName());
            if (playerIdentityOptional.isEmpty()) {
                return errorResolver.error(event.getHook(), "Unable to find player");
            }
            identity = playerIdentityOptional.get();
        }
        return resolver.resolve(event.getHook(), identity, args.word(), args.page(), args.startDate(), args.endDate());
    }

    EmbedBuilder populateSearchTerm(final EmbedBuilder builder, @Nullable ProfileData identity, @Nullable String word) {
        if (identity != null) {
            populateIdentity(builder, identity)
                .setThumbnail(identity.getAvatarURL());
        }
        if (word != null) {
            builder
                .addField("Word", escape(word), true)
                .addField("\u200B", "\u200B", true)
                .addField("\u200B", "\u200B", true);
        }
        return builder;
    }
}
