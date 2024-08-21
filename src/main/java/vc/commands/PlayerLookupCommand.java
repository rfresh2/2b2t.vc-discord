package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.util.PlayerLookup;
import vc.util.TriFunction;
import vc.util.Validator;

import java.util.Optional;
import java.util.function.BiFunction;

public abstract class PlayerLookupCommand implements SlashCommand {
    protected final PlayerLookup playerLookup;

    public PlayerLookupCommand(final PlayerLookup playerLookup) {
        this.playerLookup = playerLookup;
    }

    EmbedCreateSpec.Builder populateIdentity(final EmbedCreateSpec.Builder builder, ProfileData identity) {
        return builder
            .addField("Player", "[" + identity.name() + "](" + playerLookup.getNameMCLink(identity.uuid()) + ")", true)
            .addField("\u200B", "\u200B", true)
            .addField("\u200B", "\u200B", true);
    }

    Mono<Message> resolveData(ChatInputInteractionEvent event, BiFunction<ChatInputInteractionEvent, ProfileData, Mono<Message>> resolveFunction) {
        Optional<String> playerNameOptional = event.getOption("player")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asString);
        if (playerNameOptional.isEmpty()) {
            return error(event, "No player name");
        }
        String playerName = playerNameOptional.get();
        if (!Validator.isValidPlayerName(playerName)) {
            return error(event, "Invalid player name");
        }
        return Mono.defer(() -> {
            Optional<ProfileData> playerIdentityOptional = playerLookup.getPlayerIdentity(playerName);
            if (playerIdentityOptional.isEmpty()) {
                return error(event, "Unable to find player");
            }
            ProfileData identity = playerIdentityOptional.get();
            return resolveFunction.apply(event, identity);
        });
    }

    Mono<Message> resolveData(ChatInputInteractionEvent event, TriFunction<ChatInputInteractionEvent, ProfileData, Integer, Mono<Message>> resolveFunction) {
        Optional<String> playerNameOptional = event.getOption("player")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asString);
        if (playerNameOptional.isEmpty()) {
            return error(event, "No player name");
        }
        String playerName = playerNameOptional.get();
        if (!Validator.isValidPlayerName(playerName)) {
            return error(event, "Invalid player name");
        }
        int page = event.getOption("page")
            .flatMap(ApplicationCommandInteractionOption::getValue)
            .map(ApplicationCommandInteractionOptionValue::asLong)
            .map(Long::intValue)
            .orElse(1);
        if (page <= 0)
            return error(event, "Page must be greater than 0");
        return Mono.defer(() -> {
            Optional<ProfileData> playerIdentityOptional = playerLookup.getPlayerIdentity(playerName);
            if (playerIdentityOptional.isEmpty()) {
                return error(event, "Unable to find player");
            }
            ProfileData identity = playerIdentityOptional.get();
            return resolveFunction.apply(event, identity, page);
        });
    }
}
