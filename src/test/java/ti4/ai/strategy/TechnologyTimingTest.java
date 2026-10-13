package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// In the opening the Technology holder takes planets first and plays Technology once none are left, unless playing
// it now denies the secondary to rivals who cannot follow yet but could later. A holder paid to wait holds it.
class TechnologyTimingTest extends BaseTi4Test {

    private static final int TRADE = 5;
    private static final int TECHNOLOGY = 7;
    private static final String LODOR_SPOT = "305";
    private static final String RIVAL_ID = "7100000555555555";

    private AiTestGame test;
    private Player sol;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.game.setRound(1);
        test.solHomeBeside(LODOR_SPOT);
        sol.addSC(TECHNOLOGY);
        sol.setStrategicCC(2);
        sol.setTacticalCC(3);
        test.isActive(sol, "action");
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    // A carrier with an infantry aboard sits in Lodor: landing it there is a planet still to take.
    private void planetToTake() {
        Tile lodor = test.place("26", LODOR_SPOT);
        test.units(lodor, "space", sol, UnitType.Carrier, 1);
        test.units(lodor, "space", sol, UnitType.Infantry, 1);
        changed();
    }

    // Hacan has a strategy token but no trade goods: it cannot pay for the secondary now, but its four commodities
    // can be washed into trade goods on a later turn.
    private Player rivalShortOfResources() {
        Player hacan = test.addSeat(RIVAL_ID, "hacan", "red");
        hacan.setStrategicCC(2);
        hacan.setTg(0);
        hacan.setCommodities(4);
        changed();
        return hacan;
    }

    private Optional<AiDecision> solTurn() {
        AiPrompt turn = prompt(
                "turn", PromptSource.PUBLIC, NOW, "FFCC_sol_tacticalAction", "FFCC_sol_strategicAction_" + TECHNOLOGY);
        AiTurnContext context = test.contextFor(sol, Set.of(), NOW, turn);
        return StrategyCardTurn.playIfDue(context, List.of(turn));
    }

    @Test
    void playsTechnologyOnceNoPlanetIsLeftToTake() {
        assertThat(solTurn().map(AiTestGame::pressedId)).contains("FFCC_sol_strategicAction_7");
    }

    @Test
    void takesPlanetsBeforePlayingTechnology() {
        planetToTake();

        assertThat(solTurn()).isEmpty();
    }

    // Hacan could follow once it has washed its commodities; playing Technology before that denies it the secondary,
    // which outweighs taking Lodor one turn later.
    @Test
    void playsTechnologyEarlyToDenyARivalWhoCannotFollowYet() {
        planetToTake();
        Player hacan = rivalShortOfResources();

        assertThat(TechnologyTiming.deniable(test.game, hacan)).isTrue();
        assertThat(solTurn().map(AiTestGame::pressedId)).contains("FFCC_sol_strategicAction_7");
    }

    // A rival that can pay for the secondary right now cannot be denied by playing early.
    @Test
    void doesNotRushTechnologyAgainstARivalWhoCanFollowNow() {
        planetToTake();
        Player hacan = rivalShortOfResources();
        hacan.setTg(4);
        changed();

        assertThat(TechnologyTiming.deniable(test.game, hacan)).isFalse();
        assertThat(solTurn()).isEmpty();
    }

    // With nothing left to expand to, the holder would normally play Technology now, but it took Hacan's payment to
    // wait until Hacan can follow.
    @Test
    void holdsTechnologyForTheSeatThatPaidUntilItCanFollow() {
        Player hacan = rivalShortOfResources();
        TechnologyTiming.grantHold(test.game, test.memoryOf(sol), hacan);

        assertThat(solTurn()).isEmpty();

        hacan.setTg(4);
        changed();

        assertThat(solTurn().map(AiTestGame::pressedId)).contains("FFCC_sol_strategicAction_7");
    }

    // The hold lasts only through Hacan's next turn: granted during Hacan's first turn, it still holds while Sol takes
    // its own turn, and is over once Hacan has started (and so, with Sol active again, finished) its second.
    @Test
    void holdEndsAfterThePayersNextTurn() {
        Player hacan = rivalShortOfResources();
        hacan.setInRoundTurnCount(1);
        TechnologyTiming.grantHold(test.game, test.memoryOf(sol), hacan);

        assertThat(solTurn()).isEmpty();

        hacan.setInRoundTurnCount(2);
        changed();

        assertThat(solTurn().map(AiTestGame::pressedId)).contains("FFCC_sol_strategicAction_7");
    }

    // Hacan waits on the wash Sol will settle once it plays Trade (Hacan follows Trade for free). The hold outlasts
    // Hacan's next turn while that Trade is still to come, and ends once Trade is played and nothing is left to wash.
    @Test
    void holdLastsWhileARivalTradeWashIsStillToCome() {
        Player hacan = rivalShortOfResources();
        hacan.setCommodities(0);
        hacanBesideSol(hacan);
        sol.addSC(TRADE);
        hacan.setInRoundTurnCount(1);
        TechnologyTiming.grantHold(test.game, test.memoryOf(sol), hacan);
        hacan.setInRoundTurnCount(2);
        changed();

        assertThat(TechnologyTiming.awaitsTradeWash(test.game, hacan)).isTrue();
        assertThat(TechnologyTiming.heldBack(test.game, sol, test.memoryOf(sol)))
                .isTrue();

        test.game.setSCPlayed(TRADE, true);
        changed();

        assertThat(TechnologyTiming.heldBack(test.game, sol, test.memoryOf(sol)))
                .isFalse();
    }

    // However long the wash takes, a hold never stalls the holder past the second seat to pass.
    @Test
    void holdEndsOnceTwoSeatsHavePassed() {
        Player hacan = rivalShortOfResources();
        hacanBesideSol(hacan);
        sol.addSC(TRADE);
        TechnologyTiming.grantHold(test.game, test.memoryOf(sol), hacan);
        Player third = test.addSeat("7100000666666666", "letnev", "green");
        third.setPassed(true);
        changed();

        assertThat(TechnologyTiming.heldBack(test.game, sol, test.memoryOf(sol)))
                .isTrue();

        test.nekro.setPassed(true);
        changed();

        assertThat(TechnologyTiming.heldBack(test.game, sol, test.memoryOf(sol)))
                .isFalse();
    }

    // Waiting on a Trade still to come is a longer hold: the holder charges more for it, and asks for two.
    @Test
    void aLongerHoldCostsMore() {
        Player hacan = rivalShortOfResources();
        hacan.setCommodities(0);
        double shortHold = TechnologyTiming.holdCost(test.game, sol, hacan);
        assertThat(TechnologyTiming.holdPayment(test.game, hacan)).isEqualTo(1);

        hacanBesideSol(hacan);
        sol.addSC(TRADE);
        changed();

        assertThat(TechnologyTiming.holdCost(test.game, sol, hacan)).isGreaterThan(shortHold);
        assertThat(TechnologyTiming.holdPayment(test.game, hacan)).isEqualTo(2);
    }

    private void hacanBesideSol(Player hacan) {
        Tile lodor = test.place("26", LODOR_SPOT);
        test.units(lodor, "space", hacan, UnitType.Carrier, 1);
        changed();
    }

    @Test
    void holdEndsWhenThePayerPasses() {
        Player hacan = rivalShortOfResources();
        TechnologyTiming.grantHold(test.game, test.memoryOf(sol), hacan);
        hacan.setPassed(true);
        changed();

        assertThat(TechnologyTiming.heldBack(test.game, sol, test.memoryOf(sol)))
                .isFalse();
    }

    // A seat without a strategy token can still follow later when it holds Leadership, whose primary gives tokens.
    @Test
    void countsLeadershipTokensAsALaterFollow() {
        Player hacan = rivalShortOfResources();
        hacan.setStrategicCC(0);
        hacan.setTg(4);
        changed();

        assertThat(TechnologyTiming.deniable(test.game, hacan)).isFalse();

        hacan.addSC(1);
        changed();

        assertThat(TechnologyTiming.deniable(test.game, hacan)).isTrue();
    }

    @Test
    void waitingCostsNothingWhilePlanetsAreLeftAndNobodyIsDenied() {
        planetToTake();

        assertThat(TechnologyTiming.holdCost(test.game, sol)).isZero();
    }

    // Once no planet is left the holder would play now; waiting costs it the denial it gives up, at least a little.
    @Test
    void waitingCostsTheDenialItGivesUp() {
        rivalShortOfResources();

        assertThat(TechnologyTiming.holdCost(test.game, sol))
                .isEqualTo(TechnologyTiming.denialGain(test.game, sol))
                .isGreaterThan(1.0);
    }
}
