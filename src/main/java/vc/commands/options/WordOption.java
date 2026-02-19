package vc.commands.options;

import vc.util.Validator;

public class WordOption implements ChatInteractionOption {
    @Override
    public void apply(final ChatInteractionOptionContext context) {
        var wordOptional = context.getOptionAsString("word");
        if (wordOptional.isPresent()) {
            var word = wordOptional.get();
            if (word.isBlank() || word.length() < 3 || word.length() > 50) {
                context.setError("Word must be between 3 and 50 characters");
                return;
            }
            if (!Validator.isValidChat(word)) {
                context.setError("Word contains invalid chat characters");
                return;
            }
            context.word = wordOptional.get();
        }
    }
}
