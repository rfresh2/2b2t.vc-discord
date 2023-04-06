package vc.util;

public class Validator {
    public static boolean isUUID(String uuid) {
        return uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    public static boolean isValidUsername(String username) {
        return username.matches("[a-zA-Z0-9_]{1,16}");
    }
}
