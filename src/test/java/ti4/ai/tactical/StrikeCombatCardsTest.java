package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiSettings;
import ti4.ai.actioncards.CombatHand;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The strike estimate counts the combat action cards the deciding seat holds: Morale Boost and Fighter Prototype
// boost the first round, Shields Holding cancels two hits, Direct Hit strips a sustain and Maneuvering Jets cancels a
// space cannon hit. A rival's hand stays hidden, so estimating someone else's strike ignores it.
class StrikeCombatCardsTest extends BaseTi4Test {

    private static final String HACAN_ID = "200000000000000003";
    private static final int HAND_INDEX = 1;

    @AfterEach
    void restoreCombatCards() {
        System.clearProperty(AiSettings.COMBAT_CARDS_PROPERTY);
    }

    // Two dreadnoughts and a carrier with three infantry against Styx's two neutral dreadnoughts and destroyer: a
    // close fight, where a card moves the odds.
    private static Strike styxStrike(FractureScenario scenario) {
        Tile beside = scenario.fracture("frac3");
        scenario.ships(beside, UnitType.Dreadnought, 2);
        scenario.ships(beside, UnitType.Carrier, 1);
        scenario.board.test.units(beside, BoardView.SPACE, scenario.nekro(), UnitType.Infantry, 3);
        List<UnitMove> moves = List.of(
                new UnitMove(beside.getPosition(), BoardView.SPACE, UnitType.Dreadnought, 2),
                new UnitMove(beside.getPosition(), BoardView.SPACE, UnitType.Carrier, 1),
                new UnitMove(beside.getPosition(), BoardView.SPACE, UnitType.Infantry, 3));
        return Strike.of(
                scenario.nekro(),
                scenario.fracture(FractureScenario.STYX),
                scenario.board.test.game.getNeutral(),
                List.of(beside),
                moves,
                StyxTake.RULES);
    }

    private static double spaceWinHolding(String... aliases) {
        FractureScenario scenario = new FractureScenario();
        Strike strike = styxStrike(scenario);
        for (String alias : aliases) scenario.nekro().setActionCard(alias, HAND_INDEX);
        return decided(scenario, strike, scenario.nekro());
    }

    private static double decided(FractureScenario scenario, Strike strike, Player decider) {
        return DecidingSeat.of(
                decider,
                () -> StrikeEvaluator.estimate(scenario.board.test.game, scenario.nekro(), strike, decider)
                        .spaceWin());
    }

    // Outside a decision nobody knows the hand, so a threat estimate made for another seat never counts its cards.
    @Test
    void outsideADecisionTheHandIsIgnored() {
        FractureScenario scenario = new FractureScenario();
        Strike strike = styxStrike(scenario);
        scenario.nekro().setActionCard("sh1", HAND_INDEX);

        assertThat(StrikeEvaluator.estimate(scenario.board.test.game, scenario.nekro(), strike)
                        .spaceWin())
                .isCloseTo(spaceWinHolding(), within(1e-12));
    }

    @Test
    void withoutCardsTheFightIsClose() {
        assertThat(spaceWinHolding()).isBetween(0.2, 0.8);
    }

    @Test
    void moraleBoostRaisesTheSpaceWin() {
        assertThat(spaceWinHolding("mb1")).isGreaterThan(spaceWinHolding() + 0.02);
    }

    @Test
    void shieldsHoldingRaisesTheSpaceWinMoreThanMoraleBoost() {
        assertThat(spaceWinHolding("sh1")).isGreaterThan(spaceWinHolding("mb1"));
    }

    // The neutral fields Dreadnought IIs, which Direct Hit cannot destroy, so the card adds nothing at Styx.
    @Test
    void directHitCannotTouchTheNeutralsUpgradedDreadnoughts() {
        FractureScenario scenario = new FractureScenario();
        Player neutral = scenario.board.test.game.getNeutral();

        assertThat(BoardView.model(neutral, UnitType.Dreadnought))
                .hasValueSatisfying(
                        model -> assertThat(model.getCanBeDirectHit()).isFalse());
        assertThat(spaceWinHolding("dh1")).isCloseTo(spaceWinHolding(), within(1e-12));
    }

    @Test
    void directHitStripsTheSustainOfTheDearestShipThatCanBeHit() {
        FractureScenario scenario = new FractureScenario();
        Player hacan = scenario.board.test.addSeat(HACAN_ID, "hacan", "yellow");
        var dreadnought = new ti4.ai.eval.CombatOdds.Combatant(5, 1, true, 4, UnitType.Dreadnought);
        var carrier = new ti4.ai.eval.CombatOdds.Combatant(9, 1, false, 3, UnitType.Carrier);
        CombatHand directHit = new CombatHand(0, 0, 0, 1, false);

        var struck = directHit.directHit(hacan, List.of(carrier, dreadnought, dreadnought));

        assertThat(struck).extracting(unit -> unit.sustain()).containsExactly(false, false, true);
    }

    @Test
    void cardsThatDoNotFightAreIgnored() {
        assertThat(spaceWinHolding("sabo1")).isCloseTo(spaceWinHolding(), within(1e-12));
    }

    @Test
    void aRivalEstimatingTheStrikeDoesNotSeeTheHand() {
        FractureScenario scenario = new FractureScenario();
        Player hacan = scenario.board.test.addSeat(HACAN_ID, "hacan", "yellow");
        Strike strike = styxStrike(scenario);
        scenario.nekro().setActionCard("sh1", HAND_INDEX);

        double seen = decided(scenario, strike, scenario.nekro());
        double hidden = decided(scenario, strike, hacan);

        assertThat(hidden).isCloseTo(spaceWinHolding(), within(1e-12));
        assertThat(seen).isGreaterThan(hidden);
    }

    @Test
    void switchingCombatCardsOffIgnoresTheHand() {
        double plain = spaceWinHolding();
        System.setProperty(AiSettings.COMBAT_CARDS_PROPERTY, "false");

        assertThat(spaceWinHolding("sh1")).isCloseTo(plain, within(1e-12));
    }

    @Test
    void maneuveringJetsCancelsOneCannonHit() {
        CombatHand jets = new CombatHand(0, 0, 1, 0, false);

        double[] cancelled = jets.cancellingCannon(new double[] {0.25, 0.5, 0.25});

        assertThat(cancelled).containsExactly(0.75, 0.25);
        assertThat(jets.cancelsAny(new double[] {1.0})).isFalse();
        assertThat(jets.afterJets().cancelsAny(new double[] {0.5, 0.5})).isFalse();
    }

    @Test
    void fighterPrototypeBeatsMoraleBoostOnlyForAFighterSwarm() {
        var fighter = new ti4.ai.eval.CombatOdds.Combatant(9, 1, false, 0.5, UnitType.Fighter);
        var dreadnought = new ti4.ai.eval.CombatOdds.Combatant(5, 1, true, 4, UnitType.Dreadnought);
        CombatHand both = new CombatHand(1, 0, 0, 0, true);

        var swarm = both.firstSpaceRound(List.of(fighter, fighter, fighter, dreadnought));
        var battleline = both.firstSpaceRound(List.of(fighter, dreadnought, dreadnought));

        assertThat(swarm).extracting(unit -> unit.hitsOn()).containsExactly(7, 7, 7, 5);
        assertThat(battleline).extracting(unit -> unit.hitsOn()).containsExactly(8, 4, 4);
    }
}
