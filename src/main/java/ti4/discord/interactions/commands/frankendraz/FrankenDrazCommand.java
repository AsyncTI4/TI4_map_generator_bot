package ti4.discord.interactions.commands.frankendraz;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class FrankenDrazCommand extends AbstractParentCommand {

    public FrankenDrazCommand() {
        super(
                Constants.FRANKENDRAZ,
                "FrankenDraz",
                FrankenDrazFactionComponents.add(),
                FrankenDrazFactionComponents.remove(),
                new FrankenDrazSwapFaction(),
                new FrankenDrazSetKeptComponentLimit(),
                new FrankenDrazFactionLimit(),
                new FrankenDrazHelp());
    }
}
