package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.AgentModuleFixture;
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
    void kortaliIsNotUsableWhenThereIsNothingToTake() {
        // The old code called nextInt(0) here and threw; now the agent is not exhausted at all.
        AgentModuleFixture fixture = new AgentModuleFixture("kortali");
        KortaliAgent kortali = new KortaliAgent();

        assertThat(kortali.unusableReason(fixture.game, fixture.user, fixture.target))
                .hasValueSatisfying(reason -> assertThat(reason).contains("no relic fragments"));
        assertThat(AgentLifecycle.use(fixture.game, fixture.user, "kortaliagent", "pi_hacan", null))
                .isFalse();
        assertThat(fixture.user.getLeader("kortaliagent").orElseThrow().isExhausted())
                .isFalse();
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
    void nokarIsNotUsableWithoutTheTargetsShipsThere() {
        AgentModuleFixture fixture = new AgentModuleFixture("nokar");
        NokarAgent nokar = new NokarAgent();

        assertThat(nokar.unusableReason(fixture.game, fixture.user, fixture.target))
                .hasValueSatisfying(reason -> assertThat(reason).contains("no ships"));
    }

    @Test
    void nokarIsNotUsableWithoutAnActiveSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("nokar");
        fixture.game.setActiveSystem("");
        NokarAgent nokar = new NokarAgent();

        assertThat(nokar.unusableReason(fixture.game, fixture.user, fixture.target))
                .hasValueSatisfying(reason -> assertThat(reason).contains("active system"));
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
    void zelianIsNotUsableWithoutInfantryInSpace() {
        AgentModuleFixture fixture = new AgentModuleFixture("zelian");
        ZelianAgent zelian = new ZelianAgent();

        assertThat(zelian.unusableReason(fixture.game, fixture.user, fixture.target))
                .hasValueSatisfying(reason -> assertThat(reason).contains("no infantry"));
    }

    @Test
    void zelianUsedOnYourselfOffersLandingButtonsInANewMessage() {
        // Editing the pressed message would be undone by the pressed-button cleanup that runs afterwards.
        AgentModuleFixture fixture = new AgentModuleFixture("zelian");
        fixture.tile.addUnit("space", Units.getUnitKey(UnitType.Infantry, fixture.user.getColorID()), 1);
        ZelianAgent zelian = new ZelianAgent();

        AgentOutcome outcome = zelian.resolve(fixture.use(zelian, fixture.user));

        assertThat(outcome.messages())
                .anySatisfy(message -> assertThat(message.text()).contains("land troops"));
    }

    @Test
    void zelianUsedOnSomeoneElseOffersNoLandingButtons() {
        AgentModuleFixture fixture = new AgentModuleFixture("zelian");
        fixture.tile.addUnit("space", Units.getUnitKey(UnitType.Infantry, fixture.target.getColorID()), 1);
        ZelianAgent zelian = new ZelianAgent();

        AgentOutcome outcome = zelian.resolve(fixture.use(zelian, fixture.target));

        assertThat(outcome.messages())
                .noneSatisfy(message -> assertThat(message.text()).contains("land troops"));
    }
}
