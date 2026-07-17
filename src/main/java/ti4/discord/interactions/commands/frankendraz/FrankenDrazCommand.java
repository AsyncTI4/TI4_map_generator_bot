package ti4.discord.interactions.commands.frankendraz;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;

public class FrankenDrazCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new FrankenDrazAddFaction(),
                    new FrankenDrazRemoveFaction(),
                    new FrankenDrazSwapFaction(),
                    new FrankenDrazSetKeptComponentLimit(),
                    new FrankenDrazFactionLimit(),
                    new FrankenDrazHelp())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.FRANKENDRAZ;
    }

    @Override
    public String getDescription() {
        return "FrankenDraz";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
