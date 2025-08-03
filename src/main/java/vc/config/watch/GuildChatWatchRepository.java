package vc.config.watch;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import vc.config.watch.model.GuildChatWatchConfig;

import java.util.List;

@Repository
public class GuildChatWatchRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildChatWatchRepository.class);

    private final Jdbi jdbi;

    public GuildChatWatchRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public List<GuildChatWatchConfig> getGuildChatWatchConfigs() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM guild_chat_watch_config")
                .mapTo(GuildChatWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving guild chat watch configs", e);
            return List.of();
        }
    }

    public void writeGuildChatWatchConfig(final GuildChatWatchConfig config) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("""
                INSERT OR REPLACE INTO guild_chat_watch_config VALUES (
                    :watchId,
                    :guildId,
                    :guildName,
                    :channelId,
                    :keyword,
                    :caseSensitive,
                    :mentionUserId,
                    :mentionRoleId
                );
                """)
                .bind("watchId", config.watchId())
                .bind("guildId", config.guildId())
                .bind("guildName", config.guildName())
                .bind("channelId", config.channelId())
                .bind("keyword", config.keyword())
                .bind("caseSensitive", config.caseSensitive())
                .bind("mentionUserId", config.mentionUserId())
                .bind("mentionRoleId", config.mentionRoleId())
                .execute();
        }
    }

    public void deleteGuildChatWatchConfig(final String watchId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM guild_chat_watch_config WHERE watch_id = :watchId")
                .bind("watchId", watchId)
                .execute();
        }
    }
}
