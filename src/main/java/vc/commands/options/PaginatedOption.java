package vc.commands.options;

public class PaginatedOption implements ChatInteractionOptionInstance {
    /**
     * Extract page argument from ChatInputInteractionEvent
     * return page int
     * decorate output embed with page number
     *
     * Validate page > 0 and return error message if invalid
     */

    @Override
    public void apply(final ChatInputInteractionCommandContext context) {
        var pageArg = context.event.getOptionAsLong("page")
            .map(Long::intValue)
            .orElse(1);
        if (pageArg <= 0) {
            context.setError("Page number must be greater than 0");
            return;
        }
        context.page = pageArg;
    }
}
