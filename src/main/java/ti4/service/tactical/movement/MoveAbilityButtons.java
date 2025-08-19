package ti4.service.tactical.movement;

import java.util.List;
import ti4.service.tactical.MoveAbilityButton;

public final class MoveAbilityButtons {
    private MoveAbilityButtons() {}

    public static final List<MoveAbilityButton> ABILITIES = List.of(
            new SaarAgentButton(),
            new BelkoseaAgentButton(),
            new QhetAgentButton(),
            new DominusOrbButton(),
            new EyeOfVogulButton(),
            new AbsolLuxarchTreatiseButton(),
            new GhostAgentButton(),
            new AetherstreamButton(),
            new EmergencyModificationsButton(),
            new RealityFieldImpactorButton(),
            new GravityDriveButton(),
            new NavigationRelaysButton(),
            new LightWaveDeflectorButton(),
            new LightningDrivesButton(),
            new MidasTurbineButton(),
            new VaylerianCommanderButton(),
            new VaylerianHeroButton(),
            new UydaiHeroButton(),
            new GhostMechButton(),
            new NivynMechButton(),
            new WraithEngineButton(),
            new EkoPlanetAbilityButton());
}
