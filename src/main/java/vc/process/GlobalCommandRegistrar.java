package vc.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class GlobalCommandRegistrar implements ApplicationRunner {
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private final JDA jda;
    private final ObjectMapper objectMapper;
    private final List<RegisteredCommand> commands = new ArrayList<>();

    public GlobalCommandRegistrar(JDA jda, ObjectMapper objectMapper) {
        this.jda = jda;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        PathMatchingResourcePatternResolver matcher = new PathMatchingResourcePatternResolver();
        List<SlashCommandData> commandData = new ArrayList<>();

        for (Resource resource : matcher.getResources("classpath*:commands/*.json")) {
            JsonNode root = objectMapper.readTree(resource.getInputStream());
            var name = root.path("name").asText();
            var description = root.path("description").asText("");
            commands.add(new RegisteredCommand(name, description));

            var slash = Commands.slash(name, description);
            applyOptions(slash, root.path("options"));
            commandData.add(slash);
        }

        commandData.sort(Comparator.comparing(SlashCommandData::getName));
        commands.sort(Comparator.comparing(RegisteredCommand::name));

        jda.updateCommands()
            .addCommands(commandData)
            .queue(
                result -> result.forEach(cmd -> LOGGER.info("registered command: {}", cmd.getName())),
                e -> LOGGER.error("Failed to register global commands", e)
            );
    }

    private void applyOptions(SlashCommandData slash, JsonNode optionsNode) {
        if (!optionsNode.isArray()) return;
        for (JsonNode optionNode : optionsNode) {
            int type = optionNode.path("type").asInt();
            String name = optionNode.path("name").asText();
            String description = optionNode.path("description").asText("");

            if (type == 1) {
                SubcommandData sub = new SubcommandData(name, description);
                applySubcommandOptions(sub, optionNode.path("options"));
                slash.addSubcommands(sub);
            } else if (type == 2) {
                SubcommandGroupData group = new SubcommandGroupData(name, description);
                JsonNode subcommands = optionNode.path("options");
                if (subcommands.isArray()) {
                    for (JsonNode subNode : subcommands) {
                        SubcommandData sub = new SubcommandData(
                            subNode.path("name").asText(),
                            subNode.path("description").asText("")
                        );
                        applySubcommandOptions(sub, subNode.path("options"));
                        group.addSubcommands(sub);
                    }
                }
                slash.addSubcommandGroups(group);
            } else {
                slash.addOptions(toOptionData(optionNode));
            }
        }
    }

    private void applySubcommandOptions(SubcommandData subcommand, JsonNode optionsNode) {
        if (!optionsNode.isArray()) return;
        for (JsonNode optionNode : optionsNode) {
            subcommand.addOptions(toOptionData(optionNode));
        }
    }

    private OptionData toOptionData(JsonNode optionNode) {
        OptionType optionType = OptionType.fromKey(optionNode.path("type").asInt());
        if (optionType == OptionType.UNKNOWN) {
            throw new IllegalArgumentException("Unknown option type in command json: " + optionNode);
        }
        return new OptionData(
            optionType,
            optionNode.path("name").asText(),
            optionNode.path("description").asText(""),
            optionNode.path("required").asBoolean(false)
        );
    }

    public List<RegisteredCommand> getCommands() {
        return commands;
    }

    public record RegisteredCommand(String name, String description) {
    }
}
