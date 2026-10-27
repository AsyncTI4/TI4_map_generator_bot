package ti4.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import ti4.service.turn.StartTurnService;
import ti4.testUtils.BaseTi4Test;

/**
 * Playing Gift of Prescience restarts the action phase so its holder moves to the front of the turn order.
 * When the note had been pre-played, startActionPhase played it itself, and that restart ran inside the
 * outer start: turnStart fired twice for the first player (duplicate pings, a double turn count, a second
 * PHASE_STARTED event).
 */
class GiftOfPrescienceActionPhaseTest extends BaseTi4Test {

    private Game game;
    private Player naalu;
    private Player holder;
    private ButtonInteractionEvent event;
    private MockedStatic<MessageHelper> messageHelper;
    private MockedStatic<ButtonHelper> buttonHelper;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("gift-of-prescience-test");
        naalu = game.addPlayer("naalu-id", "naalu");
        naalu.setFaction("naalu");
        naalu.setColor("red");
        naalu.addOwnedPromissoryNoteByID("gift");
        naalu.addSC(1);
        holder = game.addPlayer("holder-id", "holder");
        holder.setFaction("sol");
        holder.setColor("blue");
        holder.setPromissoryNote("gift");
        holder.addSC(5);
        event = mock(ButtonInteractionEvent.class);
        messageHelper = mockStatic(MessageHelper.class);
        buttonHelper = mockStatic(ButtonHelper.class);
    }

    @AfterEach
    void tearDown() {
        buttonHelper.close();
        messageHelper.close();
    }

    @Test
    void preplayedGiftStartsTheFirstTurnOnlyOnce() {
        game.setPhaseOfGame("strategy");
        game.setStoredValue("Play Naalu PN", holder.getFaction());

        try (MockedStatic<StartTurnService> startTurn = mockStatic(StartTurnService.class)) {
            StartPhaseService.startActionPhase(event, game);

            startTurn.verify(() -> StartTurnService.turnStart(any(), any(), any()), times(1));
            startTurn.verify(() -> StartTurnService.turnStart(event, game, holder), times(1));
        }
        assertThat(holder.getPromissoryNotesInPlayArea()).contains("gift");
        assertThat(game.getPhaseOfGame()).isEqualTo("action");
    }

    @Test
    void playingGiftOnceTheActionPhaseIsUnderwayRestartsIt() {
        game.setPhaseOfGame("action");

        try (MockedStatic<StartPhaseService> startPhase = mockStatic(StartPhaseService.class)) {
            PromissoryNoteHelper.resolvePNPlay("gift", holder, game, event);

            startPhase.verify(() -> StartPhaseService.startActionPhase(event, game, false), times(1));
        }
    }

    @Test
    void playingGiftBeforeTheActionPhaseDoesNotStartIt() {
        game.setPhaseOfGame("strategy");

        try (MockedStatic<StartPhaseService> startPhase = mockStatic(StartPhaseService.class)) {
            PromissoryNoteHelper.resolvePNPlay("gift", holder, game, event);

            startPhase.verify(() -> StartPhaseService.startActionPhase(any(), any(), anyBoolean()), never());
        }
        assertThat(holder.getPromissoryNotesInPlayArea()).contains("gift");
    }
}
