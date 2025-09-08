package ti4.commands.special;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class SpecialCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new AddFactionCCToFleetSupply(),
                    new RemoveFactionCCFromFleetSupply(),
                    new DiploSystem(),
                    new MakeSecretIntoPO(),
                    new AdjustRoundNumber(),
                    new SwapTwoSystems(),
                    new SearchWarrant(),
                    new SleeperToken(),
                    new IonFlip(),
                    new SystemInfo(),
                    new StellarConverter(),
                    new RiseOfMessiah(),
                    new Rematch(),
                    new CloneGame(),
                    // new SwordsToPlowsharesTGGain(),
                    // new WormholeResearchFor(),
                    new FighterConscription(),
                    new SwapSC(),
                    new MoveAllUnits(),
                    new NovaSeed(),
                    new StasisInfantry(),
                    new NaaluCommander(),
                    new MoveCreussWormhole(),
                    new MoveCoatl(),
                    new CheckDistance(),
                    new CheckAllDistance())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.SPECIAL;
    }

    @Override
    public String getDescription() {
        return "Special";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
