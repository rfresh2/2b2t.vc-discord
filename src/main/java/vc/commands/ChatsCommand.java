package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.openapi.vc.handler.ChatsApi;
import vc.openapi.vc.model.ChatsResponse;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.List;
import java.util.Optional;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class ChatsCommand extends PlayerLookupCommand {
    private static final Logger LOGGER = getLogger(ChatsCommand.class);
    private final ChatsApi chatsApi;
    public ChatsCommand(final ChatsApi chatsApi, final PlayerLookup playerLookup) {
        super(playerLookup);
        this.chatsApi = chatsApi;
    }

    @Override
    public String getName() {
        return "chats";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Optional<String> playerNameOptional = event.getOption("playername")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        final int page = event.getOption("page")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asLong)
                .map(Long::intValue)
                .orElse(1);
        if (page <= 0)
            return error(event, "Page must be greater than 0");
        return playerNameOptional
                .filter(Validator::isValidPlayerName)
                .flatMap(playerLookup::getPlayerIdentity)
                .map(identity -> resolveChats(event, identity, page))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolveChats(final ChatInputInteractionEvent event, final ProfileData identity, int page) {
        ChatsResponse chatsResponse = chatsApi.chats(identity.uuid(), null, 25, page);
        if (chatsResponse == null || chatsResponse.getChats() == null || chatsResponse.getChats().isEmpty())
            return error(event, "No chats found");
        List<String> chatStrings = chatsResponse.getChats().stream()
                .map(c -> SHORT_DATE_TIME.format(c.getTime().toInstant()) + " " + escape(c.getChat()))
                .toList();
        StringBuilder result = new StringBuilder();
        for (String s : chatStrings) {
            if (result.length() + s.length() > 4090) {
                LOGGER.warn("Chat message too long, truncating: {}", s);
                break;
            }
            result.append(s).append("\n");
        }
        if (!result.isEmpty()) {
            result = new StringBuilder(result.substring(0, result.length() - 1));
        } else {
            return event.createFollowup()
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                                .title("Chats")
                                .color(Color.CYAN)
                                .description("No chats found")
                                .thumbnail(playerLookup.getAvatarURL(identity.uuid()).toString())
                                .build());
        }
        return event.createFollowup()
            .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                            .title("Chats")
                            .color(Color.CYAN)
                            .description(result.toString())
                            .addField("Total", ""+chatsResponse.getTotal(), true)
                            .addField("Current Page", ""+page, true)
                            .addField("Total Pages", ""+chatsResponse.getPageCount(), true)
                            .thumbnail(playerLookup.getAvatarURL(identity.uuid()).toString())
                            .build());
    }
}
