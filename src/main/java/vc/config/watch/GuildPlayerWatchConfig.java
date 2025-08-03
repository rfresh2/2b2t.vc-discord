package vc.config.watch;

import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.util.UUID;

public record GuildPlayerWatchConfig(
    @ColumnName("watch_id") String watchId,
    @ColumnName("guild_id") String guildId,
    @ColumnName("guild_name") String guildName,
    @ColumnName("channel_id") String channelId,
    @ColumnName("joins") boolean joins,
    @ColumnName("leaves") boolean leaves,
    @ColumnName("chats") boolean chats,
    @ColumnName("deaths") boolean deaths,
    @ColumnName("kills") boolean kills,
    @ColumnName("mention_user_id") String mentionUserId,
    @ColumnName("mention_role_id") String mentionRoleId,
    @ColumnName("target_uuid") UUID targetUuid,
    @ColumnName("target_name") String targetName
) implements GuildWatch { }
