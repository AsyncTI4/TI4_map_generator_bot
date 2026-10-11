package ti4.discord.interactions.commands.installation;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class InstallationCommand extends AbstractParentCommand {

    public InstallationCommand() {
        super(Constants.INSTALLATION, "Installations", new AddSweepToken(), new RemoveSweepToken());
    }
}
