package vc.live.dto;


import vc.live.dto.enums.Connectiontype;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConnectionsRecord(OffsetDateTime time, Connectiontype connection, String playerName, UUID playerUuid) { }
