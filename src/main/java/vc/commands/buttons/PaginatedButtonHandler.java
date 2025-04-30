package vc.commands.buttons;

import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.emoji.Emoji;
import discord4j.core.object.entity.Message;
import discord4j.discordjson.possible.Possible;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
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

    public Mono<Message> defaultButtonHandler(ButtonInteractionEvent event, ObjectMapper objectMapper, String commandName, PlayerLookup playerLookup, CommandResolver resolver, ErrorResolver errorResolver) {
        var args = decodeButtonId(objectMapper, commandName, event.getCustomId());
        return Mono.defer(() -> {
            Optional<ProfileData> playerIdentityOptional = playerLookup.getPlayerIdentity(args.playerName());
            if (playerIdentityOptional.isEmpty()) {
                return errorResolver.error(event, "Unable to find player");
            }
            ProfileData identity = playerIdentityOptional.get();
            return resolver.resolve(event, identity, args.page(), args.startDate(), args.endDate());
        });
    }

    @FunctionalInterface
    public interface CommandResolver {
        Mono<Message> resolve(DeferrableInteractionEvent event, ProfileData identity, int page, LocalDate startDate, LocalDate endDate);
    }

    @FunctionalInterface
    public interface ErrorResolver {
        Mono<Message> error(DeferrableInteractionEvent event, String message);
    }

    public Possible<List<Button>> getButtons(ObjectMapper objectMapper, String commandName, int totalPageCount, int page, ProfileData identity, LocalDate startDate, LocalDate endDate) {
        List<Button> buttons = new ArrayList<>();
        if (page > 1) {
            String padding = null;
            if (1 == page - 1) {
                padding = "0";
            }
            var firstPageArgs = new PaginatedCommandArgs(identity.name(), 1, startDate, endDate, padding);
            addButtonSafe(encodeButtonId(objectMapper, commandName, firstPageArgs), Emoji.unicode("⏮"), buttons);
            var prevPageArgs = new PaginatedCommandArgs(identity.name(), page - 1, startDate, endDate, null);
            addButtonSafe(encodeButtonId(objectMapper, commandName, prevPageArgs), Emoji.unicode("◀"), buttons);
        } else {
            addDisabledButton(Emoji.unicode("⏮"), buttons);
            addDisabledButton(Emoji.unicode("◀"), buttons);
        }
        if (page < totalPageCount) {
            String padding = null;
            if (page + 1 >= totalPageCount) {
                padding = "0";
            }
            var nextPageArgs = new PaginatedCommandArgs(identity.name(), page + 1, startDate, endDate, padding);
            addButtonSafe(encodeButtonId(objectMapper, commandName, nextPageArgs), Emoji.unicode("▶"), buttons);
            var lastPageArgs = new PaginatedCommandArgs(identity.name(), totalPageCount, startDate, endDate, null);
            addButtonSafe(encodeButtonId(objectMapper, commandName, lastPageArgs), Emoji.unicode("⏭"), buttons);
        } else {
            addDisabledButton(Emoji.unicode("▶"), buttons);
            addDisabledButton(Emoji.unicode("⏭"), buttons);
        }
        return buttons.isEmpty() ? Possible.absent() : Possible.of(buttons);
    }

    public Possible<List<ActionRow>> getButtonRow(ObjectMapper objectMapper, String commandName, int totalPageCount, int page, ProfileData identity, LocalDate startDate, LocalDate endDate) {
        return getButtons(objectMapper, commandName, totalPageCount, page, identity, startDate, endDate)
            .map(buttons -> List.of(ActionRow.of(buttons)));
    }

    public void addButtonSafe(String encodedId, Emoji emoji, List<Button> out) {
        if (encodedId.length() > 100) {
            LOGGER.warn("Button ID too long: {}", encodedId);
            return;
        }
        out.add(Button.secondary(encodedId, emoji));
    }

    public void addDisabledButton(Emoji emoji, List<Button> out) {
        out.add(Button.secondary(UUID.randomUUID().toString(), emoji).disabled());
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
