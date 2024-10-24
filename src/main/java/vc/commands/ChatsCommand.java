package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.openapi.handler.ChatsApi;
import vc.openapi.model.ChatsResponse;
import vc.util.PlayerLookup;

import java.time.LocalDate;
import java.util.List;

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
        return resolveData(event, this::resolveChats);
    }

    private Mono<Message> resolveChats(final ChatInputInteractionEvent event, final ProfileData identity, int page, LocalDate startDate, LocalDate endDate) {
        ChatsResponse chatsResponse = null;
        try {
            chatsResponse = chatsApi.chats(identity.uuid(), null, startDate, endDate, 25, page);
        } catch (final Exception e) {
            LOGGER.error("Error processing chats response", e);
        }
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
