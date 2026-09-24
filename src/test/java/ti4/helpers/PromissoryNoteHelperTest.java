package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

/**
 * Faction promissory notes carry no color in real life, and showing one in Fog of War tells the table which
 * color a faction is playing - and stops a note being passed on second-hand as a bluff. The generic notes do
 * come in a player's color, so they must keep it.
 */
class PromissoryNoteHelperTest extends BaseTi4Test {

    @Test
    void factionNotesAreDistinguishedFromColorNotes() {
        assertThat(PromissoryNoteHelper.isFactionPromissoryNote("tekklar")).isTrue();
        assertThat(PromissoryNoteHelper.isFactionPromissoryNote("blood_pact")).isTrue();

        assertThat(PromissoryNoteHelper.isFactionPromissoryNote("green_sftt")).isFalse();
        assertThat(PromissoryNoteHelper.isFactionPromissoryNote("blue_an")).isFalse();
    }

    /** An unknown id must not blow up the button builders that call this on every note in a hand. */
    @Test
    void unknownNoteIsNotTreatedAsAFactionNote() {
        assertThat(PromissoryNoteHelper.isFactionPromissoryNote("no_such_pn_id"))
                .isFalse();
    }
}
