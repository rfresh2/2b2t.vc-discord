/*
 * This file is generated by jOOQ.
 */
package vc.live.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({"all", "unchecked", "rawtypes"})
public class Chats implements Serializable {

    private static final long serialVersionUID = 1L;

    private final OffsetDateTime time;
    private final String chat;
    private final String playerName;
    private final UUID playerUuid;

    public Chats(Chats value) {
        this.time = value.time;
        this.chat = value.chat;
        this.playerName = value.playerName;
        this.playerUuid = value.playerUuid;
    }

    @JsonCreator
    public Chats(
            @JsonProperty("time") OffsetDateTime time,
            @JsonProperty("chat") String chat,
            @JsonProperty("playerName") String playerName,
            @JsonProperty("playerUuid") UUID playerUuid
    ) {
        this.time = time;
        this.chat = chat;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
    }

    /**
     * Getter for <code>public.chats.time</code>.
     */
    public OffsetDateTime getTime() {
        return this.time;
    }

    /**
     * Getter for <code>public.chats.chat</code>.
     */
    public String getChat() {
        return this.chat;
    }

    /**
     * Getter for <code>public.chats.player_name</code>.
     */
    public String getPlayerName() {
        return this.playerName;
    }

    /**
     * Getter for <code>public.chats.player_uuid</code>.
     */
    public UUID getPlayerUuid() {
        return this.playerUuid;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Chats (");

        sb.append(time);
        sb.append(", ").append(chat);
        sb.append(", ").append(playerName);
        sb.append(", ").append(playerUuid);

        sb.append(")");
        return sb.toString();
    }
}
