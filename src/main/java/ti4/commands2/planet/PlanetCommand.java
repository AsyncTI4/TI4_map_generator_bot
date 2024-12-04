package ti4.commands2.planet;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class PlanetCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new PlanetAdd(),
            new PlanetRemove(),
            new PlanetExhaust(),
            new PlanetRefresh(),
            new PlanetExhaustAbility(),
            new PlanetRefreshAbility(),
            new PlanetRefreshAll(),
            new PlanetExhaustAll(),
            new PlanetInfo()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.PLANET;
    }

    @Override
    public String getDescription() {
        return "Add/remove/exhaust/ready/spend planets";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
