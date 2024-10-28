package vc.commands.options;

public class PaginatedOption implements ChatInteractionOption {
    @Override
    public void apply(final ChatInteractionOptionContext context) {
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
