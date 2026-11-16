package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class ScoringPathsTest extends BaseTi4Test {
    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.aiIsActive("action");
        test.nekroHome();
        for (int card = 1; card <= 8; card++) test.game.setScTradeGood(card, 0);
    }

    private void reveal(String id) {
        test.game
                .getRevealedPublicObjectives()
                .put(id, test.game.getRevealedPublicObjectives().size() + 1);
    }

    private void loseHome() {
        test.nekro.removePlanet("mordaiii");
        test.nekro.getHomeSystemTile().getUnitHolderFromPlanet("mordaiii").removeAllUnits(test.nekro.getColor());
    }

    static Stream<Arguments> tradeGoodsPositions() {
        return IntStream.rangeClosed(0, 16)
                .boxed()
                .flatMap(goods -> Stream.of(false, true)
                        .flatMap(imperial -> Stream.of(false, true).map(home -> Arguments.of(goods, imperial, home))));
    }

    @ParameterizedTest
    @MethodSource("tradeGoodsPositions")
    void predictsAffordableScoresAcrossTheTradeGoodsThresholds(int goods, boolean imperial, boolean home) {
        reveal("centralize_trade");
        reveal("trade_routes");
        test.nekro.setTg(goods);
        if (imperial) test.nekro.addSC(8);
        if (!home) loseHome();
        int expected = !home || goods < 5 ? 0 : goods < 10 ? 1 : imperial && goods >= 15 ? 3 : 2;
        assertThat(ScoringPaths.readyPoints(test.game, test.nekro)).isEqualTo(expected);
        assertThat(ScoringStakes.pointsInReach(test.game, test.nekro, test.nekro))
                .isEqualTo(expected);
    }

    @Test
    void unavailableStageTwoDoesNotHideAReadyStageOne() {
        reveal("centralize_trade");
        reveal("trade_routes");
        test.nekro.setTg(5);
        assertThat(ScoringPaths.readyPoints(test.game, test.nekro)).isEqualTo(1);
    }

    @Test
    void imperialCannotSpendTheSameTradeGoodsTwice() {
        reveal("centralize_trade");
        reveal("trade_routes");
        test.nekro.setTg(10);
        test.nekro.addSC(8);
        assertThat(ScoringPaths.readyPoints(test.game, test.nekro)).isEqualTo(2);
        assertThat(ScoringStakes.pointsInReach(test.game, test.nekro, test.nekro))
                .isEqualTo(2);
    }

    @Test
    void imperialCanScoreTwoSpendsWhenBothPaymentsAreAffordable() {
        reveal("centralize_trade");
        reveal("trade_routes");
        test.nekro.setTg(15);
        test.nekro.addSC(8);
        assertThat(ScoringPaths.readyPoints(test.game, test.nekro)).isEqualTo(3);
        assertThat(ScoringStakes.pointsInReach(test.game, test.nekro, test.nekro))
                .isEqualTo(3);
    }

    private void giveWinnuHero(boolean unlocked) {
        test.nekro.addLeader("winnuhero");
        test.nekro.getLeader("winnuhero").orElseThrow().setLocked(!unlocked);
    }

    @Test
    void unlockedWinnuHeroScoresASecondPublicThroughImperial() {
        reveal("centralize_trade");
        reveal("trade_routes");
        test.nekro.setTg(15);
        giveWinnuHero(true);
        assertThat(ScoringPaths.readyPoints(test.game, test.nekro)).isEqualTo(3);
        assertThat(ScoringStakes.pointsInReach(test.game, test.nekro, test.nekro))
                .isEqualTo(3);
    }

    @Test
    void lockedWinnuHeroAddsNoScoringSlot() {
        reveal("centralize_trade");
        reveal("trade_routes");
        test.nekro.setTg(15);
        giveWinnuHero(false);
        assertThat(ScoringPaths.readyPoints(test.game, test.nekro)).isEqualTo(2);
    }

    @Test
    void winnuHeroAddsNoScoringSlotWithoutImperialInTheGame() {
        reveal("centralize_trade");
        reveal("trade_routes");
        test.nekro.setTg(15);
        giveWinnuHero(true);
        test.game.setScTradeGoods(
                new java.util.LinkedHashMap<>(java.util.Map.of(1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0)));
        assertThat(ScoringPaths.readyPoints(test.game, test.nekro)).isEqualTo(2);
    }

    @Test
    void losingHomeBlocksAnOtherwiseAffordablePublic() {
        reveal("trade_routes");
        test.nekro.setTg(5);
        loseHome();
        assertThat(ScoringPaths.readyPoints(test.game, test.nekro)).isZero();
    }

    @Test
    void imperialMecatolPointSurvivesPublicScoringIneligibility() {
        test.place("18", "000");
        test.nekro.addPlanet("mr");
        test.nekro.addSC(8);
        loseHome();
        assertThat(ScoringPaths.readyPoints(test.game, test.nekro)).isEqualTo(1);
    }
}
