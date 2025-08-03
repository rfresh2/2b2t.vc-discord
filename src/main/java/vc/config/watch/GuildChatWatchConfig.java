package vc.config.watch;

import org.jdbi.v3.core.mapper.reflect.ColumnName;

public record GuildChatWatchConfig(
    @ColumnName("watch_id") String watchId,
    @ColumnName("guild_id") String guildId,
    @ColumnName("guild_name") String guildName,
    @ColumnName("channel_id") String channelId,
    @ColumnName("keyword") String keyword,
    @ColumnName("case_sensitive") boolean caseSensitive,
    @ColumnName("mention_user_id") String mentionUserId,
    @ColumnName("mention_role_id") String mentionRoleId
) implements GuildWatch { }
