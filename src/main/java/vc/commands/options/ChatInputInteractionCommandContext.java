package vc.commands.options;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import vc.api.model.ProfileData;

import java.time.LocalDate;

public class ChatInputInteractionCommandContext {
    public boolean errorSet = false;
    public String errorMessage = "";
    public final ChatInputInteractionEvent event;
    public int page = 1;
    public ProfileData profileData;
    public LocalDate startDate;
    public LocalDate endDate;

    public ChatInputInteractionCommandContext(final ChatInputInteractionEvent event) {
        this.event = event;
    }

    public void setError(String message) {
        this.errorSet = true;
        this.errorMessage = message;
    }
}
