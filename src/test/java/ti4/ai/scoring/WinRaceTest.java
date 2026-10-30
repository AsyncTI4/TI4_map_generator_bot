package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Self-play showed seats passing with tokens saved for a status-phase spend objective while another seat was certain,
// on public information, to reach 10 points first (twice the game ended in that very action phase). Saving tokens to
// score does not help once someone else wins before you can score, so the reserve is dropped for those seats.
class WinRaceTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int DIPLOMACY = 2;
    private static final int TRADE = 5;
    private static final int IMPERIAL = 8;

    private AiTestGame test;
    private int awards;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setRound(5);
        test.aiIsActive("action");
        test.nekroHome();
        test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME));
        for (int card = 1; card <= IMPERIAL; card++) test.game.setScTradeGood(card, 0);
        test.game.getRevealedPublicObjectives().put("galvanize", 1);
        tokens(test.nekro, 3, 3);
        points(test.nekro, 4);
        test.nekro.addSC(TRADE);
    }

    private void points(Player player, int points) {
        test.game.scorePublicObjective(player.getUserID(), test.game.addCustomPO("Test points " + (++awards), points));
    }

    private static void tokens(Player player, int tactic, int strategy) {
        player.setTacticalCC(tactic);
        player.setStrategicCC(strategy);
    }

    // The user's reference case: the Leadership holder has 3 tokens and gains at least 3 more from its primary
    // (LRR 52.2), so it can spend 6 on Galvanize the People (2 points) at status. At 8 points, first in initiative
    // order (LRR 81.1) and holding its home planets (LRR 61.16), it reaches 10 before Nekro can score.
    @Test
    void theLeadershipHolderAtEightPointsWinsBeforeTheSeatCanScore() {
        test.sol.addSC(LEADERSHIP);
        tokens(test.sol, 2, 1);
        points(test.sol, 8);

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).containsExactly(test.sol);
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).isEmpty();
    }

    // With Leadership already played the 3 tokens stay 3: Sol cannot reach Galvanize, nobody wins first, and Nekro
    // keeps
    // its 6 tokens for the objective.
    @Test
    void withoutTheLeadershipTokensNobodyWinsFirst() {
        test.sol.addSC(LEADERSHIP);
        test.game.setSCPlayed(LEADERSHIP, true);
        tokens(test.sol, 2, 1);
        points(test.sol, 8);

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).isEmpty();
        assertThat(ScoringReserve.reserved(test.game, test.nekro))
                .hasValueSatisfying(
                        reserved -> assertThat(reserved.objectiveId()).isEqualTo("galvanize"));
        // Two points at 8 trade goods each (6 plus 2 while a contender sits at 8): only a plan worth more than 16
        // outweighs the reserve.
        assertThat(ScoringReserve.reservedWorth(test.game, test.nekro)).isCloseTo(16, within(1e-9));
    }

    // Losing a home planet stops a seat scoring public objectives (LRR 61.16), which is what slaying it achieves.
    @Test
    void aSeatWithoutItsHomePlanetsCannotScore() {
        test.sol.addSC(LEADERSHIP);
        tokens(test.sol, 2, 1);
        points(test.sol, 8);
        test.sol.removePlanet("jord");

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).isEmpty();
    }

    // Both seats reach 10 at status; the one earlier in initiative order wins, so only it counts for the other.
    @Test
    void theSeatFirstInInitiativeOrderFinishesFirst() {
        points(test.nekro, 4);
        test.nekro.getSCs().clear();
        test.nekro.addSC(DIPLOMACY);
        test.sol.addSC(TRADE);
        tokens(test.sol, 3, 3);
        points(test.sol, 8);

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).isEmpty();
        assertThat(WinRace.winnersBefore(test.game, test.sol)).containsExactly(test.nekro);
    }

    // The game can end in the action phase: Imperial at 9 points with Mecatol Rex gains the last point there
    // (Imperial primary), before any status phase, whatever the initiative order.
    @Test
    void imperialWithMecatolEndsTheGameBeforeStatus() {
        test.place("18", "000");
        test.sol.addPlanet("mr");
        test.sol.addSC(IMPERIAL);
        tokens(test.sol, 0, 0);
        points(test.sol, 9);
        points(test.nekro, 4);
        test.nekro.getSCs().clear();
        test.nekro.addSC(DIPLOMACY);

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).containsExactly(test.sol);
    }

    // Fog of War hides what a public projection needs, so the projection stays empty there.
    @Test
    void fogOfWarProjectsNobody() {
        test.sol.addSC(LEADERSHIP);
        tokens(test.sol, 2, 1);
        points(test.sol, 8);
        test.game.setFowMode(true);

        assertThat(WinRace.winnersBefore(test.game, test.nekro)).isEmpty();
    }

    // The user's Support for the Throne case: Sol on 8 holds Imperial without Mecatol. With a Throne point (9) and the
    // fear that it takes Mecatol, Imperial's point lands it on 10 in the action phase, ahead of Nekro on 9 scoring a
    // public at status.
    @Test
    void anImperialHolderWhoTakesMecatolWinsInTheActionPhase() {
        test.sol.addSC(IMPERIAL);
        tokens(test.sol, 0, 0);
        points(test.sol, 8);
        points(test.nekro, 5);

        WinRace.Boost feared = new WinRace.Boost(1, 0, 0, true);
        assertThat(WinRace.finish(test.game, test.sol, feared)).hasValue(0);
        assertThat(WinRace.finish(test.game, test.sol, new WinRace.Boost(1, 0, 0, false)))
                .isEmpty();

        var nekro = WinRace.finish(test.game, test.nekro, new WinRace.Boost(0, 0, 1, false));
        assertThat(nekro).hasValue(1 + TRADE);
        assertThat(WinRace.finishesBefore(nekro, WinRace.finish(test.game, test.sol, feared)))
                .isFalse();
    }

    // Both finish at status: lower initiative scores first (LRR 81.1), and a point gained in the deal itself comes
    // before either.
    @Test
    void initiativeOrdersStatusPhaseFinishes() {
        test.sol.addSC(DIPLOMACY);
        tokens(test.sol, 0, 0);
        points(test.sol, 9);
        points(test.nekro, 5);
        WinRace.Boost onePublic = new WinRace.Boost(0, 0, 1, false);

        var nekro = WinRace.finish(test.game, test.nekro, onePublic);
        var sol = WinRace.finish(test.game, test.sol, onePublic);
        assertThat(WinRace.finishesBefore(sol, nekro)).isTrue();
        assertThat(WinRace.finishesBefore(nekro, sol)).isFalse();

        var nekroNow = WinRace.finish(test.game, test.nekro, new WinRace.Boost(1, 0, 0, false));
        assertThat(WinRace.finishesBefore(nekroNow, sol)).isTrue();
    }
}
