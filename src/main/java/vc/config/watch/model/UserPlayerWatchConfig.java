package vc.config.watch.model;

import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.util.UUID;

public record UserPlayerWatchConfig(
    @ColumnName("watch_id") String watchId,
    @ColumnName("owner_user_id") String ownerUserId,
    @ColumnName("owner_user_name") String ownerUserName,
    @ColumnName("joins") boolean joins,
    @ColumnName("leaves") boolean leaves,
    @ColumnName("chats") boolean chats,
    @ColumnName("deaths") boolean deaths,
    @ColumnName("kills") boolean kills,
    @ColumnName("target_uuid") UUID targetUuid,
    @ColumnName("target_name") String targetName
) implements UserWatch { }
