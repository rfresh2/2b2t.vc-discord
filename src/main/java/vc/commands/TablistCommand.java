package vc.commands;

import com.google.common.collect.Lists;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import vc.openapi.vc.handler.TabListApi;
import vc.openapi.vc.model.TablistEntry;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Objects.isNull;

@Component
public class TablistCommand implements SlashCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(TablistCommand.class);

    private final TabListApi tabListApi;

    public TablistCommand(final TabListApi tabListApi) {
        this.tabListApi = tabListApi;
    }

    @Override
    public String getName() {
        return "tablist";
    }

    @Override
    public Mono<Message> handle(final ChatInputInteractionEvent event) {
        List<TablistEntry> onlinePlayers = tabListApi.onlinePlayers();
        if (isNull(onlinePlayers) || onlinePlayers.isEmpty()) return error(event, "Unable to resolve current tablist");
        List<String> playerNames = onlinePlayers.stream()
                .map(TablistEntry::getPlayerName)
                .distinct()
                .sorted(String::compareTo)
                .toList();
        final int longestPlayerNameSize = playerNames.stream().map(String::length).max(Integer::compareTo).get();
        final int colSize = 4; // num cols of playernames
        final int paddingSize = 1; // num spaces between names
        List<List<String>> playerNamesColumnized = Lists.partition(playerNames, colSize);
        final List<String> rows = new ArrayList<>();

        final List<List<String>> colOrderedNames = new ArrayList<>();
        IntStream.range(0, playerNamesColumnized.size())
                .forEach(i -> colOrderedNames.add(new ArrayList<>()));
        // iterate down col, then row
        final ListIterator<String> pNameIterator = playerNames.listIterator();
        for (int i = 0; i < colSize; i++) {
            for (int col = 0; col < playerNamesColumnized.size(); col++) {
                if (pNameIterator.hasNext()) {
                    colOrderedNames.get(col).add(pNameIterator.next());
                } else {
                    break;
                }
            }
        }

        colOrderedNames.forEach(row -> {
            final StringBuilder stringBuilder = new StringBuilder();
            final Formatter formatter = new Formatter(stringBuilder);
            final String formatting = IntStream.range(0, row.size())
                    .mapToObj(i -> "%-" + (longestPlayerNameSize + paddingSize) + "." + (longestPlayerNameSize + paddingSize) + "s")
                    .collect(Collectors.joining(" "));
            formatter.format(formatting, row.toArray());
            stringBuilder.append("\n");
            rows.add(stringBuilder.toString());
        });


        final List<String> outputMessages = new ArrayList<>();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (out.toString().length() + rows.get(i).length() < 1950) {
                out.append(rows.get(i));
            } else {
                outputMessages.add(out.toString());
                out = new StringBuilder();
            }
        }
        outputMessages.add(out.toString());
        try {
            outputMessages.forEach(outputMessage -> {
                event.createFollowup().withContent("```\n" + outputMessage + "\n```").block();
            });
        } catch (final Exception e) {
            LOGGER.warn("Error sending tablist", e);
        }
        return Mono.empty();
    }
}
