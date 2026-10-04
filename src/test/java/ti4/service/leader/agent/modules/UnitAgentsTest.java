package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.leader.agent.AgentOutcome;
import ti4.testUtils.BaseTi4Test;

class UnitAgentsTest extends BaseTi4Test {

    @Test
    void kortaliTakesARelicFragment() {
        AgentModuleFixture fixture = new AgentModuleFixture("kortali");
        fixture.target.addFragment("crf1");
        KortaliAgent kortali = new KortaliAgent();

        AgentOutcome outcome = kortali.resolve(fixture.use(kortali, fixture.target));

        assertThat(fixture.target.getFragments()).doesNotContain("crf1");
        assertThat(fixture.user.getFragments()).contains("crf1");
        assertThat(outcome.messages().getFirst().recipient()).isSameAs(fixture.target);
    }

    @Test
    void kortaliNoLongerCrashesWhenThereIsNothingToTake() {
        // The old code called nextInt(0) here and threw.
        AgentModuleFixture fixture = new AgentModuleFixture("kortali");
        KortaliAgent kortali = new KortaliAgent();

        AgentOutcome outcome = kortali.resolve(fixture.use(kortali, fixture.target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.text()).contains("no relic fragments");
        });
    }

    @Test
    void nokarPlacesADestroyerWithTheTargetsShips() {
        AgentModuleFixture fixture = new AgentModuleFixture("nokar");
        fixture.tile.addUnit("space", Units.getUnitKey(UnitType.Carrier, fixture.target.getColorID()), 1);
        NokarAgent nokar = new NokarAgent();

        nokar.resolve(fixture.use(nokar, fixture.target));

        assertThat(fixture.tile
                        .getUnitHolders()
                        .get("space")
                        .getUnitCount(UnitType.Destroyer, fixture.target.getColor()))
                .isEqualTo(1);
    }

    @Test
    void nokarNeedsTheTargetToHaveShipsThere() {
        AgentModuleFixture fixture = new AgentModuleFixture("nokar");
        NokarAgent nokar = new NokarAgent();

        AgentOutcome outcome = nokar.resolve(fixture.use(nokar, fixture.target));

        assertThat(fixture.tile
                        .getUnitHolders()
                        .get("space")
                        .getUnitCount(UnitType.Destroyer, fixture.target.getColor()))
                .isZero();
        assertThat(outcome.messages().getFirst().text()).contains("no destroyer placed");
    }

    @Test
    void zelianTurnsInfantryInSpaceIntoAMech() {
        AgentModuleFixture fixture = new AgentModuleFixture("zelian");
        fixture.tile.addUnit("space", Units.getUnitKey(UnitType.Infantry, fixture.target.getColorID()), 2);
        ZelianAgent zelian = new ZelianAgent();

        zelian.resolve(fixture.use(zelian, fixture.target));

        var space = fixture.tile.getUnitHolders().get("space");
        assertThat(space.getUnitCount(UnitType.Infantry, fixture.target.getColor()))
                .isEqualTo(1);
        assertThat(space.getUnitCount(UnitType.Mech, fixture.target.getColor())).isEqualTo(1);
    }

    @Test
    void zelianNeedsInfantryInSpace() {
        AgentModuleFixture fixture = new AgentModuleFixture("zelian");
        ZelianAgent zelian = new ZelianAgent();

        AgentOutcome outcome = zelian.resolve(fixture.use(zelian, fixture.target));

        assertThat(outcome.messages().getFirst().text()).contains("no mech placed");
    }
}
