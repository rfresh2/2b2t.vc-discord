package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.swagger.mojang_api.model.ProfileLookup;
import vc.swagger.vc.handler.ChatsApi;
import vc.swagger.vc.model.Chats;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.isNull;

@Component
public class ChatsCommand implements SlashCommand {
    private final ChatsApi chatsApi = new ChatsApi();
    private final PlayerLookup playerLookup;

    public ChatsCommand(final PlayerLookup playerLookup) {
        this.playerLookup = playerLookup;
    }

    @Override
    public String getName() {
        return "chats";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Optional<String> playerNameOptional = event.getOption("username")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        return playerNameOptional
                .filter(Validator::isValidUsername)
                .flatMap(playerLookup::getPlayerProfile)
                .map(profile -> resolveChats(event, profile))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolveChats(final ChatInputInteractionEvent event, final ProfileLookup profile) {
        List<Chats> chats = chatsApi.chats(playerLookup.getProfileUUID(profile), 0);
        if (isNull(chats) || chats.isEmpty()) return error(event, "No chats found");
        List<String> chatStrings = chats.stream()
                .map(c -> "<t:" + c.getTime().toEpochSecond() + ":f>: " + escape(c.getChat()))
                .toList();
        StringBuilder result = new StringBuilder();
        for (String s : chatStrings) {
            if (result.length() + s.length() > 4090) {
                break;
            }
            result.append(s).append("\n");
        }
        if (result.length() > 0) {
            result = new StringBuilder(result.substring(0, result.length() - 1));
        } else {
            return event.createFollowup()
                    .withEmbeds(EmbedCreateSpec.builder()
                            .title("Chats: " + escape(profile.getName()))
                            .color(Color.CYAN)
                            .description("No chats found")
                            .thumbnail(playerLookup.getAvatarURL(profile.getId()).toString())
                            .build());
        }
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("Chats: " + escape(profile.getName()))
                        .color(Color.CYAN)
                        .description(result.toString())
                        .thumbnail(playerLookup.getAvatarURL(profile.getId()).toString())
                        .build());
    }
}
