package ti4.service.tactical;

import java.util.List;
import ti4.service.tactical.planet.DihmohnAgentPlanetButton;
import ti4.service.tactical.planet.TnelisDeployButton;

public final class PlanetAbilityButtons {
    private PlanetAbilityButtons() {}

    public static final List<PlanetAbilityButton> ABILITIES =
            List.of(new DihmohnAgentPlanetButton(), new TnelisDeployButton());
}
