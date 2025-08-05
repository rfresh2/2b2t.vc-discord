package vc.util;

public class DiscordMarkdownEscape {
    private DiscordMarkdownEscape() {}

    public static String escape(String input) {
        // backslash
        if (input.contains("\\")) {
            input = input.replace("\\", "\\\\");
        }

        // code
        if (input.contains("`")) {
            input = input.replace("`", "\\`");
        }

        // bold and italics and bulleted list
        if (input.contains("*")) {
            input = input.replace("*", "\\*");
        }

        // italics and underline
        if (input.contains("_")) {
            input = input.replace("_", "\\_");
        }

        // strikethrough
        if (input.contains("~~")) {
            input = input.replace("~~", "\\~\\~");
        }

        // spoiler
        if (input.contains("||")) {
            input = input.replace("||", "\\|\\|");
        }

        // headings
        if (input.contains("#")) {
            input = input.replace("#", "\\#");
        }

        // links
        if (input.contains("[")) {
            input = input.replace("[", "\\[");
        }

        return input;
    }
}
