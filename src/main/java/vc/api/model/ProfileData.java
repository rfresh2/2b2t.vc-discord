package vc.api.model;

import vc.util.DiscordMarkdownEscape;

import java.util.UUID;

public interface ProfileData {
    String name();
    UUID uuid();

    default String getNameMCLink(UUID uuid) {
        return "https://namemc.com/profile/" + uuid.toString();
    }

    default String getAvatarURL() {
        return String.format("https://api.mineatar.io/body/full/%s", uuid());
    }

    default String getHeadURL() {
        return String.format("https://crafthead.net/helm/%s/64", uuid().toString().replace("-", ""));
    }

    default String toDiscordFieldValue() {
        var nameEscaped = DiscordMarkdownEscape.escape(name());
        var fmt = "[%s](%s)";
        if (!nameEscaped.equals(name())) {
            // discord markdown does not support escaping inside links
            // well, it does, but the backslashes are always visible so its arguably worse
            // https://github.com/discord/discord-api-docs/issues/6185
            fmt = "%s ([link](%s))";
        }
        return String.format(fmt, nameEscaped, getNameMCLink(uuid()));
    }
}
