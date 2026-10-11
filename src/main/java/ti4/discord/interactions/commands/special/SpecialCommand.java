package ti4.discord.interactions.commands.special;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class SpecialCommand extends AbstractParentCommand {

    public SpecialCommand() {
        super(
                Constants.SPECIAL,
                "Special",
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
                new CheckAllDistance(),
                new Bounties());
    }
}
