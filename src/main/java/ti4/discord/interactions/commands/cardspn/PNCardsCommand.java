package ti4.discord.interactions.commands.cardspn;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class PNCardsCommand extends AbstractParentCommand {

    public PNCardsCommand() {
        super(
                Constants.CARDS_PN,
                "Promissory Notes",
                new ShowPN(),
                new ShowAllPN(),
                new ShowPNToAll(),
                new PlayPN(),
                new SendPN(),
                new PurgePN(),
                new PNInfo(),
                new SendRandomPN(),
                new PNReset());
    }
}
