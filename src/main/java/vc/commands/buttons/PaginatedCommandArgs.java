package vc.commands.buttons;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

public record PaginatedCommandArgs(
    @Nullable String playerName,
    @Nullable String word,
    int page,
    @Nullable LocalDate startDate,
    @Nullable LocalDate endDate,
    @Nullable String action,
    @Nullable String a // padding if needed
) {
}
