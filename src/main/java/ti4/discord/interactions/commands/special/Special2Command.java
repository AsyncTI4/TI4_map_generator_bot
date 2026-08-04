package ti4.discord.interactions.commands.special;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;

public class Special2Command implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new SetupNeutralPlayer(),
                    new GeneratePainBoxMapString(),
                    new SearchWinningPath(),
                    new SetExpedition(),
                    new Galvanize(),
                    new LoreCommand(),
                    new ImportDeckConfig())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.SPECIAL + "2";
    }

    @Override
    public String getDescription() {
        return "More special commands";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
