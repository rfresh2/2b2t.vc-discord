package vc.live;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.Color;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vc.config.live_feed.LiveFeedRepository;
import vc.config.live_feed.model.LiveFeedConfig;
import vc.live.dto.ChatsRecord;
import vc.live.dto.DeathsRecord;

import java.util.List;

import static java.util.Arrays.asList;
import static vc.util.DiscordMarkdownEscape.escape;

@Component
public class LiveChat extends LiveFeed {

    public LiveChat(
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
        return liveFeedRepository.getByLiveChatEnabled();
    }

    @Override
    protected String liveChannelId(final LiveFeedConfig guildConfigRecord) {
        return guildConfigRecord.liveChatChannelId();
    }

    @Override
    protected LiveFeedConfig disableRecordInternal(final LiveFeedConfig in) {
        return new LiveFeedConfig(in.guildId(), in.guildName(), false, in.liveChatChannelId(), in.liveConnectionsEnabled(), in.liveConnectionsChannelId());
    }

    @Override
    protected LiveFeedConfig enableRecordInternal(final LiveFeedConfig in, final String guildId, final String channelId) {
        return new LiveFeedConfig(guildId, in.guildName(), true, channelId, in.liveConnectionsEnabled(), in.liveConnectionsChannelId());
    }

    @Override
    protected List<InputQueue> inputQueues() {
        return asList(
            new InputQueue<>("Chats", feedListener::addChatListener, ChatsRecord.class, this::getChatEmbed, this::getChatTimestamp),
            new InputQueue<>("Deaths", feedListener::addDeathsListener, DeathsRecord.class, this::getDeathEmbed, this::getDeathTimestamp)
        );
    }

    @Override
    protected LiveFeedDispatcher.FeedLane feedLane() {
        return LiveFeedDispatcher.FeedLane.CHAT;
    }

    private MessageEmbed getChatEmbed(final ChatsRecord chat) {
        return new EmbedBuilder()
            .setDescription("**" + escape(chat.playerName()) + ":** " + escape(chat.chat()))
            .setFooter("\u200b", avatarUrl(chat.playerUuid()).toString())
            .setColor(chat.chat().startsWith(">") ? Color.MEDIUM_SEA_GREEN : Color.BLACK)
            .setTimestamp(chat.time().toInstant())
            .build();
    }

    protected long getChatTimestamp(final ChatsRecord chat) {
        return chat.time().toInstant().toEpochMilli();
    }

    private MessageEmbed getDeathEmbed(final DeathsRecord death) {
        return new EmbedBuilder()
            .setDescription(escape(death.deathMessage()).replace(escape(death.victimPlayerName()), "**" + escape(death.victimPlayerName()) + "**"))
            .setFooter("\u200b", avatarUrl(death.victimPlayerUuid()).toString())
            .setColor(Color.RUBY)
            .setTimestamp(death.time().toInstant())
            .build();
    }

    protected long getDeathTimestamp(final DeathsRecord death) {
        return death.time().toInstant().toEpochMilli();
    }
}
