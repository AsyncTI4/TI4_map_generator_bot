package ti4.discord.interactions.commands.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class GameStatisticsFiltererTest extends BaseTi4Test {

    private static final Set<String> YSSARIL_AND_RAL_NEL = Set.of("yssaril", "ralnel");

    @Test
    void shouldFindANamedFactionAtTheTable() {
        assertThat(GameStatisticsFilterer.hasAnyFaction(
                        gameWithFactions("sol", "yssaril", "xxcha"), YSSARIL_AND_RAL_NEL))
                .isTrue();
        assertThat(GameStatisticsFilterer.hasAnyFaction(gameWithFactions("sol", "ralnel"), YSSARIL_AND_RAL_NEL))
                .isTrue();
    }

    @Test
    void shouldIgnoreTheCaseOfTheAliasOnTheTable() {
        assertThat(GameStatisticsFilterer.hasAnyFaction(gameWithFactions("YSSARIL"), YSSARIL_AND_RAL_NEL))
                .isTrue();
    }

    @Test
    void shouldTreatAReworkedAliasAsADifferentFaction() {
        // miltymodyssaril and pi_yssaril are homebrew reworks. Matching them on the name inside the
        // alias would quietly widen every caller's set to factions they never asked for.
        assertThat(GameStatisticsFilterer.hasAnyFaction(gameWithFactions("miltymodyssaril"), YSSARIL_AND_RAL_NEL))
                .isFalse();
        assertThat(GameStatisticsFilterer.hasAnyFaction(gameWithFactions("pi_yssaril"), YSSARIL_AND_RAL_NEL))
                .isFalse();
    }

    @Test
    void shouldLeaveEveryOtherTableAlone() {
        assertThat(GameStatisticsFilterer.hasAnyFaction(
                        gameWithFactions("sol", "xxcha", "hacan", "arborec", "letnev", "naalu"), YSSARIL_AND_RAL_NEL))
                .isFalse();
        assertThat(GameStatisticsFilterer.hasAnyFaction(gameWithFactions(), YSSARIL_AND_RAL_NEL))
                .isFalse();
        assertThat(GameStatisticsFilterer.hasAnyFaction(gameWithFactions("sol", "yssaril"), Set.of()))
                .isFalse();
    }

    private static Game gameWithFactions(String... factions) {
        // A player needs a color as well as a faction before the game counts them, and getFactions()
        // only reports the players it counts.
        List<String> colors = List.of("red", "blue", "green", "yellow", "purple", "orange");
        Game game = new Game();
        for (int i = 0; i < factions.length; i++) {
            Player player = game.addPlayer(factions[i], factions[i]);
            player.setFaction(factions[i]);
            player.setColor(colors.get(i));
        }
        return game;
    }
}
