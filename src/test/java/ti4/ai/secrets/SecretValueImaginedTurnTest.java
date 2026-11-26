package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.ImaginedTurn;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// A rival imagining the seat's turn sees only what the table sees: the seat's unscored secrets and its hand stay
// hidden, so the secret valuation must not lean on them while that seat is imagined.
class SecretValueImaginedTurnTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
    }

    private double value(String secret) {
        return SecretValue.keepValue(test.game, test.nekro, secret);
    }

    private void controls(String tileId, String position, String... planets) {
        test.place(tileId, position);
        for (String planet : planets) test.nekro.addPlanet(planet);
    }

    // The imagined seat holds no secrets the table can see, so there is none to discard; its own turn still finds one.
    @Test
    void anImaginedTurnFindsNoSecretToDiscard() {
        test.nekro.setSecret("btv");
        test.nekro.setSecret("eap");

        assertThat(ImaginedTurn.of(test.nekro, () -> SecretValue.lowestValueSecret(test.game, test.nekro)))
                .isEmpty();
        assertThat(SecretValue.lowestValueSecret(test.game, test.nekro)).isPresent();
        assertThat(ImaginedTurn.of(test.nekro, () -> SecretValue.lowestValueSecret(test.game, test.nekro)))
                .isEmpty();
    }

    // Imagining another seat leaves this seat's secrets in view.
    @Test
    void imaginingAnotherSeatKeepsThisSeatsSecretsInView() {
        Player rival = test.addSeat("200000000000000002", "sardakk", "red");
        test.nekro.setSecret("btv");
        test.nekro.setSecret("eap");

        assertThat(ImaginedTurn.of(rival, () -> SecretValue.lowestValueSecret(test.game, test.nekro)))
                .contains(SecretValue.lowestValueSecret(test.game, test.nekro).orElseThrow());
    }

    // Same board as SecretValueTest's congestion case: round five with one status phase left, Establish Hegemony at
    // 10 of 12 influence. Once a met Strengthen Bonds competes for that last status phase, the seat's own turn halves
    // Establish Hegemony; an imagined turn cannot see Strengthen Bonds, so it keeps the uncongested value.
    @Test
    void anImaginedTurnDoesNotSeeAHiddenSecretCongestTheLastStatusPhase() {
        test.game.setRound(5);
        test.points(test.sol, 8);
        controls("37", "101", "arinam", "meer");
        controls("24", "102", "meharxull");
        controls("21", "103", "thibah");
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        Tile battlefield = test.place("46", neighbour);
        test.units(battlefield, "space", test.sol, UnitType.Destroyer, 1);
        test.nekro.setSecret("eh");
        double uncongested = value("eh");

        test.sol.addOwnedPromissoryNoteByID("blue_cf");
        test.nekro.setPromissoryNote("blue_cf");
        test.nekro.addPromissoryNoteToPlayArea("blue_cf");
        test.nekro.setSecret("sb");

        double imagined = ImaginedTurn.of(test.nekro, () -> value("eh"));
        double own = value("eh");
        double imaginedAfterwards = ImaginedTurn.of(test.nekro, () -> value("eh"));

        assertThat(own).isCloseTo(uncongested * 0.5, within(0.005));
        assertThat(imagined).isCloseTo(uncongested, within(0.005));
        assertThat(imaginedAfterwards).isCloseTo(uncongested, within(0.005));
    }

    // A Ghost Ship in hand opens the locked nexus (82a) for Defy Space and Time on the seat's own turn. The imagined
    // turn cannot see the hand, so the nexus stays locked there and the secret keeps 0.12 of its value.
    @Test
    void anImaginedTurnDoesNotSeeAGhostShipOpenTheNexus() {
        test.place("82a", neighbour);
        test.nekro.setSecret("dfat");
        test.nekro.setActionCard("ghost_ship", 101);

        double imagined = ImaginedTurn.of(test.nekro, () -> value("dfat"));
        double own = value("dfat");
        double imaginedAfterwards = ImaginedTurn.of(test.nekro, () -> value("dfat"));

        assertThat(own).isGreaterThan(0);
        assertThat(imagined).isCloseTo(own * 0.12, within(0.005));
        assertThat(imaginedAfterwards).isCloseTo(own * 0.12, within(0.005));
    }
}
