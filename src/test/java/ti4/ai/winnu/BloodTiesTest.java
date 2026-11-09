package ti4.ai.winnu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.CustodiansRace;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.tactical.TacticalRules;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Blood Ties: the Winnu remove the custodians token without spending influence (they still need ground forces on
// Mecatol Rex, which the landing plans already require), so every custodians check prices it at 0 for them.
class BloodTiesTest extends BaseTi4Test {

    private final WinnuTestBoard board = new WinnuTestBoard();

    @Test
    void theCustodiansCostNoInfluenceWithBloodTies() {
        assertThat(CustodiansRace.cost(board.winnu)).isZero();
        assertThat(CustodiansRace.cost(board.test.sol)).isEqualTo(CustodiansRace.COST);
    }

    // A Winnu seat without a single planet to exhaust can still afford the custodians: there is nothing to pay.
    @Test
    void aWinnuWithoutInfluenceCanStillPayForTheCustodians() {
        board.winnu.getPlanets().clear();

        assertThat(ScoringReserve.planForCustodians(board.test.game, board.winnu))
                .isPresent();
        assertThat(ScoringReserve.custodiansReserve(board.test.game, board.winnu))
                .isEqualTo(SpendCost.NONE);
    }

    // The waiver keys on the ability, so a seat that gains Blood Ties another way (Nekro, Franken) gets it too.
    @Test
    void theWaiverFollowsTheAbility() {
        board.test.nekro.addAbility(BloodTies.ABILITY);

        assertThat(CustodiansRace.cost(board.test.nekro)).isZero();
    }

    // The seat owes trade goods it cannot pay yet, so the reserve-aware custodians plan comes back empty. The fallback
    // used to price the custodians at 6 influence and dropped the landing; with Blood Ties there is nothing to pay.
    @Test
    void landsOnTheCustodiansWithoutInfluenceWhileOwingTradeGoods() {
        board.winnu.getPlanets().clear();
        board.winnu.setTg(0);
        board.winnu.addTransactionItem("sendingwinnu_receivingsol_TGs_3");
        board.mecatol.addToken(Constants.CUSTODIAN_TOKEN_PNG, WinnuTestBoard.MECATOL);
        board.test.units(board.mecatol, "space", board.winnu, UnitType.Carrier, 1);
        board.test.units(board.mecatol, "space", board.winnu, UnitType.Infantry, 1);
        board.winnuActive("action");
        board.test.game.setStoredValue("currentActionSummarywinnu", " Activated 000 (Mecatol Rex).");
        board.test.game.setActiveSystem(WinnuTestBoard.MECATOL_POSITION);
        String land = "FFCC_winnu_landUnits_000_1gf_mr_green";
        AiPrompt landing =
                AiTestGame.prompt("landing", PromptSource.PUBLIC, AiTestGame.NOW, land, "FFCC_winnu_doneLanding_000");

        assertThat(ScoringReserve.planForCustodians(board.test.game, board.winnu))
                .isEmpty();
        assertThat(AiTestGame.pressedId(TacticalRules.continueAction(
                                board.test.contextFor(board.winnu, Set.of(), AiTestGame.NOW, landing))
                        .orElseThrow()))
                .isEqualTo(land);
    }
}
