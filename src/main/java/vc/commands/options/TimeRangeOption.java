package vc.commands.options;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

public class TimeRangeOption implements ChatInteractionOption {
    @Override
    public void apply(final ChatInteractionOptionContext context) {
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = getLocalDateIfPresent(context, "startdate");
            endDate = getLocalDateIfPresent(context, "enddate");
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

    @Nullable LocalDate getLocalDateIfPresent(ChatInteractionOptionContext context, String argName) {
        var inputOptional = context.getOptionAsString(argName);
        if (inputOptional.isEmpty()) return null;
        try {
            return LocalDate.parse(inputOptional.get());
        } catch (Exception e) {
            throw new RuntimeException("Failed parsing date: " + inputOptional.get());
        }
    }
}
