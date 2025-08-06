package vc.config.live_feed.model;

import org.jdbi.v3.core.mapper.reflect.ColumnName;

public record LiveFeedConfig(
    @ColumnName("guild_id") String guildId,
    @ColumnName("guild_name") String guildName,
    @ColumnName("live_chat_enabled") boolean liveChatEnabled,
    @ColumnName("live_chat_channel_id") String liveChatChannelId,
    @ColumnName("live_connections_enabled") boolean liveConnectionsEnabled,
    @ColumnName("live_connections_channel_id") String liveConnectionsChannelId
) {
    public static LiveFeedConfig defaultConfig(String guildId, String guildName) {
        return new LiveFeedConfig(guildId, guildName, false, "", false, "");
    }
}
