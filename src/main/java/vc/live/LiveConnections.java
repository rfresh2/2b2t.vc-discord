package vc.live;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.EmbedData;
import discord4j.rest.util.Color;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vc.config.live_feed.LiveFeedRepository;
import vc.config.live_feed.model.LiveFeedConfig;
import vc.live.dto.ConnectionsRecord;
import vc.live.dto.enums.Connectiontype;

import java.util.List;

import static vc.util.DiscordMarkdownEscape.escape;

@Component
public class LiveConnections extends LiveFeed {
    public LiveConnections(
        final FeedApiManager feedListener,
        final GatewayDiscordClient discordClient,
        final LiveFeedRepository liveFeedRepository,
        @Value("${LIVE_FEEDS}")
        final String liveFeedsEnabled
    ) {
        super(
            feedListener,
            discordClient,
            liveFeedRepository,
            Boolean.parseBoolean(liveFeedsEnabled)
        );
    }

    @Override
    protected List<LiveFeedConfig> getAllEnabled() {
        return liveFeedRepository.getByLiveConnectionsEnabled();
    }

    @Override
    protected String liveChannelId(final LiveFeedConfig guildConfigRecord) {
        return guildConfigRecord.liveConnectionsChannelId();
    }

    @Override
    public LiveFeedConfig disableRecordInternal(final LiveFeedConfig in) {
        return new LiveFeedConfig(in.guildId(), in.guildName(), in.liveChatEnabled(), in.liveChatChannelId(), false, in.liveConnectionsChannelId());
    }

    @Override
    protected LiveFeedConfig enableRecordInternal(final LiveFeedConfig in, final String guildId, final String channelId) {
        return new LiveFeedConfig(in.guildId(), in.guildName(), in.liveChatEnabled(), in.liveChatChannelId(), true, channelId);
    }

    @Override
    protected List<InputQueue> inputQueues() {
        return List.of(new InputQueue<>(
            "Connections",
            feedListener::addConnectionListener,
            ConnectionsRecord.class,
            this::buildConnectionsEmbed,
            this::getConnectionTimestamp)
        );
    }

    protected EmbedData buildConnectionsEmbed(final ConnectionsRecord con) {
        boolean isJoin = con.connection() == Connectiontype.JOIN;
        return EmbedCreateSpec.builder()
            .description("**" + escape(con.playerName()) + "** " + (isJoin ? "connected" : "disconnected"))
            .footer("\u200b", avatarUrl(con.playerUuid()).toString())
            .color(isJoin ? Color.SEA_GREEN : Color.RUBY)
            .timestamp(con.time().toInstant())
            .build()
            .asRequest();
    }

    protected long getConnectionTimestamp(final ConnectionsRecord con) {
        return con.time().toInstant().toEpochMilli();
    }
}
