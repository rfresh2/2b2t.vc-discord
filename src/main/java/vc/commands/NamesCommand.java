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
import vc.swagger.vc.handler.NamesApi;
import vc.swagger.vc.model.Names;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.isNull;

@Component
public class NamesCommand implements SlashCommand {
    private final NamesApi namesApi = new NamesApi();
    private final PlayerLookup playerLookup;

    public NamesCommand(final PlayerLookup playerLookup) {
        this.playerLookup = playerLookup;
    }

    @Override
    public String getName() {
        return "names";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Optional<String> playerNameOptional = event.getOption("username")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        return playerNameOptional
                .filter(Validator::isValidUsername)
                .flatMap(playerLookup::getPlayerProfile)
                .map(profile -> resolveNames(event, profile))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolveNames(final ChatInputInteractionEvent event, final ProfileLookup profile) {
        List<Names> names = namesApi.names(playerLookup.getProfileUUID(profile));
        if (isNull(names) || names.isEmpty()) return error(event, "No name history found for player");
        List<String> namesStrings = names.stream()
                .map(n -> "**" + escape(n.getName()) + "**"
                        + Optional.ofNullable(n.getChangedtoat())
                            .map(at -> " (To: <t:" + at.toEpochSecond() + ":f>)")
                            .orElse("")
                        + Optional.ofNullable(n.getChangedfromat())
                            .map(at -> " (From: <t:" + at.toEpochSecond() + ":f>)")
                            .orElse(""))
                .toList();
        StringBuilder result = new StringBuilder();
        for (String s : namesStrings) {
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
                            .title("Names: " + escape(profile.getName()))
                            .color(Color.CYAN)
                            .description("No names found")
                            .thumbnail(playerLookup.getAvatarURL(profile.getId()).toString())
                            .build());
        }
        return event.createFollowup()
                .withEmbeds(EmbedCreateSpec.builder()
                        .title("Names: " + escape(profile.getName()))
                        .color(Color.CYAN)
                        .description(result.toString())
                        .thumbnail(playerLookup.getAvatarURL(profile.getId()).toString())
                        .build());
    }
}
