package vc.config.watch;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import vc.config.watch.model.UserChatWatchConfig;

import java.util.List;

@Repository
public class UserChatWatchRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserChatWatchRepository.class);
    static final String cacheId = "userChatWatch";

    private final Jdbi jdbi;

    public UserChatWatchRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Cacheable(cacheId)
    public List<UserChatWatchConfig> getAll() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM user_chat_watch_config")
                .mapTo(UserChatWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving user chat watch configs", e);
            return List.of();
        }
    }

    @Cacheable(cacheId)
    public List<UserChatWatchConfig> getByOwnerId(final String ownerUserId) {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM user_chat_watch_config WHERE owner_user_id = :ownerUserId")
                .bind("ownerUserId", ownerUserId)
                .mapTo(UserChatWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving user chat watch configs for owner user ID: {}", ownerUserId, e);
            return List.of();
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void write(final UserChatWatchConfig config) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("""
                INSERT OR REPLACE INTO user_chat_watch_config VALUES (
                    :watchId,
                    :ownerUserId,
                    :ownerUserName,
                    :keyword,
                    :caseSensitive
                );
                """)
                .bind("watchId", config.watchId())
                .bind("ownerUserId", config.ownerUserId())
                .bind("ownerUserName", config.ownerUserName())
                .bind("keyword", config.keyword())
                .bind("caseSensitive", config.caseSensitive())
                .execute();
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void deleteByWatchId(final String watchId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM user_chat_watch_config WHERE watch_id = :watchId")
                .bind("watchId", watchId)
                .execute();
        }
    }

    @CacheEvict(cacheNames = cacheId, allEntries = true)
    public void delete(final UserChatWatchConfig config) {
        deleteByWatchId(config.watchId());
    }
}
