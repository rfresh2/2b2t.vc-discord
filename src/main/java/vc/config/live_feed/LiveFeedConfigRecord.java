package vc.config.live_feed;

import org.jdbi.v3.core.mapper.reflect.ColumnName;

public record LiveFeedConfigRecord(
    @ColumnName("guild_id") String guildId,
    @ColumnName("guild_name") String guildName,
    @ColumnName("live_chat_enabled") boolean liveChatEnabled,
    @ColumnName("live_chat_channel_id") String liveChatChannelId,
    @ColumnName("live_connections_enabled") boolean liveConnectionsEnabled,
    @ColumnName("live_connections_channel_id") String liveConnectionsChannelId
) {}
