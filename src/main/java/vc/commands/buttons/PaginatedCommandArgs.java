package vc.commands.buttons;

import org.springframework.lang.Nullable;

import java.time.LocalDate;

public record PaginatedCommandArgs(
    String playerName,
    int page,
    @Nullable LocalDate startDate,
    @Nullable LocalDate endDate,
    @Nullable String a // padding if needed
) {
}
