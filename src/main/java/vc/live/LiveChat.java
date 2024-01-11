package vc.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.EmbedData;
import discord4j.rest.util.Color;
import org.springframework.stereotype.Component;
import vc.config.GuildConfigManager;
import vc.config.GuildConfigRecord;
import vc.live.dto.ChatsRecord;
import vc.live.dto.DeathsRecord;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.Arrays.asList;

@Component
public class LiveChat extends LiveFeed {

    // todo: we aren't receiving system messages from the existing chat queue
    //  so no restart msgs

    public LiveChat(final RedisClient redisClient,
                    final GatewayDiscordClient discordClient,
                    final GuildConfigManager guildConfigManager,
                    final ScheduledExecutorService executorService,
                    final ObjectMapper objectMapper
    ) {
        super(redisClient,
              discordClient,
              guildConfigManager,
              executorService,
              objectMapper);
    }

    @Override
    protected boolean channelEnabledPredicate(final GuildConfigRecord guildConfigRecord) {
        return guildConfigRecord.liveChatEnabled();
    }

    @Override
    protected String liveChannelId(final GuildConfigRecord guildConfigRecord) {
        return guildConfigRecord.liveChatChannelId();
    }

    @Override
    protected GuildConfigRecord disableRecordInternal(final GuildConfigRecord in) {
        return new GuildConfigRecord(in.guildId(), in.guildName(), false, in.liveChatChannelId(), in.liveConnectionsEnabled(), in.liveConnectionsChannelId());
    }

    @Override
    protected GuildConfigRecord enableRecordInternal(final GuildConfigRecord in, final String guildId, final String channelId) {
        return new GuildConfigRecord(guildId, in.guildName(), true, channelId, in.liveConnectionsEnabled(), in.liveConnectionsChannelId());
    }

    @Override
    protected List<InputQueue> inputQueues() {
        return asList(new InputQueue<>("ChatsQueue", ChatsRecord.class, this::getChatEmbed, this::getChatTimestamp),
                      new InputQueue<>("DeathsQueue", DeathsRecord.class, this::getDeathEmbed, this::getDeathTimestamp));
    }

    private EmbedData getChatEmbed(final ChatsRecord chat) {
        return EmbedCreateSpec.builder()
            .description(escape("**" + chat.playerName() + ":** " + chat.chat()))
            .footer("\u200b", avatarUrl(chat.playerUuid()).toString())
            .color(chat.chat().startsWith(">") ? Color.MEDIUM_SEA_GREEN : Color.BLACK)
            .timestamp(Instant.ofEpochSecond(chat.time().toEpochSecond()))
            .build()
            .asRequest();
    }

    protected long getChatTimestamp(final ChatsRecord chat) {
        return chat.time().toEpochSecond();
    }

    private EmbedData getDeathEmbed(final DeathsRecord death) {
        return EmbedCreateSpec.builder()
            .description(escape(death.deathMessage().replace(death.victimPlayerName(), "**" + death.victimPlayerName() + "**")))
            .footer("\u200b", avatarUrl(death.victimPlayerUuid()).toString())
            .color(Color.RUBY)
            .timestamp(Instant.ofEpochSecond(death.time().toEpochSecond()))
            .build()
            .asRequest();
    }

    protected long getDeathTimestamp(final DeathsRecord death) {
        return death.time().toEpochSecond();
    }
}
