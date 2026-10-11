package ti4.discord.interactions.commands.spin;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class SpinCommand extends AbstractParentCommand {

    public SpinCommand() {
        super(
                Constants.SPIN,
                "Spin tiles in rings",
                new ListSpinSettings(),
                new AddSpinSetting(),
                new ExecuteSpin(),
                new RemoveSpinSetting());
    }
}
