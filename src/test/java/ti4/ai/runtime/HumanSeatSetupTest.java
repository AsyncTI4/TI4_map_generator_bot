package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import ti4.ai.AiSeats;
import ti4.ai.selfplay.SelfPlaySetup;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.testUtils.BaseTi4Test;

class HumanSeatSetupTest extends BaseTi4Test {

    // /ai play: the person who runs it plays one seat and five AIs fill the others.
    @Test
    void seatsTheOwnerAsAHumanBesideFiveAiPlayers() throws Exception {
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(1_790_000_000_000L)) {
            SelfPlayGame selfPlay = new SelfPlayGame(env, "aiplaytest1", 0, 11L);
            try {
                Random random = new Random(11);
                SelfPlaySetup.Table table = SelfPlaySetup.Table.withHuman(
                        "yin", "red", SelfPlaySetup.pickOpponentFactions(5, "yin", random), random);

                Game game = selfPlay.setUp(table);

                assertThat(game.getRealPlayers()).hasSize(6);
                Player human = game.getPlayer(SelfPlayGame.ownerId());
                assertThat(human.isRealPlayer()).isTrue();
                assertThat(AiSeats.isAiSeat(human)).isFalse();
                assertThat(human.getFaction()).isEqualTo("yin");
                assertThat(human.getColor()).isEqualTo("red");
                assertThat(human.getHomeSystemTile()).isNotNull();
                assertThat(AiSeats.aiSeats(game)).hasSize(5);
                assertThat(AiSeats.aiSeats(game)).extracting(Player::getFaction).doesNotContain("yin");
                assertThat(game.getRealPlayers()).extracting(Player::getFaction).doesNotHaveDuplicates();
                assertThat(game.getRealPlayers().stream().filter(Player::isSpeaker))
                        .hasSize(1);
                assertThat(AiSeats.isSelfPlay(game)).isFalse();
            } finally {
                GameManager.delete(selfPlay.name);
            }
        }
    }

    @Test
    void anOpponentCountBelowFiveLeavesTheOtherSeatsUnused() throws Exception {
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(1_790_000_000_000L)) {
            SelfPlayGame selfPlay = new SelfPlayGame(env, "aiplaytest2", 0, 12L);
            try {
                Random random = new Random(12);
                SelfPlaySetup.Table table = SelfPlaySetup.Table.withHuman(
                        "sol", null, SelfPlaySetup.pickOpponentFactions(3, "sol", random), random);

                Game game = selfPlay.setUp(table);

                assertThat(game.getRealPlayers()).hasSize(4);
                assertThat(game.getPlayerCountForMap()).isEqualTo(4);
                assertThat(List.of("sol"))
                        .isSubsetOf(game.getRealPlayers().stream()
                                .map(Player::getFaction)
                                .toList());
            } finally {
                GameManager.delete(selfPlay.name);
            }
        }
    }
}
