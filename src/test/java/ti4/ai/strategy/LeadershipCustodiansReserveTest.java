package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ScoringReserve;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Live game aiplay1, round 2, Leadership secondary:
// - Sardakk (initiative 6) held 6 influence back for the custodians although Hacan and Sol landed first, so it bought
//   no token with Meer. Influence is only kept for a race the seat expects to win.
// - Sol was the favourite (90%) but spent the 2 planets its own Diplomacy would ready, leaving 5 influence. Readying
//   takes Sol's next action, so the claim slipped a turn. A claim the seat can make now must survive the purchase.
class LeadershipCustodiansReserveTest extends BaseTi4Test {

    private static final int DIPLOMACY = 2;
    private static final int LEADERSHIP = 1;
    private static final int WARFARE = 6;
    private static final String EMPTY_SYSTEM = "46";

    private AiTestGame test;
    private Player seat;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.game.setRound(2);
        test.game.setStrategyCardSet("pok");
        test.aiIsActive("action");
        Tile rex = test.place("18", "000");
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        seat = test.nekro;
        test.place("37", "102");
        seat.addPlanet("meer");
        seat.addPlanet("arinam");
        loadedCarrier(seat, "101");
        seat.setTg(2);
        seat.addSC(WARFARE);
        test.game.setSCPlayed(LEADERSHIP, true);
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private void loadedCarrier(Player owner, String position) {
        Tile tile = test.game.getTileByPosition(position);
        if (tile == null) tile = test.place(EMPTY_SYSTEM, position);
        test.units(tile, "space", owner, UnitType.Carrier, 1);
        test.units(tile, "space", owner, UnitType.Infantry, 1);
        changed();
    }

    private int tokensBought() {
        return TokenPurchase.best(test.game, seat, StrategyCardRules.reinforcements(test.game, seat))
                .map(TokenPurchase.Purchase::tokens)
                .orElse(0);
    }

    @Test
    void keepsTheCustodiansInfluenceWhenItLeadsTheRace() {
        assertThat(ScoringReserve.contendsForCustodians(test.game, seat)).isTrue();

        assertThat(tokensBought()).isZero();
    }

    @Test
    void spendsTheInfluenceWhenAnEarlierRivalLandsFirst() {
        loadedCarrier(test.sol, "104");
        test.sol.addSC(LEADERSHIP);
        test.sol.setTg(6);
        changed();

        assertThat(ScoringReserve.contendsForCustodians(test.game, seat)).isFalse();
        assertThat(tokensBought()).isPositive();
    }

    @Test
    void doesNotCountDiplomacyReadiesTowardAClaimItCanMakeNow() {
        seat.addSC(DIPLOMACY);
        changed();

        assertThat(ScoringReserve.custodiansClaim(test.game, seat)).isPresent();
        assertThat(tokensBought()).isZero();
    }
}
