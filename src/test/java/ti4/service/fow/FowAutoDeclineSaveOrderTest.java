package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

// FastScFollowCron saves the game once, after every game-state change, and only then runs the Discord and
// database follow-ups (reactions, messages). Before, the reaction ran first and could remove the strategy card's
// GameMessage; if the save then never happened (the cron failed or the process stopped), the decline was lost for
// good, because the next run skips strategy cards whose message is gone.
class FowAutoDeclineSaveOrderTest extends BaseTi4Test {

    private static final int DIPLOMACY = 2;

    private Game game;
    private Player primary;
    private Player follower;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("test-fow-auto-decline");
        game.setFowMode(true);
        game.setFowOption(FOWOption.FOG_QOL_01, true);
        primary = addPlayer("primary-user", "sol", "red");
        follower = addPlayer("follower-user", "hacan", "blue");
        primary.addSC(DIPLOMACY);
        follower.setStrategicCC(0);
    }

    private Player addPlayer(String userId, String faction, String color) {
        Player player = game.addPlayer(userId, faction);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }

    @Test
    void dueDeclineChangesGameStateNowAndDefersTheReactionAndMessage() {
        game.setStoredValue("fowAutoDecline_" + DIPLOMACY + "_hacan", "0_" + game.getRound());
        List<Runnable> afterSave = new ArrayList<>();

        boolean changed = FowAutoDeclineService.resolveDue(game, (g, p) -> {}, afterSave::add);

        assertThat(changed).isTrue();
        assertThat(follower.hasFollowedSC(DIPLOMACY)).isTrue();
        assertThat(game.getStoredValue("fowAutoDecline_" + DIPLOMACY + "_hacan"))
                .isEmpty();
        // The reaction (which can remove the strategy card's GameMessage) and the cards-info message are deferred,
        // so none of them has happened yet: the cron runs them only after GameManager.save succeeds.
        assertThat(afterSave).hasSize(1);
    }

    @Test
    void declineThatIsNotDueYetDefersNothing() {
        game.setStoredValue("fowAutoDecline_" + DIPLOMACY + "_hacan", Long.MAX_VALUE + "_" + game.getRound());
        List<Runnable> afterSave = new ArrayList<>();

        boolean changed = FowAutoDeclineService.resolveDue(game, (g, p) -> {}, afterSave::add);

        assertThat(changed).isFalse();
        assertThat(follower.hasFollowedSC(DIPLOMACY)).isFalse();
        assertThat(afterSave).isEmpty();
    }
}
