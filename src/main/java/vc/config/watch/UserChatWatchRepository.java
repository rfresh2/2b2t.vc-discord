package vc.config.watch;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import vc.config.watch.model.UserChatWatchConfig;

import java.util.List;

@Repository
public class UserChatWatchRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserChatWatchRepository.class);

    private final Jdbi jdbi;

    public UserChatWatchRepository(final Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public List<UserChatWatchConfig> getUserChatWatchConfigs() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM user_chat_watch_config")
                .mapTo(UserChatWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving user chat watch configs", e);
            return List.of();
        }
    }

    public void writeUserChatWatchConfig(final UserChatWatchConfig config) {
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

    public void deleteUserChatWatchConfig(final String watchId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM user_chat_watch_config WHERE watch_id = :watchId")
                .bind("watchId", watchId)
                .execute();
        }
    }
}
