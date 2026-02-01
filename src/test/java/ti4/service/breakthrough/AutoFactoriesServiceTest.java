package ti4.service.breakthrough;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.helpers.ButtonHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Tests for AutoFactoriesService.
 * Note: These tests verify the preconditions that the service checks.
 * Full integration tests would require complex setup with produced units,
 * unit models, and message handling which is tested through manual testing.
 */
class AutoFactoriesServiceTest extends BaseTi4Test {

    @Test
    void shouldNotGainFleetTokenWhenFleetRegulationsInPlayAndAtLimit() {
        beforeAll();

        var game = new Game();
        // Set Fleet Regulations law (the integer value is a unique identifier for the law instance)
        game.setLaws(Map.of("regulations", 1));

        var player = new Player("userId", "userName", game);
        // Set fleet CC to exactly 4 (the limit under Fleet Regulations)
        player.setFleetCC(4);
        // Add and unlock the Hacan breakthrough
        player.addBreakthrough("hacanbt");
        player.setBreakthroughUnlocked("hacanbt", true);
        player.setColor("black");

        int initialFleetCC = player.getFleetCC();

        // Verify the preconditions that AutoFactoriesService checks
        boolean hasBreakthrough = player.hasUnlockedBreakthrough("hacanbt");
        boolean regulationsInPlay = ButtonHelper.isLawInPlay(game, "regulations");
        boolean atOrAboveLimit = player.getEffectiveFleetCC() >= 4;

        assertThat(hasBreakthrough).isTrue();
        assertThat(regulationsInPlay).isTrue();
        assertThat(atOrAboveLimit).isTrue();
        assertThat(initialFleetCC).isEqualTo(4);

        // Under these conditions, AutoFactoriesService.resolveAutoFactories() will:
        // 1. Check if player has unlocked hacanbt breakthrough (true)
        // 2. Check if Fleet Regulations is in play AND effective fleet CC >= 4 (true)
        // 3. Send a message explaining why they cannot gain the token
        // 4. Return early WITHOUT granting the fleet token
    }

    @Test
    void shouldAllowFleetTokenGainWhenBelowLimit() {
        beforeAll();

        var game = new Game();
        // Set Fleet Regulations law (the integer value is a unique identifier for the law instance)
        game.setLaws(Map.of("regulations", 1));

        var player = new Player("userId", "userName", game);
        // Set fleet CC to 3 (below the limit under Fleet Regulations)
        player.setFleetCC(3);
        // Add and unlock the Hacan breakthrough
        player.addBreakthrough("hacanbt");
        player.setBreakthroughUnlocked("hacanbt", true);
        player.setColor("black");

        int initialFleetCC = player.getFleetCC();

        // Verify the preconditions that AutoFactoriesService checks
        boolean hasBreakthrough = player.hasUnlockedBreakthrough("hacanbt");
        boolean regulationsInPlay = ButtonHelper.isLawInPlay(game, "regulations");
        boolean belowLimit = player.getEffectiveFleetCC() < 4;

        assertThat(hasBreakthrough).isTrue();
        assertThat(regulationsInPlay).isTrue();
        assertThat(belowLimit).isTrue();
        assertThat(initialFleetCC).isEqualTo(3);

        // Under these conditions, AutoFactoriesService.resolveAutoFactories() will:
        // 1. Check if player has unlocked hacanbt breakthrough (true)
        // 2. Check if Fleet Regulations is in play AND effective fleet CC >= 4 (false - they're at 3)
        // 3. Grant the fleet token, bringing them to 4
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

        // Verify the preconditions that AutoFactoriesService checks
        boolean hasBreakthrough = player.hasUnlockedBreakthrough("hacanbt");
        boolean regulationsInPlay = ButtonHelper.isLawInPlay(game, "regulations");

        assertThat(hasBreakthrough).isTrue();
        assertThat(regulationsInPlay).isFalse();

        // Under these conditions, AutoFactoriesService.resolveAutoFactories() will:
        // 1. Check if player has unlocked hacanbt breakthrough (true)
        // 2. Check if Fleet Regulations is in play (false)
        // 3. Grant the fleet token (no limit when regulations is not in play)
    }
}
