package ti4.discord.interactions.commands.planet;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class PlanetCommand extends AbstractParentCommand {

    public PlanetCommand() {
        super(
                Constants.PLANET,
                "Add/remove/exhaust/ready/spend planets",
                new PlanetAdd(),
                new PlanetRemove(),
                new PlanetExhaust(),
                new PlanetRefresh(),
                new PlanetExhaustAbility(),
                new PlanetRefreshAbility(),
                new PlanetRefreshAll(),
                new PlanetExhaustAll(),
                new PlanetInfo());
    }
}
