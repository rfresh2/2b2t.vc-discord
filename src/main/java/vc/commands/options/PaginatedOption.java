package vc.commands.options;

public class PaginatedOption implements ChatInteractionOption {
    @Override
    public void apply(final ChatInteractionOptionContext context) {
        int pageArg = context.event.getOptionAsLong("page")
            .map(Long::intValue)
            .orElse(1);
        if (pageArg <= 0 || pageArg > 10000) {
            context.setError("Page must be greater than 0");
            return;
        }
        context.page = pageArg;
    }
}
