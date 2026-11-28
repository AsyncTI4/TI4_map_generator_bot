package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class FactionComponentTrackerTest extends BaseTi4Test {

    private AiTestGame test;
    private Player xxcha;
    private FactionComponentTracker tracker;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat("300000000000000003", "xxcha", "green");
        test.addSeat("300000000000000004", "sardakk", "red");
        tracker = new FactionComponentTracker();
        tracker.observe(test.game, null);
    }

    private FactionComponentTracker.Usage usage(String key) {
        return tracker.summary("test").usages().get(key);
    }

    private static FactionComponentTracker.Component ability(String faction, String id, String name) {
        return new FactionComponentTracker.Component(faction, "ability", id, name);
    }

    // The engine's camelCase handler peaceAccords_ never contained the ability id peace_accords, so every Peace Accords
    // claim read as unused.
    @Test
    void matchesACamelCaseHandlerAgainstTheIdWithoutUnderscores() {
        assertThat(FactionComponentTracker.matchesId(
                        ability("xxcha", "peace_accords", "Peace Accords"), "FFCC_xxcha_peaceAccords_tempesta"))
                .isTrue();
        assertThat(FactionComponentTracker.matchesId(
                        ability("sol", "orbital_drop", "Orbital Drop"),
                        "FFCC_sol_componentActionRes_ability_orbitalDrop"))
                .isTrue();
    }

    @Test
    void matchesOnlyWholeTokensSoAShortIdDoesNotHideInsideAnotherHandler() {
        var instinctTraining = new FactionComponentTracker.Component("xxcha", "tech", "it", "Instinct Training");
        assertThat(FactionComponentTracker.matchesId(instinctTraining, "FFCC_xxcha_quitTurn"))
                .isFalse();
        assertThat(FactionComponentTracker.matchesId(
                        ability("xxcha", "peace_accords", "Peace Accords"), "FFCC_xxcha_peaceAccordsExtra_tempesta"))
                .isFalse();
    }

    @Test
    void matchesAHandlerAliasForAComponentWhoseButtonNeverNamesIt() {
        var mindsieve = new FactionComponentTracker.Component("naalu", "breakthrough", "naalubt", "Mindsieve");
        assertThat(FactionComponentTracker.matchesId(mindsieve, "FFCC_naalu_mindsieveFollow_3"))
                .isTrue();
    }

    @Test
    void countsAPeaceAccordsPress() {
        tracker.pressed(2, "xxcha", "FFCC_xxcha_peaceAccords_tempesta", "PRESSED");

        assertThat(usage("xxcha|ability|peace_accords").pressed).isEqualTo(1);
    }

    // "skip the space combat with Sh'val" is how SardakkHeroRules names Sh'val, Harbinger.
    @Test
    void countsAReasonThatUsesALeadersShortName() {
        tracker.decided(5, "sardakk", "skip the space combat with Sh'val");

        assertThat(usage("sardakk|hero|sardakkhero").decided).isEqualTo(1);
    }

    @Test
    void countsResearchApartFromUse() {
        tracker.pressed(3, "xxcha", "FFCC_xxcha_getTech_it", "PRESSED");

        var instinctTraining = usage("xxcha|tech|it");
        assertThat(instinctTraining.researched).isEqualTo(1);
        assertThat(instinctTraining.pressed).isZero();
        assertThat(instinctTraining.used()).isFalse();
    }

    @Test
    void marksCommandersAndWindowlessAbilitiesPassive() {
        assertThat(usage("xxcha|commander|xxchacommander").component.passive()).isTrue();
        assertThat(usage("xxcha|ability|peace_accords").component.passive()).isFalse();
    }

    // Ggrocuto Rinn is exhausted one press before the planet is readied; only a ready press after that exhaust follows
    // it through. The same handler also comes from Reparations, which is not the agent.
    @Test
    void countsTheAgentsPlanetReadyOnlyAfterItsExhaust() {
        tracker.pressed(2, "xxcha", "FFCC_xxcha_khraskHeroStep4Ready_xxcha_lodor", "PRESSED");
        assertThat(usage("xxcha|agent|xxchaagent").followThrough).isZero();

        xxcha.getLeader("xxchaagent").orElseThrow().setExhausted(true);
        tracker.observe(test.game, null);
        tracker.pressed(2, "xxcha", "FFCC_xxcha_khraskHeroStep4Ready_xxcha_lodor", "PRESSED");
        tracker.pressed(2, "xxcha", "FFCC_xxcha_khraskHeroStep4Ready_xxcha_lodor", "PRESSED");

        var agent = usage("xxcha|agent|xxchaagent");
        assertThat(agent.state).isEqualTo(1);
        assertThat(agent.followThrough).isEqualTo(1);
    }

    // Sol's infantry are Spec Ops I; landing more of them is a use of the faction unit, not only of its flagship/mech.
    @Test
    void countsFactionUnitsOtherThanFlagshipAndMechBeingPlaced() {
        Tile tile = test.place("26", "101");
        test.units(tile, "lodor", test.sol, UnitType.Infantry, 2);

        tracker.observe(test.game, null);

        var specOps = tracker.summary("test").usages().values().stream()
                .filter(usage ->
                        "sol".equals(usage.component.faction()) && "unit:infantry".equals(usage.component.kind()))
                .findFirst()
                .orElseThrow();
        assertThat(specOps.state).isEqualTo(1);
        assertThat(specOps.used()).isTrue();
    }
}
