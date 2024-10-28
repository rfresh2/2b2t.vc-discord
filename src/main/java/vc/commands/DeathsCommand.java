package vc.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.api.model.ProfileData;
import vc.commands.buttons.ButtonCommand;
import vc.commands.buttons.PaginatedButtonHandler;
import vc.commands.options.ChatInteractionOptionResolver;
import vc.commands.options.PaginatedOption;
import vc.commands.options.PlayerLookupOption;
import vc.commands.options.TimeRangeOption;
import vc.openapi.handler.ApiException;
import vc.openapi.handler.DeathsApi;
import vc.openapi.model.DeathsResponse;
import vc.util.PlayerLookup;

import java.time.LocalDate;
import java.util.List;

import static discord4j.common.util.TimestampFormat.SHORT_DATE_TIME;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class DeathsCommand implements SlashCommand, ButtonCommand {
    private static final Logger LOGGER = getLogger(DeathsCommand.class);
    private final DeathsApi deathsApi;
    private final ObjectMapper objectMapper;
    private final ChatInteractionOptionResolver resolver;
    private final PlayerLookup playerLookup;
    private final PaginatedButtonHandler buttonHandler;

    public DeathsCommand(final DeathsApi deathsApi, final PlayerLookup playerLookup, final ObjectMapper objectMapper, final PaginatedButtonHandler buttonHandler) {
        this.deathsApi = deathsApi;
        this.playerLookup = playerLookup;
        this.objectMapper = objectMapper;
        this.resolver = new ChatInteractionOptionResolver()
            .registerTrait(new PaginatedOption())
            .registerTrait(new PlayerLookupOption(playerLookup))
            .registerTrait(new TimeRangeOption());
        this.buttonHandler = buttonHandler;
    }

    @Override
    public String getName() {
        return "deaths";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        var ctx = resolver.execute(event);
        if (ctx.errorSet) return error(event, ctx.errorMessage);
        return resolveDeaths(event, ctx.profileData, ctx.page, ctx.startDate, ctx.endDate);
    }

    private Mono<Message> resolveDeaths(final DeferrableInteractionEvent event, final ProfileData identity, int page, LocalDate startDate, LocalDate endDate) {
        DeathsResponse deathsResponse = null;
        try {
            deathsResponse = deathsApi.deaths(identity.uuid(), null, startDate, endDate, 25, page);
        } catch (final Exception e) {
            if (e instanceof ApiException apiException
                && (apiException.getCause() instanceof MismatchedInputException || apiException.getCode() == 204)) {
                // fall through
            } else {
                LOGGER.error("Failed to get deaths", e);
            }
        }
        if (deathsResponse == null || deathsResponse.getDeaths() == null || deathsResponse.getDeaths().isEmpty())
            return error(event, "No deaths found for player");
        List<String> deathStrings = deathsResponse.getDeaths().stream()
                .map(k -> SHORT_DATE_TIME.format(k.getTime().toInstant()) + " " + escape(k.getDeathMessage()))
                .toList();
        StringBuilder result = new StringBuilder();
        for (String s : deathStrings) {
            if (result.length() + s.length() > 4090) {
                LOGGER.warn("Message too long, truncating: {}", s);
                break;
            }
            result.append(s).append("\n");
        }
        if (result.length() > 0) {
            result = new StringBuilder(result.substring(0, result.length() - 1));
        } else {
            return event.createFollowup()
                .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                                .title("Deaths")
                                .color(Color.CYAN)
                                .description("No deaths found")
                                .thumbnail(identity.getAvatarURL())
                                .build());
        }
        return event.createFollowup()
            .withEmbeds(populateIdentity(EmbedCreateSpec.builder(), identity)
                            .title("Deaths")
                            .color(Color.CYAN)
                            .description(result.toString())
                            .addField("Total", ""+deathsResponse.getTotal(), true)
                            .addField("Page", ""+page, true)
                            .addField("Page Count", ""+deathsResponse.getPageCount(), true)
                            .thumbnail(identity.getAvatarURL())
                            .build())
            .withComponents(buttonHandler.getButtonRow(objectMapper, getName(), deathsResponse.getPageCount(), page, identity, startDate, endDate));
    }

    @Override
    public Mono<Message> handleButton(final ButtonInteractionEvent event) {
        return buttonHandler.defaultButtonHandler(event, objectMapper, getName(), playerLookup, this::resolveDeaths, this::error);
    }
}
