package ti4.discord.interactions.commands.tigl;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class TiglCommand extends AbstractParentCommand {

    public TiglCommand() {
        super(
                Constants.TIGL,
                "Twilight Imperium Global League",
                new ChangeNickname(),
                new IsFractured(),
                new Games(),
                new ReportManually());
    }
}
