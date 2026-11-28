package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.StrategyCard;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class SpendingTrackerTest extends BaseTi4Test {

    private static final String XXCHA = "xxcha";

    private AiTestGame test;
    private Player xxcha;
    private Tile lodorSystem;
    private SpendingTracker tracker;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat("300000000000000003", XXCHA, "green");
        lodorSystem = test.place("26", "101");
        test.place("27", "102");
        xxcha.addPlanet("lodor");
        xxcha.addPlanet("starpoint");
        xxcha.setTacticalCC(3);
        xxcha.setFleetCC(3);
        xxcha.setStrategicCC(2);
        test.isActive(xxcha, "action");
        tracker = new SpendingTracker();
    }

    private int initiativeOf(StrategyCard card) {
        for (int initiative = 1; initiative <= 8; initiative++) {
            if (StrategyCard.of(test.game, initiative) == card) return initiative;
        }
        throw new AssertionError("no " + card);
    }

    private Planet planet(String name) {
        Planet planet = test.game.getPlanetsInfo().get(name);
        assertThat(planet).as(name).isNotNull();
        return planet;
    }

    // Each harness tick makes at most one press, and the game is observed between ticks.
    private void press(Player seat, String customId) {
        tracker.pressed(test.game.getRound(), seat.getFaction(), customId, "PRESSED");
        tracker.observe(test.game);
    }

    private void exhaust(Player seat, String planet) {
        seat.getExhaustedPlanets().add(planet);
    }

    private void ready(Player seat, String planet) {
        seat.getExhaustedPlanets().remove(planet);
    }

    private int count(String key) {
        return tracker.count(XXCHA, key);
    }

    @Test
    void aLeadershipSecondaryBuysEveryTokenItGainsAndCountsTheInfluenceLeftOver() {
        int leadership = initiativeOf(StrategyCard.LEADERSHIP);
        test.sol.addSC(leadership);
        test.game.setSCPlayed(leadership, true);
        test.isActive(test.sol, "action");
        tracker.observe(test.game);

        press(xxcha, "leadershipGenerateCCButtons");
        exhaust(xxcha, "lodor");
        press(xxcha, "spend_lodor_inf");
        exhaust(xxcha, "starpoint");
        press(xxcha, "spend_starpoint_inf");
        xxcha.setTacticalCC(4);
        press(xxcha, "FFCC_xxcha_increase_tactic_cc");
        press(xxcha, "deleteButtons_leadership");
        press(xxcha, "FFCC_xxcha_deleteButtons_leadership");

        int influence = planet("lodor").getInfluence() + planet("starpoint").getInfluence();
        assertThat(count("leadership.secondary.uses")).isEqualTo(1);
        assertThat(count("leadership.secondary.gained")).isEqualTo(1);
        assertThat(count("leadership.secondary.bought")).isEqualTo(1);
        assertThat(count("leadership.secondary.influence")).isEqualTo(influence);
        assertThat(count("leadership.secondary.wasted")).isEqualTo(Math.max(0, influence - 3));
        assertThat(count("leadership.primary.uses")).isZero();
    }

    // The primary's first 3 tokens are free (LRR 52.2); only the fourth was bought.
    @Test
    void aLeadershipPrimaryDoesNotCountItsFreeTokensAsBought() {
        int leadership = initiativeOf(StrategyCard.LEADERSHIP);
        xxcha.addSC(leadership);
        test.game.setSCPlayed(leadership, true);
        tracker.observe(test.game);

        press(xxcha, "leadershipGenerateCCButtons");
        xxcha.setTacticalCC(5);
        xxcha.setFleetCC(5);
        press(xxcha, "FFCC_xxcha_increase_fleet_cc");
        press(xxcha, "FFCC_xxcha_deleteButtons_leadership");
        // The seat moves on without pressing "Done Exhausting Planets"; that closes the window too.
        press(xxcha, "FFCC_xxcha_tacticalAction");

        assertThat(count("leadership.primary.uses")).isEqualTo(1);
        assertThat(count("leadership.primary.gained")).isEqualTo(4);
        assertThat(count("leadership.primary.bought")).isEqualTo(1);
    }

    @Test
    void aPlanetReadiedByGgrocutoRinnAndExhaustedAgainIsRespent() {
        exhaust(xxcha, "lodor");
        tracker.observe(test.game);

        xxcha.getLeader("xxchaagent").orElseThrow().setExhausted(true);
        press(xxcha, "exhaustAgent_xxchaagent_xxcha");
        ready(xxcha, "lodor");
        press(xxcha, "FFCC_xxcha_khraskHeroStep4Ready_xxcha_lodor");
        exhaust(xxcha, "lodor");
        press(xxcha, "spend_lodor_res");

        assertThat(count("agent.exhausts")).isEqualTo(1);
        assertThat(count("readies.xxchaagent")).isEqualTo(1);
        assertThat(count("respent.xxchaagent")).isEqualTo(1);
        assertThat(count("readies.other")).isZero();
    }

    // khraskHeroStep4Ready_ is also posted by Reparations; without an exhausted agent it is not Ggrocuto Rinn.
    @Test
    void theSharedReadyHandlerWithoutAnAgentUseIsNotCreditedToTheAgent() {
        exhaust(xxcha, "lodor");
        tracker.observe(test.game);

        ready(xxcha, "lodor");
        press(xxcha, "FFCC_xxcha_khraskHeroStep4Ready_xxcha_lodor");

        assertThat(count("readies.xxchaagent")).isZero();
        assertThat(count("readies.other")).isEqualTo(1);
    }

    @Test
    void ggrocutoRinnOnAnotherSeatsPlanetIsCreditedToXxcha() {
        xxcha.getPlanets().remove("lodor");
        test.sol.addPlanet("lodor");
        exhaust(test.sol, "lodor");
        tracker.observe(test.game);

        xxcha.getLeader("xxchaagent").orElseThrow().setExhausted(true);
        press(xxcha, "exhaustAgent_xxchaagent_xxcha");
        ready(test.sol, "lodor");
        press(xxcha, "FFCC_xxcha_khraskHeroStep4Ready_sol_lodor");

        assertThat(count("readies.xxchaagent")).isEqualTo(1);
        assertThat(count("readies.xxchaagent.others")).isEqualTo(1);
        assertThat(tracker.count("sol", "readies.other")).isZero();
    }

    // The status phase readies every planet; none of that is a readying effect.
    @Test
    void doesNotCountPlanetsReadiedOutsideTheActionPhase() {
        exhaust(xxcha, "lodor");
        tracker.observe(test.game);

        test.game.setPhaseOfGame("status");
        ready(xxcha, "lodor");
        tracker.observe(test.game);

        assertThat(count("readies.other")).isZero();
        assertThat(count("readies.xxchaagent")).isZero();
    }

    @Test
    void aResetSpendIsNotAReady() {
        exhaust(xxcha, "lodor");
        tracker.observe(test.game);

        ready(xxcha, "lodor");
        press(xxcha, "resetSpend_res");

        assertThat(count("readies.other")).isZero();
    }

    @Test
    void aConstructionFollowThatSpentATokenPlacesAForwardDock() {
        int construction = initiativeOf(StrategyCard.CONSTRUCTION);
        test.sol.addSC(construction);
        test.game.setSCPlayed(construction, true);
        tracker.observe(test.game);

        xxcha.setStrategicCC(1);
        press(xxcha, "construction_sd");
        test.units(lodorSystem, "lodor", xxcha, UnitType.Spacedock, 1);
        press(xxcha, "FFCC_xxcha_placeOneNDone_skipbuild_sd_lodor");

        assertThat(count("construction.follow.uses")).isEqualTo(1);
        assertThat(count("construction.follow.tokens")).isEqualTo(1);
        assertThat(count("docks.forward.follow")).isEqualTo(1);
        assertThat(count("docks.home.follow")).isZero();
    }

    @Test
    void aConstructionFollowPressWithoutATokenIsCountedApart() {
        int construction = initiativeOf(StrategyCard.CONSTRUCTION);
        test.sol.addSC(construction);
        test.game.setSCPlayed(construction, true);
        tracker.observe(test.game);

        press(xxcha, "construction_pds");

        assertThat(count("construction.follow.uses")).isZero();
        assertThat(count("construction.follow.withoutToken")).isEqualTo(1);
    }

    // Under Archon's Gift a planet pays either kind, so it is worth its larger value, once.
    @Test
    void poolsAPlanetUnderArchonsGiftAtItsLargerValue() {
        xxcha.addBreakthrough("xxchabt");
        xxcha.setBreakthroughUnlocked("xxchabt", true);
        tracker.observe(test.game);

        exhaust(xxcha, "lodor");
        press(xxcha, "spend_lodor_res");

        Planet lodor = planet("lodor");
        SpendingTracker.Spend spent = tracker.spent(XXCHA, test.game.getRound());
        assertThat(spent.resources()).isEqualTo(lodor.getResources());
        assertThat(spent.influence()).isEqualTo(lodor.getInfluence());
        assertThat(spent.pooled()).isEqualTo(Math.max(lodor.getResources(), lodor.getInfluence()));
    }

    @Test
    void printsOneLinePerSeat() {
        tracker.observe(test.game);

        assertThat(tracker.summary("test").lines())
                .hasSize(test.game.getRealPlayers().size())
                .anySatisfy(line -> assertThat(line).startsWith("xxcha r2: leadership primary 0"));
    }
}
