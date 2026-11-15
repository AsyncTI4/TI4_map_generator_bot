package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Covert Legislation: the speaker draws the next agenda face down and reads out only its outcomes. Here the hidden
// card is an "Elect Player" agenda. Sol is the speaker; Nekro sees only the announced target.
class CovertValuesTest extends BaseTi4Test {

    private static final double EPS = 1e-9;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setRound(3);
        test.game.setSpeakerUserID(test.sol.getUserID());
        test.game.setCurrentAgendaInfo("Law_Elect Player_CL_covert");
        test.game.getSentAgendas().put("minister_war", 101);
    }

    private double value(boolean ownView, String outcome) {
        return value(test.nekro, ownView, outcome);
    }

    private double value(Player voter, boolean ownView, String outcome) {
        OptionalDouble value = AgendaValues.of(test.game, voter, ownView, true, CovertValues.COVERT, outcome, outcome);
        assertThat(value).isPresent();
        return value.getAsDouble();
    }

    // The outcomes are the announced target's, not the hidden card's.
    @Test
    void theOutcomesComeFromTheAnnouncedTarget() {
        assertThat(AgendaOutcomes.of(test.game)).contains("sol", "nekro");
    }

    // Leak check: Nekro's value does not change when the card in the speaker's hand is swapped for another
    // "Elect Player" agenda; it averages over every one still possible.
    @Test
    void aNonSpeakerDoesNotReadTheHiddenAgenda() {
        double before = value(true, "nekro");
        test.game.getSentAgendas().clear();
        test.game.getSentAgendas().put("minister_peace", 102);

        assertThat(value(true, "nekro")).isCloseTo(before, within(EPS));
    }

    // The speaker holds the card, so in its own view it values that agenda exactly.
    @Test
    void theSpeakerValuesTheCardItHolds() {
        double covert = value(test.sol, true, "sol");
        OptionalDouble held = AgendaValues.of(test.game, test.sol, true, true, "minister_war", "sol", "sol");

        assertThat(held).isPresent();
        assertThat(covert).isCloseTo(held.getAsDouble(), within(EPS));
    }

    // Discarded agendas and laws in play are not in the deck, so they cannot be the hidden card.
    @Test
    void discardsAndLawsInPlayAreRuledOut() {
        assertThat(CovertValues.possibleAgendas(test.game)).contains("minister_war", "minister_peace");

        test.game.getDiscardAgendas().put("minister_peace", 201);
        test.game.addLaw("minister_war", "sol");

        assertThat(CovertValues.possibleAgendas(test.game)).doesNotContain("minister_war", "minister_peace");
        assertThat(CovertValues.possibleAgendas(test.game)).isNotEmpty();
    }
}
