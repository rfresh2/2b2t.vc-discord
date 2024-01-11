package vc.live.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeathsRecord(
    OffsetDateTime time,
    String deathMessage,
    String victimPlayerName,
    UUID victimPlayerUuid,
    String killerPlayerName,
    UUID killerPlayerUuid,
    String weaponName,
    String killerMob
) {
}
