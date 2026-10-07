package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * The "when" labels for promissory notes were built by string-concatenating {@code getColor()}, which is an
 * Optional, so a held Political Secret read "Optional[red] Political Secret" and Political Favor, which has
 * no color, read "Optional.empty Political Favor".
 */
class AgendaWhensAftersHelperTest extends BaseTi4Test {

    private Game game;
    private Player holder;
    private Player owner;

    @BeforeEach
    void setUp() {
        game = new Game();
        owner = game.addPlayer("owner-id", "owner");
        owner.setFaction("xxcha");
        owner.setColor("red");
        holder = game.addPlayer("holder-id", "holder");
        holder.setFaction("sol");
        holder.setColor("blue");
    }

    private void giveNote(String pnID) {
        owner.addOwnedPromissoryNoteByID(pnID);
        holder.setPromissoryNote(pnID);
    }

    @Test
    void politicalSecretButtonShowsTheNoteColor() {
        giveNote("red_ps");

        assertThat(AgendaWhensAftersHelper.getPossibleWhenButtons(holder))
                .extracting(Button::getLabel)
                .containsExactly("Red Political Secret");
    }

    @Test
    void politicalSecretIsListedWithTheNoteColor() {
        giveNote("red_ps");

        assertThat(AgendaWhensAftersHelper.getPossibleWhenNames(holder)).containsExactly("Red Political Secret");
    }

    @Test
    void colorlessFactionNoteHasNoColorPrefix() {
        giveNote("favor");

        assertThat(AgendaWhensAftersHelper.getPossibleWhenButtons(holder))
                .extracting(Button::getLabel)
                .containsExactly("Political Favor");
        assertThat(AgendaWhensAftersHelper.getPossibleWhenNames(holder)).containsExactly("Political Favor");
    }
}
