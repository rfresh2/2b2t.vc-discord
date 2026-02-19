package vc.commands.options;

public class PaginatedOption implements ChatInteractionOption {
    @Override
    public void apply(final ChatInteractionOptionContext context) {
        var page = context.getOptionAsInt("page").orElse(1);
        if (page < 1) {
            context.setError("Page must be at least 1");
            return;
        }
        context.page = page;
    }
}
