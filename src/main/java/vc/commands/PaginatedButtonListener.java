package vc.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Message;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.discordjson.possible.Possible;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.util.PlayerLookup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// todo: I think we can codify this better by thinking more about the command inheritance structure
//  e.g. PaginatedCommand as a base class with standard behavior
//  Need to design how PlayerLookup traits are structured too as those need to also be on non-paginated commands
public interface PaginatedButtonListener {
    Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PaginatedButtonListener.class);
    String ID_PREFIX_DELIMITER = ":";

    Mono<Message> handleButton(ButtonInteractionEvent event);

    @FunctionalInterface
    interface CommandResolver {
        Mono<Message> resolve(DeferrableInteractionEvent event, ProfileData identity, int page, LocalDate startDate, LocalDate endDate);
    }

    interface ErrorResolver {
        Mono<Message> error(DeferrableInteractionEvent event, String message);
    }

    default Mono<Message> paginatedPlayerLookupButtonHandler(ButtonInteractionEvent event, ObjectMapper objectMapper, String commandName, PlayerLookup playerLookup, CommandResolver resolver, ErrorResolver errorResolver) {
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

    default Possible<List<ActionRow>> getButtonRow(ObjectMapper objectMapper, String commandName, int totalPageCount, int page, ProfileData identity, LocalDate startDate, LocalDate endDate) {
        return getButtons(objectMapper, commandName, totalPageCount, page, identity, startDate, endDate)
            .map(buttons -> List.of(ActionRow.of(buttons)));
    }

    default void addButtonSafe(String encodedId, ReactionEmoji emoji, List<Button> out) {
        if (encodedId.length() > 100) {
            LOGGER.warn("Button ID too long: {}", encodedId);
            return;
        }
        out.add(Button.secondary(encodedId, emoji));
    }

    default Possible<List<Button>> getButtons(ObjectMapper objectMapper, String commandName, int totalPageCount, int page, ProfileData identity, LocalDate startDate, LocalDate endDate) {
        List<Button> buttons = new ArrayList<>();
        if (page > 1) {
            var firstPageArgs = new PaginatedCommandArgs(identity.name(), 1, startDate, endDate);
            addButtonSafe(encodeButtonId(objectMapper, commandName, firstPageArgs), ReactionEmoji.unicode("⏮"), buttons);
            if (page - 1 > 1) {
                var prevPageArgs = new PaginatedCommandArgs(identity.name(), page - 1, startDate, endDate);
                addButtonSafe(encodeButtonId(objectMapper, commandName, prevPageArgs), ReactionEmoji.unicode("◀"), buttons);
            }
        }
        if (page < totalPageCount) {
            if (page + 1 < totalPageCount) {
                var nextPageArgs = new PaginatedCommandArgs(identity.name(), page + 1, startDate, endDate);
                addButtonSafe(encodeButtonId(objectMapper, commandName, nextPageArgs), ReactionEmoji.unicode("▶"), buttons);
            }
            var lastPageArgs = new PaginatedCommandArgs(identity.name(), totalPageCount, startDate, endDate);
            addButtonSafe(encodeButtonId(objectMapper, commandName, lastPageArgs), ReactionEmoji.unicode("⏭"), buttons);
        }
        return buttons.isEmpty() ? Possible.absent() : Possible.of(buttons);
    }

    default String encodeButtonId(ObjectMapper objectMapper, String commandName, PaginatedCommandArgs args) {
        try {
            return commandName + ID_PREFIX_DELIMITER + objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    default PaginatedCommandArgs decodeButtonId(ObjectMapper objectMapper, String commandName, String id) {
        try {
            var json = id.substring(commandName.length() + ID_PREFIX_DELIMITER.length());
            return objectMapper.readValue(json, PaginatedCommandArgs.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
