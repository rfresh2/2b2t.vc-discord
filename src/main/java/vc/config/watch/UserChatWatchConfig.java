package vc.config.watch;

import org.jdbi.v3.core.mapper.reflect.ColumnName;

public record UserChatWatchConfig(
    @ColumnName("watch_id") String watchId,
    @ColumnName("owner_user_id") String ownerUserId,
    @ColumnName("owner_user_name") String ownerUserName,
    @ColumnName("keyword") String keyword,
    @ColumnName("case_sensitive") boolean caseSensitive
) implements UserWatch { }
