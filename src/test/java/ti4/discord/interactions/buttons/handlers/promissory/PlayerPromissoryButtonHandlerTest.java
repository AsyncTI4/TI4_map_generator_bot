package ti4.discord.interactions.buttons.handlers.promissory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

/**
 * Old "Promissory notes in your hand" messages keep their {@code resolvePNPlay_<id>} buttons after the note
 * has been played or traded away. Pressing one used to play the note a second time: the owner got it
 * re-added to their hand, every on-play effect fired again, and a stale Research Agreement button granted
 * the technology for free.
 */
class PlayerPromissoryButtonHandlerTest extends BaseTi4Test {

    private Game game;
    private Player holder;
    private Player owner;
    private ButtonInteractionEvent event;
    private MockedStatic<PromissoryNoteHelper> pnHelper;
    private MockedStatic<MessageHelper> messageHelper;
    private MockedStatic<ButtonHelper> buttonHelper;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("pn-play-test");
        owner = game.addPlayer("owner-id", "owner");
        owner.setFaction("jolnar");
        owner.setColor("red");
        holder = game.addPlayer("holder-id", "holder");
        holder.setFaction("sol");
        holder.setColor("blue");
        event = mock(ButtonInteractionEvent.class);
        pnHelper = mockStatic(PromissoryNoteHelper.class);
        messageHelper = mockStatic(MessageHelper.class);
        buttonHelper = mockStatic(ButtonHelper.class);
    }

    @AfterEach
    void tearDown() {
        buttonHelper.close();
        messageHelper.close();
        pnHelper.close();
    }

    private void giveNote(String pnID) {
        owner.addOwnedPromissoryNoteByID(pnID);
        holder.setPromissoryNote(pnID);
    }

    private void press(Player presser, String pnID) {
        PlayerPromissoryButtonHandler.resolvePNPlay(event, presser, "resolvePNPlay_" + pnID, game);
    }

    private void assertNotPlayed(String pnID) {
        pnHelper.verify(() -> PromissoryNoteHelper.resolvePNPlay(eq(pnID), any(), any(), any()), never());
        messageHelper.verify(() -> MessageHelper.sendEphemeralMessageToEventChannel(eq(event), anyString()));
    }

    @Test
    void playsANoteStillInHand() {
        giveNote("red_ta");

        press(holder, "red_ta");

        pnHelper.verify(() -> PromissoryNoteHelper.resolvePNPlay("red_ta", holder, game, event), times(1));
    }

    @Test
    void refusesANoteThatWasAlreadyReturnedToItsOwner() {
        owner.addOwnedPromissoryNoteByID("red_ta");
        owner.setPromissoryNote("red_ta");

        press(holder, "red_ta");

        assertNotPlayed("red_ta");
    }

    @Test
    void refusesTheOwnerPlayingTheirOwnNote() {
        owner.addOwnedPromissoryNoteByID("red_ta");
        owner.setPromissoryNote("red_ta");

        press(owner, "red_ta");

        assertNotPlayed("red_ta");
    }

    @Test
    void refusesANoteAlreadyPlacedInThePlayArea() {
        giveNote("blue_an");
        holder.addPromissoryNoteToPlayArea("blue_an");

        press(holder, "blue_an");

        assertNotPlayed("blue_an");
    }

    @Test
    void staleResearchAgreementButtonDoesNotGrantTheTechnology() {
        owner.addOwnedPromissoryNoteByID("ra");
        owner.setPromissoryNote("ra");

        press(holder, "ra_gd");

        assertNotPlayed("ra");
        assertThat(holder.getTechs()).doesNotContain("gd");
    }

    @Test
    void blackMarketForgeryOutsideTheFragmentFlowIsCheckedAgainstTheRealCard() {
        giveNote("bmf");

        press(holder, "bmfNotHand");

        pnHelper.verify(() -> PromissoryNoteHelper.resolvePNPlay("bmfNotHand", holder, game, event), times(1));
    }
}
