package vc.commands.options;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import vc.api.model.ProfileData;

import java.time.LocalDate;

public class ChatInteractionOptionContext {
    private boolean errorSet = false;
    private String errorMessage = "";
    public final ChatInputInteractionEvent event;
    public int page;
    public ProfileData profileData;
    public LocalDate startDate;
    public LocalDate endDate;

    public ChatInteractionOptionContext(final ChatInputInteractionEvent event) {
        this.event = event;
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
