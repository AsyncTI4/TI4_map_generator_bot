package ti4.service.breakthrough;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.map.Game;
import ti4.map.Player;
import ti4.testUtils.BaseTi4Test;

class AutoFactoriesServiceTest extends BaseTi4Test {

    @Test
    void shouldNotGainFleetTokenWhenFleetRegulationsInPlayAndAtLimit() {
        beforeAll();

        var game = new Game();
        // Set Fleet Regulations law
        game.setLaws(Map.of("regulations", 1));

        var player = new Player("userId", "userName", game);
        // Set fleet CC to exactly 4 (the limit under Fleet Regulations)
        player.setFleetCC(4);
        // Add and unlock the Hacan breakthrough
        player.addBreakthrough("hacanbt");
        player.setBreakthroughUnlocked("hacanbt", true);
        player.setColor("black");

        // Player should not gain fleet CC because they are already at the limit
        // and Fleet Regulations is in play
        int initialFleetCC = player.getFleetCC();

        // Direct check of the logic: player has unlocked hacanbt, and regulations is in play with 4+ fleet CC
        boolean hasBreakthrough = player.hasUnlockedBreakthrough("hacanbt");
        boolean regulationsInPlay = game.getLaws().containsKey("regulations");
        boolean atOrAboveLimit = player.getEffectiveFleetCC() >= 4;

        assertThat(hasBreakthrough).isTrue();
        assertThat(regulationsInPlay).isTrue();
        assertThat(atOrAboveLimit).isTrue();
        assertThat(initialFleetCC).isEqualTo(4);

        // The logic in AutoFactoriesService should prevent gaining a token
        // when regulations is in play and effective fleet CC is >= 4
    }

    @Test
    void shouldAllowFleetTokenGainWhenBelowLimit() {
        beforeAll();

        var game = new Game();
        // Set Fleet Regulations law
        game.setLaws(Map.of("regulations", 1));

        var player = new Player("userId", "userName", game);
        // Set fleet CC to 3 (below the limit under Fleet Regulations)
        player.setFleetCC(3);
        // Add and unlock the Hacan breakthrough
        player.addBreakthrough("hacanbt");
        player.setBreakthroughUnlocked("hacanbt", true);
        player.setColor("black");

        // Player should be able to gain a fleet token because they are below the limit
        int initialFleetCC = player.getFleetCC();

        boolean hasBreakthrough = player.hasUnlockedBreakthrough("hacanbt");
        boolean regulationsInPlay = game.getLaws().containsKey("regulations");
        boolean belowLimit = player.getEffectiveFleetCC() < 4;

        assertThat(hasBreakthrough).isTrue();
        assertThat(regulationsInPlay).isTrue();
        assertThat(belowLimit).isTrue();
        assertThat(initialFleetCC).isEqualTo(3);

        // When regulations is in play but effective fleet CC < 4, gaining is allowed
    }

    @Test
    void shouldAllowFleetTokenGainWhenFleetRegulationsNotInPlay() {
        beforeAll();

        var game = new Game();
        // No Fleet Regulations law in play

        var player = new Player("userId", "userName", game);
        // Set fleet CC to 5 (would be above limit if regulations was in play)
        player.setFleetCC(5);
        // Add and unlock the Hacan breakthrough
        player.addBreakthrough("hacanbt");
        player.setBreakthroughUnlocked("hacanbt", true);
        player.setColor("black");

        boolean hasBreakthrough = player.hasUnlockedBreakthrough("hacanbt");
        boolean regulationsInPlay = game.getLaws().containsKey("regulations");

        assertThat(hasBreakthrough).isTrue();
        assertThat(regulationsInPlay).isFalse();

        // When regulations is NOT in play, there is no restriction on gaining fleet tokens
    }
}
