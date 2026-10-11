package ti4.discord.interactions.commands.user;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class UserCommand extends AbstractParentCommand {

    public UserCommand() {
        super(
                Constants.USER,
                "User",
                new ShowUserSettings(),
                new SetPreferredColourList(),
                new SetPersonalPingInterval(),
                new SetPingOnNextTurn(),
                new OfferAFKTimeOptions(),
                new Survey(),
                new MessageMyGames(),
                new SetPreferredSettings(),
                new WipeTurnTime(),
                new SetFowFilter(),
                new SetFogGhostHexes());
    }
}
