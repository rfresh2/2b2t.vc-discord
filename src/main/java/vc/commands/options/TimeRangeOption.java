package vc.commands.options;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import org.springframework.lang.Nullable;

import java.time.LocalDate;

public class TimeRangeOption implements ChatInteractionOptionInstance {
    @Override
    public void apply(final ChatInputInteractionCommandContext context) {
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = getLocalDateIfPresent(context.event, "startdate");
            endDate = getLocalDateIfPresent(context.event, "enddate");
            if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                context.setError("Start Date must be before End Date");
                return;
            }
        } catch (Exception e) {
            context.setError("Invalid date. Required format: YYYY-MM-DD");
            return;
        }
        context.startDate = startDate;
        context.endDate = endDate;
    }

    // throws runtime exception if date is present but format is invalid
    @Nullable LocalDate getLocalDateIfPresent(ChatInputInteractionEvent event, String argName) {
        var inputOptional = event.getOptionAsString(argName);
        if (inputOptional.isEmpty()) return null;
        try {
            return LocalDate.parse(inputOptional.get());
        } catch (Exception e) {
            throw new RuntimeException("Failed parsing date: " + inputOptional.get());
        }
    }
}
