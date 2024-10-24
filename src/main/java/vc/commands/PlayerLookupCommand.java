package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.util.PlayerLookup;
import vc.util.Validator;

import java.time.LocalDate;
import java.util.Optional;

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

    @FunctionalInterface
    public interface SimpleResolveFunction {
        Mono<Message> resolve(ChatInputInteractionEvent event, ProfileData identity);
    }

    Mono<Message> resolveData(ChatInputInteractionEvent event, SimpleResolveFunction resolveFunction) {
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
            return resolveFunction.resolve(event, identity);
        });
    }

    @FunctionalInterface
    public interface PaginatedResolveFunction {
        Mono<Message> resolve(ChatInputInteractionEvent event, ProfileData identity, int page, LocalDate startDate, LocalDate endDate);
    }

    Mono<Message> resolveData(ChatInputInteractionEvent event, PaginatedResolveFunction resolveFunction) {
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
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = getLocalDateIfPresent(event, "startdate");
            endDate = getLocalDateIfPresent(event, "enddate");
            if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                return error(event, "Start Date must be before End Date");
            }
        } catch (Exception e) {
            return error(event, "Invalid date. Required format: YYYY-MM-DD");
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
            return resolveFunction.resolve(event, identity, page, startDate, endDate);
        });
    }
}
