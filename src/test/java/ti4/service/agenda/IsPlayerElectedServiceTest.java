package ti4.service.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.map.Game;
import ti4.map.Player;

class IsPlayerElectedServiceTest {

    @Test
    void shouldShowPlayerIsNotElectedIfPlayerIsNull() {
        boolean isPlayerElected = IsPlayerElectedService.isPlayerElected(new Game(), null, "testAgenda");

        assertThat(isPlayerElected).isFalse();
    }

    @Test
    void shouldShowPlayerIsNotElectedIfLawsAreDisabled() {
        var game = new Game();
        game.setStoredValue("lawsDisabled", "yes");
        var player = new Player("userId", "userName", game);

        boolean isPlayerElected = IsPlayerElectedService.isPlayerElected(game, player, "testAgenda");

        assertThat(isPlayerElected).isFalse();
    }

    @Test
    void shouldCheckIfPlayerIsElectedAndReturnFalseIfThereIsNoMatchingLawInfo() {
        var game = new Game();
        game.setLaws(Map.of("testAgenda", 1));
        var player = new Player("userId", "userName", game);

        boolean isPlayerElected = IsPlayerElectedService.isPlayerElected(game, player, "testAgenda");

        assertThat(isPlayerElected).isFalse();
    }

    @Test
    void shouldCheckIfPlayerIsElectedAndReturnFalseIfNotElected() {
        var game = new Game();
        game.setLaws(Map.of("testAgenda", 1));
        game.setLawsInfo(Map.of("testAgenda", "notTheSameFactionOrColor"));
        var player = new Player("userId", "userName", game);
        player.setFaction("faction");

        boolean isPlayerElected = IsPlayerElectedService.isPlayerElected(game, player, "testAgenda");

        assertThat(isPlayerElected).isFalse();
    }

    @Test
    void shouldCheckIfPlayerIsElectedAndReturnTrueIfElectedPlayerHasSameFaction() {
        var game = new Game();
        game.setLaws(Map.of("testAgenda", 1));
        game.setLawsInfo(Map.of("testAgenda", "Faction"));
        var player = new Player("userId", "userName", game);
        player.setFaction("faction");

        boolean isPlayerElected = IsPlayerElectedService.isPlayerElected(game, player, "testAgenda");

        assertThat(isPlayerElected).isTrue();
    }

    // TODO: This should be added back when Player.getColor can be called without the JSON being loaded into memory
    // (e.g. it can be mocked)
    //    @Test
    //    void shouldCheckIfPlayerIsElectedAndReturnTrueIfElectedHasSameColor() {
    //        var game = new Game();
    //        game.setLaws(Map.of("testAgenda", 1));
    //        game.setLawsInfo(Map.of("testAgenda", "blue"));
    //        var player = new Player("userId", "userName", game);
    //        player.setColor("blue");
    //
    //        boolean isPlayerElected = IsPlayerElectedService.isPlayerElected(game, player, "testAgenda");
    //
    //        assertThat(isPlayerElected).isTrue();
    //    }
}
