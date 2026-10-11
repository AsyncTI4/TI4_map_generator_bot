package ti4.discord.interactions.commands.relic;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class RelicCommand extends AbstractParentCommand {

    public RelicCommand() {
        super(
                Constants.RELIC,
                "Relic",
                new RelicInfo(),
                new RelicDraw(),
                new RelicPurge(),
                new RelicExhaust(),
                new RelicReady(),
                new RelicDrawSpecific(),
                new RelicLookAtTop(),
                new RelicBottom(),
                new RelicSend(),
                new RelicShuffleBack(),
                new RelicShowRemaining(),
                new RelicAddBackIntoDeck(),
                new RelicAddCodexRelics(),
                new RelicSendFragments(),
                new RelicPurgeFragments(),
                new ShowPurgedFragments());
    }
}
