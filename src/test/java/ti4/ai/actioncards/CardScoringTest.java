package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class CardScoringTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;
    private static final int IMPERIAL = 8;

    private CardTable table;

    @BeforeEach
    void setUp() {
        table = new CardTable();
    }

    private void holdsMecatol(Player player) {
        if (table.game.getTileByPosition("000") == null) table.test.place("18", "000");
        player.addPlanet("mr");
    }

    @Test
    void aCardItKnowsNothingAboutScoresNothing() {
        assertThat(CardScoring.pointsFor("Brilliance", table.game, table.sol)).isZero();
        assertThat(CardScoring.pointsDenied("Brilliance", table.game, table.nekro))
                .isZero();
    }

    // Overrule performs Imperial's primary while Imperial is readied or unchosen: the Mecatol Rex point, and one more
    // public when the player qualifies for more than its scoring chances.
    @Test
    void overruleScoresTheMecatolPointWhileImperialIsStillThere() {
        holdsMecatol(table.sol);

        assertThat(CardScoring.pointsFor("Overrule", table.game, table.sol)).isCloseTo(1, within(EXACT));

        table.game.setSCPlayed(IMPERIAL, true);
        assertThat(CardScoring.pointsFor("Overrule", table.game, table.sol)).isZero();
    }

    @Test
    void anImperialRiderIsAGuess() {
        assertThat(CardScoring.pointsFor("Imperial Rider", table.game, table.sol))
                .isCloseTo(0.6, within(EXACT));
    }

    @Test
    void imperialHoldersGainALittleFromTheStabilisingCards() {
        assertThat(CardScoring.pointsFor("Political Stability", table.game, table.sol))
                .isZero();

        table.sol.addSC(IMPERIAL);

        assertThat(CardScoring.pointsFor("Political Stability", table.game, table.sol))
                .isCloseTo(0.5, within(EXACT));
        assertThat(CardScoring.pointsFor("Puppets on a String", table.game, table.sol))
                .isCloseTo(0.5, within(EXACT));
        assertThat(CardScoring.pointsFor("Master Plan", table.game, table.sol)).isCloseTo(0.5, within(EXACT));
    }

    // Negotiate Trade Routes takes 5 trade goods; Mining Initiative's best planet (3 resources) gets Sol there.
    @Test
    void anEconomyCardScoresThePointItUnlocks() {
        table.solHome();
        table.system("38", "201");
        table.sol.addPlanet("abyz");
        table.reveal("trade_routes");
        table.sol.setTg(2);

        assertThat(CardScoring.pointsFor("Mining Initiative", table.game, table.sol))
                .isCloseTo(1, within(EXACT));
        assertThat(CardScoring.pointsFor("Uprising", table.game, table.sol)).isZero();

        table.sol.setTg(3);
        assertThat(CardScoring.pointsFor("Uprising", table.game, table.sol)).isCloseTo(1, within(EXACT));
    }

    // Diversify Research: 2 technologies in each of 2 colors. Sol starts with one biotic and one propulsion technology,
    // so a second biotic one is one short.
    @Test
    void aResearchCardScoresATechnologyObjectiveThatIsOneShort() {
        table.reveal("diversify");
        assertThat(CardScoring.pointsFor("Focused Research", table.game, table.sol))
                .isZero();

        table.sol.addTech("pa");

        assertThat(CardScoring.pointsFor("Focused Research", table.game, table.sol))
                .isCloseTo(1, within(EXACT));
        assertThat(CardScoring.pointsFor("Plagiarize", table.game, table.sol)).isCloseTo(1, within(EXACT));
    }

    // Extreme Duress costs a seat that reserves its trade goods for an objective the point.
    @Test
    void extremeDuressCostsTheSeatItsReservedTradeGoods() {
        table.reveal("trade_routes");
        table.nekro.setTg(5);

        assertThat(CardScoring.pointsDenied("Extreme Duress", table.game, table.nekro))
                .isCloseTo(1, within(EXACT));

        table.nekro.setTg(0);
        assertThat(CardScoring.pointsDenied("Extreme Duress", table.game, table.nekro))
                .isZero();
    }

    // Public Disgrace, Coup d'Etat and Crisis stop the strategy card the seat means to play; Imperial with Mecatol
    // Rex scores a point.
    @Test
    void cardsThatStopAStrategyCardCostTheSeatWhatItWouldScore() {
        assertThat(CardScoring.pointsDenied("Public Disgrace", table.game, table.nekro))
                .isZero();

        table.nekro.addSC(IMPERIAL);
        holdsMecatol(table.nekro);

        assertThat(CardScoring.pointsDenied("Public Disgrace", table.game, table.nekro))
                .isCloseTo(1, within(EXACT));
        assertThat(CardScoring.pointsDenied("Coup d'Etat", table.game, table.nekro))
                .isCloseTo(1, within(EXACT));
        assertThat(CardScoring.pointsDenied("Crisis", table.game, table.nekro)).isCloseTo(1, within(EXACT));

        table.game.setSCPlayed(IMPERIAL, true);
        assertThat(CardScoring.pointsDenied("Crisis", table.game, table.nekro)).isZero();
    }

    @Test
    void anAgendaCardCostsTheSeatNothingWhenNoAgendaIsInPlay() {
        assertThat(CardScoring.pointsDenied("Confounding Legal Text", table.game, table.nekro))
                .isZero();
    }
}
