package ti4.ai.promissory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.testUtils.BaseTi4Test;

class NoteGivingTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.sol.addOwnedPromissoryNoteByID("blue_sftt");
    }

    // Least harmful first: Ceasefire, Trade Agreement, Political Secret, any other note, Alliance, and Support for
    // the Throne last. Handing a note back to its owner beats all of them.
    @Test
    void ranksNotesByHarm() {
        assertThat(NoteGiving.giveRank(test.game, "black_cf", "sol")).isZero();
        assertThat(NoteGiving.giveRank(test.game, "black_ta", "sol")).isEqualTo(1);
        assertThat(NoteGiving.giveRank(test.game, "black_ps", "sol")).isEqualTo(2);
        assertThat(NoteGiving.giveRank(test.game, "antivirus", "sol")).isEqualTo(3);
        assertThat(NoteGiving.giveRank(test.game, "black_an", "sol")).isEqualTo(4);
        assertThat(NoteGiving.giveRank(test.game, "black_sftt", "sol")).isEqualTo(5);
        assertThat(NoteGiving.giveRank(test.game, "blue_sftt", "sol")).isEqualTo(-1);
        assertThat(NoteGiving.giveRank(test.game, "black_cf", null)).isZero();
    }

    // Support for the Throne and Alliance only go when allowed; notes in the play area are not in hand.
    @Test
    void leastHarmfulKeepsThroneAndAlliance() {
        test.nekro.setPromissoryNote("black_sftt", 1);
        test.nekro.setPromissoryNote("black_an", 2);

        assertThat(NoteGiving.leastHarmful(test.game, test.nekro, test.sol, false))
                .isEmpty();
        assertThat(NoteGiving.leastHarmful(test.game, test.nekro, test.sol, true))
                .contains("black_an");

        test.nekro.setPromissoryNote("black_ps", 3);
        test.nekro.setPromissoryNote("black_cf", 4);
        test.nekro.getPromissoryNotesInPlayArea().add("black_cf");
        assertThat(NoteGiving.leastHarmful(test.game, test.nekro, test.sol, false))
                .contains("black_ps");
    }

    // After accepting a deal that owes Sol a "TBD" note, the AI remembers which note it priced, once.
    @Test
    void remembersTheNoteItOwes() {
        AiTurnContext context = test.context();

        NoteGiving.owe(context, "sol", "black_ps");

        assertThat(NoteGiving.owed(context, "letnev")).isEmpty();
        assertThat(NoteGiving.owed(context, "sol")).contains("black_ps");
        assertThat(NoteGiving.owed(context, "sol")).isEmpty();
    }
}
