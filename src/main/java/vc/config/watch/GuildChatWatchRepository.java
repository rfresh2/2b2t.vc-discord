package vc.config.watch;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import vc.config.watch.model.GuildChatWatchConfig;

import java.util.List;

@Repository
public class GuildChatWatchRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildChatWatchRepository.class);
    static final String cacheId = "guildChatWatch";

    private final Jdbi jdbi;

    public GuildChatWatchRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Cacheable(value = cacheId, key = "#root.methodName")
    public List<GuildChatWatchConfig> getAll() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM guild_chat_watch_config")
                .mapTo(GuildChatWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving guild chat watch configs", e);
            return List.of();
        }
    }

    @Cacheable(cacheId)
    public List<GuildChatWatchConfig> getByGuildId(final String guildId) {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM guild_chat_watch_config WHERE guild_id = :guildId")
                .bind("guildId", guildId)
                .mapTo(GuildChatWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving guild chat watch configs for guild ID: {}", guildId, e);
            return List.of();
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void write(final GuildChatWatchConfig config) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("""
                INSERT OR REPLACE INTO guild_chat_watch_config (
                    watch_id,
                    guild_id,
                    guild_name,
                    channel_id,
                    keyword,
                    case_sensitive,
                    mention_user_id,
                    mention_role_id
                ) VALUES (
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

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void deleteByWatchId(final String watchId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM guild_chat_watch_config WHERE watch_id = :watchId")
                .bind("watchId", watchId)
                .execute();
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void deleteByGuildId(final String guildId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM guild_chat_watch_config WHERE guild_id = :guildId")
                .bind("guildId", guildId)
                .execute();
        } catch (final Exception e) {
            LOGGER.error("Error deleting guild chat watch configs for guild ID: {}", guildId, e);
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void delete(final GuildChatWatchConfig config) {
        deleteByWatchId(config.watchId());
    }
}
