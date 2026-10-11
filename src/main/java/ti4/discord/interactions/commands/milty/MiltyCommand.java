package ti4.discord.interactions.commands.milty;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class MiltyCommand extends AbstractParentCommand {

    public MiltyCommand() {
        super(
                Constants.MILTY,
                "Milty draft",
                new DebugMilty(),
                new ForcePick(),
                new RandomSetup(),
                new SetupMilty(),
                new StartMilty(),
                new ShowMilty());
    }
}
