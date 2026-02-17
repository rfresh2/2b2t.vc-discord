package vc.commands.options;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import vc.api.model.ProfileData;

import java.time.LocalDate;
import java.util.Optional;

public class ChatInteractionOptionContext {
    private boolean errorSet = false;
    private String errorMessage = "";
    public final SlashCommandInteractionEvent event;
    public int page;
    public ProfileData profileData;
    public String word;
    public LocalDate startDate;
    public LocalDate endDate;

    public ChatInteractionOptionContext(final SlashCommandInteractionEvent event) {
        this.event = event;
    }

    public Optional<String> getOptionAsString(String optionName) {
        return Optional.ofNullable(event.getOption(optionName)).map(OptionMapping::getAsString);
    }

    public Optional<Integer> getOptionAsInt(String optionName) {
        return Optional.ofNullable(event.getOption(optionName)).map(OptionMapping::getAsInt);
    }

    public void setError(String message) {
        this.errorSet = true;
        this.errorMessage = message;
    }

    public boolean isErrorSet() {
        return errorSet;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
