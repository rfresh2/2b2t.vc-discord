package vc.commands;

import org.springframework.lang.Nullable;

import java.time.LocalDate;

public record PaginatedCommandArgs(String playerName, int page, @Nullable LocalDate startDate, @Nullable LocalDate endDate) {
}
