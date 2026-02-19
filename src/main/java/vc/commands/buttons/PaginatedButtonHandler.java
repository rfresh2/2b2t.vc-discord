package vc.commands.buttons;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vc.api.model.ProfileData;
import vc.util.PlayerLookup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaginatedButtonHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger(PaginatedButtonHandler.class);
    public static final String ID_PREFIX_DELIMITER = ":";

    public WebhookMessageCreateAction<Message> defaultButtonHandler(
        ButtonInteractionEvent event,
        ObjectMapper objectMapper,
        String commandName,
        PlayerLookup playerLookup,
        CommandResolver resolver,
        ErrorResolver errorResolver
    ) {
        var args = decodeButtonId(objectMapper, commandName, event.getComponentId());
        Optional<ProfileData> playerIdentityOptional = playerLookup.getPlayerIdentity(args.playerName());
        if (playerIdentityOptional.isEmpty()) {
            return errorResolver.error(event.getHook(), "Unable to find player");
        }
        ProfileData identity = playerIdentityOptional.get();
        return resolver.resolve(event.getHook(), identity, args.page(), args.startDate(), args.endDate());
    }

    @FunctionalInterface
    public interface CommandResolver {
        WebhookMessageCreateAction<Message> resolve(InteractionHook event, ProfileData identity, int page, LocalDate startDate, LocalDate endDate);
    }

    @FunctionalInterface
    public interface ErrorResolver {
        WebhookMessageCreateAction<Message> error(InteractionHook event, String message);
    }

    public List<Button> getButtons(ObjectMapper objectMapper, String commandName, int totalPageCount, int page, ProfileData identity, LocalDate startDate, LocalDate endDate) {
        return getButtons(objectMapper, commandName, totalPageCount, page, identity, null, startDate, endDate);
    }

    public List<Button> getButtons(ObjectMapper objectMapper, String commandName, int totalPageCount, int page, ProfileData identity, String word, LocalDate startDate, LocalDate endDate) {
        List<Button> buttons = new ArrayList<>();
        String playerName = identity != null ? identity.name() : null;
        if (page > 1) {
            String padding = null;
            if (1 == page - 1) {
                padding = "0";
            }
            var firstPageArgs = new PaginatedCommandArgs(playerName, word, 1, startDate, endDate, padding);
            addButtonSafe(encodeButtonId(objectMapper, commandName, firstPageArgs), "⏮", buttons);
            var prevPageArgs = new PaginatedCommandArgs(playerName, word, page - 1, startDate, endDate, null);
            addButtonSafe(encodeButtonId(objectMapper, commandName, prevPageArgs), "◀", buttons);
        } else {
            addDisabledButton("⏮", buttons);
            addDisabledButton("◀", buttons);
        }
        if (page < totalPageCount) {
            String padding = null;
            if (page + 1 >= totalPageCount) {
                padding = "0";
            }
            var nextPageArgs = new PaginatedCommandArgs(playerName, word, page + 1, startDate, endDate, padding);
            addButtonSafe(encodeButtonId(objectMapper, commandName, nextPageArgs), "▶", buttons);
            var lastPageArgs = new PaginatedCommandArgs(playerName, word, totalPageCount, startDate, endDate, null);
            addButtonSafe(encodeButtonId(objectMapper, commandName, lastPageArgs), "⏭", buttons);
        } else {
            addDisabledButton("▶", buttons);
            addDisabledButton("⏭", buttons);
        }
        return buttons;
    }

    public List<ActionRow> getButtonRow(ObjectMapper objectMapper, String commandName, int totalPageCount, int page, ProfileData identity, LocalDate startDate, LocalDate endDate) {
        return getButtonRow(objectMapper, commandName, totalPageCount, page, identity, null, startDate, endDate);
    }

    public List<ActionRow> getButtonRow(ObjectMapper objectMapper, String commandName, int totalPageCount, int page, ProfileData identity, String word, LocalDate startDate, LocalDate endDate) {
        return List.of(ActionRow.of(getButtons(objectMapper, commandName, totalPageCount, page, identity, word, startDate, endDate)));
    }

    public void addButtonSafe(String encodedId, String emoji, List<Button> out) {
        if (encodedId.length() > 100) {
            LOGGER.warn("Button ID too long: {}", encodedId);
            return;
        }
        out.add(Button.of(ButtonStyle.SECONDARY, encodedId, " ").withEmoji(Emoji.fromUnicode(emoji)));
    }

    public void addDisabledButton(String emoji, List<Button> out) {
        out.add(Button.of(ButtonStyle.SECONDARY, UUID.randomUUID().toString(), " ").withEmoji(Emoji.fromUnicode(emoji)).asDisabled());
    }

    public String encodeButtonId(ObjectMapper objectMapper, String commandName, PaginatedCommandArgs args) {
        try {
            return commandName + ID_PREFIX_DELIMITER + objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public PaginatedCommandArgs decodeButtonId(ObjectMapper objectMapper, String commandName, String id) {
        try {
            var json = id.substring(commandName.length() + ID_PREFIX_DELIMITER.length());
            return objectMapper.readValue(json, PaginatedCommandArgs.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
