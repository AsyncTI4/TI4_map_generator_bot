package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.testUtils.BaseTi4Test;

/**
 * Pride of Kenara, the Hacan mech: a planet with a Hacan mech on it may be traded. Sol (blue, a human standing in for
 * Hacan) controls Lodor (3 resources, 1 influence) and offers it to Nekro (black, the AI), its neighbour, in the
 * action phase of round 3 with nobody scored, so at least three rounds remain.
 */
class TradePlanetsTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;
    private static final long NOW = AiTestGame.NOW;

    private TradeTable table;

    @BeforeEach
    void setUp() {
        table = TradeTable.withHumanSol();
        table.nekroAndSolNeighbour();
        table.test.place("26", TradeTable.BESIDE_SOL);
        table.sol.addPlanet("lodor");
        table.stock(table.nekro, 0, 6);
        table.stock(table.sol, 0, 0);
    }

    private static DealItem item(String raw) {
        return DealItem.parse(raw, "nekro", "sol").orElseThrow();
    }

    private double lodor() {
        return BoardView.planetValue(table.game.getPlanetsInfo().get("lodor"));
    }

    // Three rounds of Lodor's output, halved because it arrives with no units of Nekro's to hold it.
    @Test
    void aReceivedPlanetIsWorthHalfOfThreeRoundsOfIt() {
        DealItem planet = item("sendingsol_receivingnekro_Planets_lodor");

        assertThat(ComponentValues.receive(table.game, table.nekro, table.sol, planet, 0.8))
                .isCloseTo(0.5 * 3 * lodor(), within(EXACT));
        assertThat(ComponentValues.give(
                        table.game, table.nekro, table.sol, item("sendingnekro_receivingsol_Planets_lodor"), 0.8))
                .isInfinite();
    }

    // Lodor is worth 0.5 x 3 x 3.6 = 5.4 to Nekro: for 2 trade goods it is a bargain Nekro takes, for 6 it is not.
    @Test
    void acceptsAPlanetForLessThanItIsWorth() {
        assertThat(answer(offer(1, "sendingsol_receivingnekro_Planets_lodor", "sendingnekro_receivingsol_TGs_2")))
                .isEqualTo("acceptOffer_blue_1");
        assertThat(answer(offer(2, "sendingsol_receivingnekro_Planets_lodor", "sendingnekro_receivingsol_TGs_6")))
                .isNotEqualTo("acceptOffer_blue_2");
    }

    // An AI never gives a planet away: asked for one, it does not accept.
    @Test
    void neverGivesAPlanetAway() {
        table.nekro.addPlanet("lodor");
        table.sol.removePlanet("lodor");
        table.stock(table.sol, 0, 10);

        assertThat(answer(offer(1, "sendingnekro_receivingsol_Planets_lodor", "sendingsol_receivingnekro_TGs_10")))
                .isNotEqualTo("acceptOffer_blue_1");
    }

    private AiPrompt offer(int number, String... items) {
        table.sol.getTransactionItems().clear();
        for (String raw : items) table.sol.addTransactionItem(raw);
        table.game.setStoredValue("offerFromsolTonekro", String.valueOf(number));
        return TradeButtons.incoming("offer-" + number, NOW - 1_000L, table.sol, number);
    }

    private String answer(AiPrompt offer) {
        OfferBuilder.abandon(table.context());
        Optional<AiDecision> decision = OfferResponder.answer(table.test.contextFor(table.nekro, Set.of(), NOW, offer));
        return decision.map(AiTestGame::pressedId).orElse("");
    }
}
