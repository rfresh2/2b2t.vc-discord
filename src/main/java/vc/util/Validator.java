package vc.util;

public class Validator {
    public static boolean isUUID(String uuid) {
        return uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    public static boolean isValidPlayerName(String playerName) {
        return PlayerLookup.validUsernamePattern.matcher(playerName).matches() || PlayerLookup.bedrockUsernamePattern.matcher(playerName).matches();
    }

    public static boolean isValidChat(String chat) {
        if (chat == null || chat.isBlank()) return false;
        for (char c0 : chat.toCharArray()) {
            if (!isAllowedChatCharacter(c0)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAllowedChatCharacter(char c0) {
        return c0 != 167 && c0 >= 32 && c0 != 127;
    }
}
