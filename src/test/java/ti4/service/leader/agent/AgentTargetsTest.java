package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class AgentTargetsTest extends BaseTi4Test {

    @Test
    void aFactionContainingTheSeparatorIsReadWhole() {
        AgentModuleFixture fixture = new AgentModuleFixture("naaz");

        assertThat(AgentTargets.player(fixture.game, AgentModuleFixture.TARGET_FACTION))
                .contains(fixture.target);
        // A trailing payload segment after the faction is ignored.
        assertThat(AgentTargets.player(fixture.game, "blue_extra")).contains(fixture.target);
    }

    @Test
    void anEmptyPayloadMeansTheUserOnlyWhenTheTargetIsOptional() {
        AgentModuleFixture fixture = new AgentModuleFixture("naaz");

        assertThat(AgentTargets.player(fixture.game, "")).isEmpty();
        assertThat(AgentTargets.playerOrUser(fixture.game, fixture.user, "")).contains(fixture.user);
        assertThat(AgentTargets.playerOrUser(fixture.game, fixture.user, "nobody"))
                .isEmpty();
    }

    @Test
    void aPlanetInSystemNeedsTheTileAndThePlanetOnIt() {
        AgentModuleFixture fixture = new AgentModuleFixture("naaz");
        String active = AgentModuleFixture.ACTIVE_SYSTEM;

        assertThat(AgentTargets.planetInSystem(fixture.game, active + "_lodor"))
                .contains(new AgentTargets.PlanetInSystem(fixture.tile, "lodor"));
        // Lodor is not in system 999, and Mecatol Rex is not on tile 26.
        assertThat(AgentTargets.planetInSystem(fixture.game, "999_lodor")).isEmpty();
        assertThat(AgentTargets.planetInSystem(fixture.game, active + "_mr")).isEmpty();
        assertThat(AgentTargets.planetInSystem(fixture.game, active)).isEmpty();
    }

    @Test
    void aPlayerOrPlanetPayloadPrefersAWholePlayerThenAPlanet() {
        AgentModuleFixture fixture = new AgentModuleFixture("naaz");

        assertThat(playerOrPlanet(fixture, "")).contains("player " + fixture.user.getFaction());
        // "pi_hacan" would read as position "pi", planet "hacan" if the planet were tried first.
        assertThat(playerOrPlanet(fixture, AgentModuleFixture.TARGET_FACTION))
                .contains("player " + AgentModuleFixture.TARGET_FACTION);
        assertThat(playerOrPlanet(fixture, AgentModuleFixture.ACTIVE_SYSTEM + "_lodor"))
                .contains("planet lodor");
        assertThat(playerOrPlanet(fixture, "blue_extra")).contains("player " + AgentModuleFixture.TARGET_FACTION);
        assertThat(playerOrPlanet(fixture, "999_lodor")).isEmpty();
    }

    private static Optional<String> playerOrPlanet(AgentModuleFixture fixture, String payload) {
        return AgentTargets.playerOrPlanetInSystem(
                fixture.game,
                fixture.user,
                payload,
                player -> "player " + player.getFaction(),
                planet -> "planet " + planet.planet());
    }
}
