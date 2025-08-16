package ti4.service.tactical;

import ti4.service.tactical.planet.DihmohnAgentPlanetButton;
import ti4.service.tactical.planet.TnelisDeployButton;

import java.util.List;

public final class PlanetAbilityButtons {
    private PlanetAbilityButtons() {}

    public static final List<PlanetAbilityButton> ABILITIES = List.of(
            new DihmohnAgentPlanetButton(),
            new TnelisDeployButton());
}


