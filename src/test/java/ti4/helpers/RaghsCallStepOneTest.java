package ti4.helpers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

/**
 * Step one of Ragh's Call is reached two ways: straight from the post-movement offer, while the note is still
 * in hand, or from the prompt that playing the note from hand sends. Step one used to play the note in both
 * cases, so the second route played it twice - and for the Sigma variant it played "ragh", a note nobody
 * owns there, which crashed on the missing owner.
 */
class RaghsCallStepOneTest extends BaseTi4Test {

    private Game game;
    private Player saar;
    private Player lander;
    private ButtonInteractionEvent event;
    private MockedStatic<PromissoryNoteHelper> pnHelper;
    private MockedStatic<MessageHelper> messageHelper;
    private MockedStatic<ButtonHelper> buttonHelper;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("raghs-call-test");
        saar = game.addPlayer("saar-id", "saar");
        saar.setFaction("saar");
        saar.setColor("red");
        lander = game.addPlayer("lander-id", "lander");
        lander.setFaction("sol");
        lander.setColor("blue");
        event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getButton().getLabel()).thenReturn("Ragh's Call on Mecatol Rex");
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

    private void pressStepOne() {
        ButtonHelperFactionSpecific.resolveRaghsCallStepOne(lander, game, event, "raghsCallStepOne_mr");
    }

    @Test
    void playsTheNoteWhenStepOneIsReachedWithTheNoteStillInHand() {
        saar.addOwnedPromissoryNoteByID("ragh");
        lander.setPromissoryNote("ragh");

        pressStepOne();

        pnHelper.verify(() -> PromissoryNoteHelper.resolvePNPlay("ragh", lander, game, event), times(1));
    }

    @Test
    void doesNotPlayTheNoteAgainAfterItWasPlayedFromHand() {
        // Playing from hand already returned the note to the Saar player before offering step one.
        saar.addOwnedPromissoryNoteByID("ragh");
        saar.setPromissoryNote("ragh");

        pressStepOne();

        pnHelper.verify(() -> PromissoryNoteHelper.resolvePNPlay(anyString(), any(), any(), any()), never());
    }

    @Test
    void doesNotPlayTheBaseNoteForTheSigmaVariant() {
        saar.addOwnedPromissoryNoteByID("sigma_raghs_call");
        saar.setPromissoryNote("sigma_raghs_call");

        pressStepOne();

        pnHelper.verify(() -> PromissoryNoteHelper.resolvePNPlay(anyString(), any(), any(), any()), never());
    }
}
