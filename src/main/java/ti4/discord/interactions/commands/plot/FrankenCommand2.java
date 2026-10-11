package ti4.discord.interactions.commands.plot;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class FrankenCommand2 extends AbstractParentCommand {

    public FrankenCommand2() {
        super(Constants.FRANKEN2, "Overflow Franken commands", new PlotAdd(), new PlotRemove());
    }
}
