package vc.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import io.swagger.client.api.PlaytimeApi;
import io.swagger.client.model.PlaytimeResponse;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

public class PlaytimeCommand implements SlashCommand {

    private final PlaytimeApi playtimeApi = new PlaytimeApi();

    @Override
    public String getName() {
        return "playtime";
    }

    @Override
    public Mono<Void> handle(final ChatInputInteractionEvent event) {
        Optional<String> playerNameOptional = event.getOption("username")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        Optional<String> uuidOptional = event.getOption("uuid")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
        // todo: check if either are present, if both are present prefer UUID
        //  validate UUID is parseable
        //  validate playerName is correct regex
        //  for playerName we need to resolve the player's profile to UUID through mojang API
        //  implement api.2b2t.vc interaction class(es)

        PlaytimeResponse playtime = playtimeApi.playtime(UUID.fromString("572e683c-888a-4a0d-bc10-5d9cfa76d892"));
        Integer playtimeSeconds = playtime.getPlaytimeSeconds();

        return null;
    }
}
