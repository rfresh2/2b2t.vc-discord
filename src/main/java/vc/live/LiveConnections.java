package vc.live;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.Color;
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
        final JDA jda,
        final LiveFeedRepository liveFeedRepository,
        final LiveFeedDispatcher dispatcher,
        @Value("${LIVE_FEEDS}")
        final String liveFeedsEnabled
    ) {
        super(feedListener, jda, liveFeedRepository, dispatcher, Boolean.parseBoolean(liveFeedsEnabled));
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

    @Override
    protected LiveFeedDispatcher.FeedLane feedLane() {
        return LiveFeedDispatcher.FeedLane.CONNECTIONS;
    }

    protected MessageEmbed buildConnectionsEmbed(final ConnectionsRecord con) {
        boolean isJoin = con.connection() == Connectiontype.JOIN;
        return new EmbedBuilder()
            .setDescription("**" + escape(con.playerName()) + "** " + (isJoin ? "connected" : "disconnected"))
            .setFooter("\u200b", avatarUrl(con.playerUuid()).toString())
            .setColor(isJoin ? Color.SEA_GREEN : Color.RUBY)
            .setTimestamp(con.time().toInstant())
            .build();
    }

    protected long getConnectionTimestamp(final ConnectionsRecord con) {
        return con.time().toInstant().toEpochMilli();
    }
}
