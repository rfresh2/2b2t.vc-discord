package vc.config.watch;

public interface GuildWatch extends Watch {
    String guildId();
    String guildName();
    String channelId();
    String mentionUserId();
    String mentionRoleId();
}
