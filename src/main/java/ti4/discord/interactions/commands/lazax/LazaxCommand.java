package ti4.discord.interactions.commands.lazax;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class LazaxCommand extends AbstractParentCommand {

    public LazaxCommand() {
        super(Constants.LAZAX, "Lazax War Archives", new LazaxMyPoints(), new LazaxTop100());
    }
}
