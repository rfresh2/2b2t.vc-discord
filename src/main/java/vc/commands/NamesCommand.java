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
import vc.openapi.vc.handler.NamesApi;
import vc.openapi.vc.model.Names;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.util.List;
import java.util.Optional;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static java.util.Objects.isNull;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class NamesCommand extends PlayerLookupCommand {
    private static final Logger LOGGER = getLogger(NamesCommand.class);
    private final NamesApi namesApi;

    public NamesCommand(final NamesApi namesApi, final PlayerLookup playerLookup) {
        super(playerLookup);
        this.namesApi = namesApi;
    }

    @Override
    public String getName() {
        return "names";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        Optional<String> playerNameOptional = event.getOption("playername")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        return playerNameOptional
                .filter(Validator::isValidPlayerName)
                .flatMap(playerLookup::getPlayerIdentity)
                .map(identity -> resolveNames(event, identity))
                .orElse(error(event, "Unable to find player"));
    }

    private Mono<Message> resolveNames(final ChatInputInteractionEvent event, final ProfileData identity) {
        List<Names> names = null;
        try {
            names = namesApi.names(identity.uuid(), null);
        } catch (final Exception e) {
            LOGGER.error("Failed to get names for player: " + identity.uuid(), e);
        }
        if (isNull(names) || names.isEmpty()) return error(event, "No name history found for player");
        List<String> namesStrings = names.stream()
                .map(n -> "**" + escape(n.getName()) + "**"
                        + Optional.ofNullable(n.getChangedtoat())
                            .map(at -> " (To: " + SHORT_DATE_TIME.format(at.toInstant()) + ")")
                            .orElse("")
                        + Optional.ofNullable(n.getChangedfromat())
                            .map(at -> " (From: " + SHORT_DATE_TIME.format(at.toInstant()) + ")")
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
                    .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                            .title("Names")
                            .color(Color.CYAN)
                            .description("No names found")
                            .thumbnail(playerLookup.getAvatarURL(identity.uuid()).toString())
                            .build());
        }
        return event.createFollowup()
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                        .title("Names")
                        .color(Color.CYAN)
                        .description(result.toString())
                        .thumbnail(playerLookup.getAvatarURL(identity.uuid()).toString())
                        .build());
    }
}
