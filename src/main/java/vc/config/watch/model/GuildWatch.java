package vc.config.watch.model;

public interface GuildWatch extends Watch {
    String guildId();
    String guildName();
    String channelId();
    String mentionUserId();
    String mentionRoleId();
}
