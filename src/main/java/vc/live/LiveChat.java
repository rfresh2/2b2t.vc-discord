package vc.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.EmbedData;
import discord4j.rest.util.Color;
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

    // todo: we aren't receiving system messages from the existing chat queue
    //  so no restart msgs

    public LiveChat(
        final RedisClient redisClient,
        final GatewayDiscordClient discordClient,
        final LiveFeedRepository liveFeedRepository,
        final ObjectMapper objectMapper,
        @Value("${LIVE_FEEDS}")
        final String liveFeedsEnabled
    ) {
        super(
            redisClient,
            discordClient,
            liveFeedRepository,
            objectMapper,
            Boolean.parseBoolean(liveFeedsEnabled)
        );
    }

    @Override
    protected List<LiveFeedConfig> getAllEnabled() {
        return liveFeedRepository.getByLiveChatEnabled();
    }

    @Override
    protected boolean channelEnabledPredicate(final LiveFeedConfig guildConfigRecord) {
        return guildConfigRecord.liveChatEnabled();
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
        return asList(new InputQueue<>("ChatsTopic", ChatsRecord.class, this::getChatEmbed, this::getChatTimestamp),
            new InputQueue<>("DeathsTopic", DeathsRecord.class, this::getDeathEmbed, this::getDeathTimestamp));
    }

    private EmbedData getChatEmbed(final ChatsRecord chat) {
        return EmbedCreateSpec.builder()
            .description("**" + escape(chat.playerName()) + ":** " + escape(chat.chat()))
            .footer("\u200b", avatarUrl(chat.playerUuid()).toString())
            .color(chat.chat().startsWith(">") ? Color.MEDIUM_SEA_GREEN : Color.BLACK)
            .timestamp(chat.time().toInstant())
            .build()
            .asRequest();
    }

    protected long getChatTimestamp(final ChatsRecord chat) {
        return chat.time().toEpochSecond();
    }

    private EmbedData getDeathEmbed(final DeathsRecord death) {
        return EmbedCreateSpec.builder()
            .description(escape(death.deathMessage()).replace(escape(death.victimPlayerName()), "**" + escape(death.victimPlayerName()) + "**"))
            .footer("\u200b", avatarUrl(death.victimPlayerUuid()).toString())
            .color(Color.RUBY)
            .timestamp(death.time().toInstant())
            .build()
            .asRequest();
    }

    protected long getDeathTimestamp(final DeathsRecord death) {
        return death.time().toEpochSecond();
    }
}
