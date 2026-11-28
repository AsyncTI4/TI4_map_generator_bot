package ti4.ai.winnu;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.StrategyCardRules;
import ti4.testUtils.BaseTi4Test;

// Acquiescence (Winnu note): when the Winnu resolve a strategic action, its holder may perform the secondary without
// spending a command token. The bot sends the holder an unowned "winnuPNPlay_<initiative>" button in its private
// thread; pressing it marks the card followed, and the secondary is then resolved with the card's usual buttons (the
// follow button spends nothing for a card already followed). An AI holder plays it the way it exhausts the Scepter of
// Emelpar: right before a follow it has decided to make.
class AcquiescenceTest extends BaseTi4Test {

    private static final int TECHNOLOGY = 7;

    private WinnuTestBoard board;
    private AiPrompt card;
    private AiPrompt note;

    @BeforeEach
    void setUp() {
        board = new WinnuTestBoard();
        board.test.aiIsActive("action");
        board.test.nekroHome();
        board.test.nekro.setStrategicCC(0);
        board.test.nekro.setPromissoryNote(Acquiescence.NOTE);
        board.winnu.addSC(TECHNOLOGY);
        board.test.game.setSCPlayed(TECHNOLOGY, true);
        board.test.game.setActivePlayerID(board.winnu.getUserID());
        card = prompt(
                "tech",
                PromptSource.PUBLIC,
                NOW,
                "sc_no_follow_7",
                "acquireATechWithSC_first",
                "FFCC_nekro_nekroFollowTech");
        note = prompt("acq", PromptSource.AI_THREAD, NOW, "winnuPNPlay_7", "deleteButtons");
    }

    @Test
    void playsAcquiescenceBeforeFollowingWithoutAStrategyToken() {
        Optional<AiDecision> play = StrategyCardRules.follow(board.test.context(card, note));

        assertThat(play.map(AiTestGame::pressedId)).contains("winnuPNPlay_7");
    }

    @Test
    void thenStillMakesTheFollow() {
        StrategyCardRules.follow(board.test.context(card, note));
        board.test.nekro.getFollowedSCs().add(TECHNOLOGY);
        Set<String> pressed = Set.of(AiTurnContext.pressKey(note, note.buttons().getFirst()));

        assertThat(StrategyCardRules.follow(board.test.context(pressed, card, note))
                        .map(AiTestGame::pressedId))
                .contains("FFCC_nekro_nekroFollowTech");
    }

    // The Winnu's own note in their own hand is never played on themselves.
    @Test
    void noPlayForTheOwner() {
        board.winnu.setPromissoryNote(Acquiescence.NOTE);
        board.winnu.addOwnedPromissoryNoteByID(Acquiescence.NOTE);

        assertThat(Acquiescence.holdsForeign(board.winnu)).isFalse();
        assertThat(Acquiescence.holdsForeign(board.test.nekro)).isTrue();
    }
}
