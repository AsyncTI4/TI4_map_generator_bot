package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

/**
 * A Ceasefire changes hands under duress: when its owner's fleet can take the holder's home system (or Mecatol Rex),
 * the threatened seat values the owner's Ceasefire at three quarters of what it stands to lose, and its own Ceasefire
 * is cheap to hand over, since it was not going to attack the stronger seat anyway.
 */
class CeasefireValueTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;
    private Tile lodor;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.nekroHome();
        lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addOwnedPromissoryNoteByID("black_cf");
        test.sol.addOwnedPromissoryNoteByID("blue_cf");
    }

    @Test
    void noThreatNoDuress() {
        assertThat(CeasefireValue.underDuress(test.game, test.nekro, test.sol)).isFalse();
        assertThat(CeasefireValue.givenCost(test.game, test.nekro, test.sol)).isCloseTo(2.5, within(EXACT));
    }

    // Two Sol dreadnoughts next door outgun an empty home: losing it costs a point's worth (6) and 2 more.
    @Test
    void aFleetThatCanTakeTheHomeSystemIsDuress() {
        test.units(lodor, "space", test.sol, UnitType.Dreadnought, 2);
        test.game.setLastModifiedDate(2);

        assertThat(CeasefireValue.underDuress(test.game, test.nekro, test.sol)).isTrue();
        assertThat(NotesForTrade.receiveValue(test.game, test.nekro, "blue_cf"))
                .isCloseTo(0.75 * (6 + 2), within(EXACT));
        assertThat(CeasefireValue.givenCost(test.game, test.nekro, test.sol)).isCloseTo(0.5, within(EXACT));
        // Sol, for its part, gives up half of what it could take.
        assertThat(CeasefireValue.givenCost(test.game, test.sol, test.nekro)).isCloseTo(0.5 * (6 + 2), within(EXACT));
    }

    // A lone destroyer within reach of an empty home is no real threat: a fleet must be worth 8 resources.
    @Test
    void aScoutingShipIsNoDuress() {
        test.units(lodor, "space", test.sol, UnitType.Destroyer, 1);
        test.game.setLastModifiedDate(4);

        assertThat(CeasefireValue.underDuress(test.game, test.nekro, test.sol)).isFalse();
    }

    // Enough defenders at home (1.5 times what could come is needed to feel safe) lift the duress.
    @Test
    void aDefendedHomeIsNoDuress() {
        test.units(lodor, "space", test.sol, UnitType.Dreadnought, 2);
        test.units(test.nekro.getHomeSystemTile(), "space", test.nekro, UnitType.Dreadnought, 3);
        test.game.setLastModifiedDate(3);

        assertThat(CeasefireValue.underDuress(test.game, test.nekro, test.sol)).isFalse();
    }
}
