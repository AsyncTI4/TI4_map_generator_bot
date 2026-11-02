package ti4.service.actioncard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.GameMessage;
import ti4.message.GameMessageType;
import ti4.service.actioncard.ActionCardWindowHoldService.HeldWindow;
import ti4.testUtils.BaseTi4Test;

class ActionCardWindowHoldServiceTest extends BaseTi4Test {

    private Game game;
    private Player sol;
    private Player hacan;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("ac-window-hold-test");
        game.setAcDeckID("action_deck_2");
        sol = addPlayer("sol-id", "sol", "blue");
        hacan = addPlayer("hacan-id", "hacan", "green");
    }

    @Test
    void windowStaysLiveUntilEveryItsCardIsDiscarded() {
        assertThat(ActionCardWindowHoldService.liveCardIds(game, HeldWindow.STRATEGY_PHASE_START))
                .containsExactly("deflection", "revolution");

        discard("deflection");
        assertThat(ActionCardWindowHoldService.liveCardIds(game, HeldWindow.STRATEGY_PHASE_START))
                .containsExactly("revolution");

        discard("revolution");
        assertThat(ActionCardWindowHoldService.liveCardIds(game, HeldWindow.STRATEGY_PHASE_START))
                .isEmpty();
    }

    // Only a player actually holding the card is spared from the auto-pass; everyone else may be auto-passed.
    @Test
    void onlyHoldersCouldPlayIntoTheWindow() {
        sol.setActionCard("corruption");
        hacan.setActionCard("deflection");

        assertThat(ActionCardWindowHoldService.couldPlay(sol, HeldWindow.CORRUPTION))
                .isTrue();
        assertThat(ActionCardWindowHoldService.couldPlay(hacan, HeldWindow.CORRUPTION))
                .isFalse();
        assertThat(ActionCardWindowHoldService.couldPlay(hacan, HeldWindow.STRATEGY_PHASE_START))
                .isTrue();
    }

    @Test
    void windowIsReadFromTheHoldMessageKey() {
        GameMessage hold = new GameMessage("1", GameMessageType.ACTION_CARD_WINDOW, 0L, "CORRUPTION");
        GameMessage unknownKey = new GameMessage("2", GameMessageType.ACTION_CARD_WINDOW, 0L, "SOMETHING_ELSE");
        GameMessage otherType = new GameMessage("3", GameMessageType.ACTION_CARD, 0L, "CORRUPTION");

        assertThat(ActionCardWindowHoldService.windowOf(hold)).contains(HeldWindow.CORRUPTION);
        assertThat(ActionCardWindowHoldService.windowOf(unknownKey)).isEmpty();
        assertThat(ActionCardWindowHoldService.windowOf(otherType)).isEmpty();
    }

    private void discard(String cardId) {
        Map<String, Integer> discards = new HashMap<>(game.getDiscardActionCards());
        discards.put(cardId, discards.size() + 1);
        game.setDiscardActionCards(discards);
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }
}
