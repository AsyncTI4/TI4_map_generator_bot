package ti4.service.actioncard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;

class CorruptionTimingServiceTest {

    private Game game;
    private Player player;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setRound(3);
        player = new Player("player1", "", game);
        player.setFaction("sol");
    }

    @Test
    void shouldBlockCorruptionDrawnDuringThisStatusPhase() {
        game.setPhaseOfGame("statusHomework");
        boolean heldBefore = CorruptionTimingService.isHeld(player);
        player.setActionCard("corruption");

        CorruptionTimingService.markIfDrawnSince(game, player, heldBefore);

        assertThat(CorruptionTimingService.isBlocked(game, player, "corruption"))
                .isTrue();
    }

    @Test
    void shouldAllowCorruptionHeldBeforeTheStatusDraw() {
        game.setPhaseOfGame("statusHomework");
        player.setActionCard("corruption");
        boolean heldBefore = CorruptionTimingService.isHeld(player);

        CorruptionTimingService.markIfDrawnSince(game, player, heldBefore);

        assertThat(CorruptionTimingService.isBlocked(game, player, "corruption"))
                .isFalse();
    }

    @Test
    void shouldAllowCorruptionOnceTheNextRoundReveals() {
        game.setPhaseOfGame("statusHomework");
        player.setActionCard("corruption");
        CorruptionTimingService.markIfDrawnSince(game, player, false);

        game.setRound(4);
        game.setPhaseOfGame("statusScoring");

        assertThat(CorruptionTimingService.isBlocked(game, player, "corruption"))
                .isFalse();
    }

    @Test
    void shouldAllowCorruptionOutsideTheStatusPhase() {
        game.setPhaseOfGame("statusHomework");
        player.setActionCard("corruption");
        CorruptionTimingService.markIfDrawnSince(game, player, false);

        game.setPhaseOfGame("agenda");

        assertThat(CorruptionTimingService.isBlocked(game, player, "corruption"))
                .isFalse();
    }

    @Test
    void shouldNotBlockOtherActionCards() {
        game.setPhaseOfGame("statusHomework");
        player.setActionCard("corruption");
        CorruptionTimingService.markIfDrawnSince(game, player, false);

        assertThat(CorruptionTimingService.isBlocked(game, player, "sabotage")).isFalse();
    }
}
