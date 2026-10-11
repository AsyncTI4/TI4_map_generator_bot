package ti4.discord.interactions.commands.tech;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class TechCommand extends AbstractParentCommand {

    public TechCommand() {
        super(
                Constants.TECH,
                "Add/remove/exhaust/ready/purge Technologies",
                TechAddRemove.add(),
                TechAddRemove.remove(),
                TechAddRemove.purge(),
                TechAddRemove.unpurge(),
                TechAddRemove.exhaust(),
                TechAddRemove.refresh(),
                new TechInfo(),
                new GetTechButton(),
                new TechChangeType(),
                new TechShowDeck());
    }
}
