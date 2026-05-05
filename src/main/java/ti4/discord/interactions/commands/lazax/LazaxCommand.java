package ti4.discord.interactions.commands.lazax;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;

public class LazaxCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new LazaxMyPoints(),
                    new LazaxTop100(),
                    new LazaxDelegationLeaderboard(),
                    new LazaxStartSeason1(),
                    new LazaxGrantFavor(),
                    new LazaxHacanTradeConvoys(),
                    new LazaxMentakFalseColors(),
                    new LazaxNaaluGiftForesight())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.LAZAX;
    }

    @Override
    public String getDescription() {
        return "Lazax War Archives";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
