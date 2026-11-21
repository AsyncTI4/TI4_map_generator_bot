package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class SpendUnlockTest extends BaseTi4Test {

    private AiTestGame test;

    // Nekro controls its home system (Mordai II, 4 resources), so it may score public objectives.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
    }

    private void reveal(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
    }

    // Abyz (3 resources) and Fria (2 resources), both exhausted.
    private void exhaustedResourcePlanets() {
        test.place("38", "201");
        for (String planet : List.of("abyz", "fria")) {
            test.nekro.addPlanet(planet);
            test.nekro.exhaustPlanet(planet);
        }
    }

    // Erect a Monument takes 8 resources. Mordai II pays 4; readying Abyz and Fria adds 5, readying Fria alone only 2.
    // Trade goods count too: 4 more pay for it with Mordai II.
    @Test
    void readiedPlanetsOrTradeGoodsUnlockASpendObjective() {
        exhaustedResourcePlanets();
        reveal("monument");

        assertThat(SpendUnlock.unlocks(test.game, test.nekro, List.of("abyz", "fria"), 0))
                .isTrue();
        assertThat(SpendUnlock.unlocks(test.game, test.nekro, List.of("fria"), 0))
                .isFalse();
        assertThat(SpendUnlock.unlocks(test.game, test.nekro, List.of(), 4)).isTrue();
    }

    // An objective it can already pay for is not unlocked by anything.
    @Test
    void doesNotCountAnObjectiveThatIsAlreadyPayable() {
        reveal("trade_routes");
        test.nekro.setTg(5);

        assertThat(SpendUnlock.unlocks(test.game, test.nekro, List.of(), 3)).isFalse();
        assertThat(SpendUnlock.pointDelta(test.game, test.nekro, 3)).isZero();
    }

    // Negotiate Trade Routes takes 5 trade goods: with 3, two more score a point and one more does not.
    @Test
    void twoMoreTradeGoodsUnlockTradeRoutes() {
        reveal("trade_routes");
        test.nekro.setTg(3);

        assertThat(SpendUnlock.pointDelta(test.game, test.nekro, 2)).isEqualTo(1);
        assertThat(SpendUnlock.pointDelta(test.game, test.nekro, 1)).isZero();
        assertThat(SpendUnlock.pointDelta(test.game, test.nekro, 0)).isZero();
    }

    // With exactly 5 trade goods the seat keeps them back for Trade Routes; giving 2 away loses that point, while a
    // seat with 7 can spare them.
    @Test
    void givingAwayReservedTradeGoodsLosesThePoint() {
        reveal("trade_routes");
        test.nekro.setTg(5);
        assertThat(ScoringReserve.reserved(test.game, test.nekro))
                .hasValueSatisfying(
                        reserved -> assertThat(reserved.objectiveId()).isEqualTo("trade_routes"));

        assertThat(SpendUnlock.pointDelta(test.game, test.nekro, -2)).isEqualTo(-1);

        test.nekro.setTg(7);
        assertThat(SpendUnlock.pointDelta(test.game, test.nekro, -2)).isZero();
    }

    // Mirror Computing makes each trade good spent as resources worth 2: Mordai II's 4 plus 2 trade goods pay for Erect
    // a Monument's 8, so 1 more trade good unlocks it. Without the technology it does not.
    @Test
    void mirrorComputingDoublesTradeGoodsSpentAsResources() {
        reveal("monument");
        test.nekro.setTg(1);
        assertThat(SpendUnlock.pointDelta(test.game, test.nekro, 1)).isZero();

        test.nekro.addTech("mc");
        assertThat(SpendUnlock.pointDelta(test.game, test.nekro, 1)).isEqualTo(1);
    }

    // Trade goods spent as trade goods are not doubled: Trade Routes still needs 5 of them.
    @Test
    void mirrorComputingDoesNotDoubleTradeGoodsSpentAsTradeGoods() {
        reveal("trade_routes");
        test.nekro.addTech("mc");
        test.nekro.setTg(3);

        assertThat(SpendUnlock.pointDelta(test.game, test.nekro, 1)).isZero();
        assertThat(SpendUnlock.pointDelta(test.game, test.nekro, 2)).isEqualTo(1);
    }

    // Judging another seat must not peek at its hand: the answer comes from its planets, trade goods, tokens and
    // strategy cards, all of which are on the table.
    @Test
    void readsOnlyPublicDataForAnotherSeat() {
        test.place("01", "304");
        test.sol.addPlanet("jord");
        test.sol.setTg(3);
        reveal("trade_routes");
        Player sol = Mockito.spy(test.sol);
        test.game.getPlayers().put(sol.getUserID(), sol);

        assertThat(SpendUnlock.pointDelta(test.game, sol, 2)).isEqualTo(1);
        assertThat(SpendUnlock.pointDelta(test.game, sol, -2)).isZero();
        assertThat(SpendUnlock.unlocks(test.game, sol, List.of(), 2)).isTrue();

        verify(sol, never()).getSecretsUnscored();
        verify(sol, never()).getActionCards();
        verify(sol, never()).getPromissoryNotes();
    }
}
