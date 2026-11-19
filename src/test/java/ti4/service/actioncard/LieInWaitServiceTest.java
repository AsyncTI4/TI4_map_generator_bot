package ti4.service.actioncard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Several buttons cover one transaction (Trade's Replenish & Wash plus Send 1 Trade Good, the quick wash, the
 * legacy Done button), so the Lie in Wait reminder records each holder/pair once per window and skips repeats.
 */
class LieInWaitServiceTest extends BaseTi4Test {

    private Game game;
    private Player titans;
    private Player deepwrought;
    private Player saar;
    private Player l1z1x;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setRound(4);
        game.setPhaseOfGame("action");
        game.setActivePlayerID("deepwrought-user");
        titans = addPlayer("titans-user", "titans");
        deepwrought = addPlayer("deepwrought-user", "deepwrought");
        saar = addPlayer("saar-user", "saar");
        l1z1x = addPlayer("l1z1x-user", "l1z1x");
    }

    private Player addPlayer(String userId, String faction) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        return player;
    }

    @Test
    void firstReminderForAPair_isRecorded() {
        assertThat(LieInWaitService.recordReminder(game, titans, saar, deepwrought))
                .isTrue();
    }

    @Test
    void secondButtonForTheSameTransaction_isSkipped() {
        // Replenish & Wash, then Send 1 Trade Good, for the same follower and Trade holder.
        LieInWaitService.recordReminder(game, titans, saar, deepwrought);

        assertThat(LieInWaitService.recordReminder(game, titans, saar, deepwrought))
                .isFalse();
    }

    @Test
    void pairOrderDoesNotMatter() {
        LieInWaitService.recordReminder(game, titans, saar, deepwrought);

        assertThat(LieInWaitService.recordReminder(game, titans, deepwrought, saar))
                .isFalse();
    }

    @Test
    void differentPair_isStillRecorded() {
        LieInWaitService.recordReminder(game, titans, saar, deepwrought);

        assertThat(LieInWaitService.recordReminder(game, titans, l1z1x, deepwrought))
                .isTrue();
    }

    @Test
    void differentHolder_isStillRecorded() {
        LieInWaitService.recordReminder(game, titans, saar, deepwrought);

        assertThat(LieInWaitService.recordReminder(game, l1z1x, saar, deepwrought))
                .isTrue();
    }

    @Test
    void nextPlayersTurn_startsAFreshWindow() {
        LieInWaitService.recordReminder(game, titans, saar, deepwrought);
        game.setActivePlayerID("titans-user");

        assertThat(LieInWaitService.recordReminder(game, titans, saar, deepwrought))
                .isTrue();
    }

    @Test
    void nextAgenda_startsAFreshWindow() {
        // LRR 94.6: each agenda opens its own transaction with every other player.
        game.setPhaseOfGame("agendawaiting");
        game.setCurrentAgendaInfo("Law_Player_1_minister_war");
        LieInWaitService.recordReminder(game, titans, saar, deepwrought);
        game.setCurrentAgendaInfo("Directive_ForAgainst_2_mutiny");

        assertThat(LieInWaitService.recordReminder(game, titans, saar, deepwrought))
                .isTrue();
    }

    @Test
    void newWindow_dropsOldReminders() {
        LieInWaitService.recordReminder(game, titans, saar, deepwrought);
        game.setRound(5);
        LieInWaitService.recordReminder(game, titans, l1z1x, deepwrought);

        assertThat(game.getStoredValue(LieInWaitService.REMINDED_KEY)).isEqualTo("titans:deepwrought+l1z1x");
    }
}
