package ti4.commands.game;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.commands.special.AddFactionCCToFleetSupply;
import ti4.commands.special.AdjustRoundNumber;
import ti4.commands.special.CheckAllDistance;
import ti4.commands.special.CheckDistance;
import ti4.commands.special.CloneGame;
import ti4.commands.special.DiploSystem;
import ti4.commands.special.FighterConscription;
import ti4.commands.special.IonFlip;
import ti4.commands.special.MakeSecretIntoPO;
import ti4.commands.special.MoveAllUnits;
import ti4.commands.special.MoveCreussWormhole;
import ti4.commands.special.NaaluCommander;
import ti4.commands.special.NovaSeed;
import ti4.commands.special.Rematch;
import ti4.commands.special.RemoveFactionCCFromFleetSupply;
import ti4.commands.special.RiseOfMessiah;
import ti4.commands.special.SearchWarrant;
import ti4.commands.special.SleeperToken;
import ti4.commands.special.StasisInfantry;
import ti4.commands.special.SwapSC;
import ti4.commands.special.SwapTwoSystems;
import ti4.commands.special.SwordsToPlowsharesTGGain;
import ti4.commands.special.WormholeResearchFor;
import ti4.helpers.Constants;

public class GameCommand implements ParentCommand {

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
            new RiseOfMessiah(),
            new Rematch(),
            new CloneGame(),
            new SwordsToPlowsharesTGGain(),
            new WormholeResearchFor(),
            new FighterConscription(),
            new SwapSC(),
            new MoveAllUnits(),
            new NovaSeed(),
            new StasisInfantry(),
            new NaaluCommander(),
            new MoveCreussWormhole(),
            new CheckDistance(),
            new CheckAllDistance()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));


    @Override
    public String getName() {
        return Constants.GAME;
    }

    @Override
    public String getDescription() {
        return "Game";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
