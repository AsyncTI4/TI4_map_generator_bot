package ti4.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.statistics.FogEndedGameStatisticsService.FogTally;
import ti4.testUtils.BaseTi4Test;

class FogEndedGameStatisticsServiceTest extends BaseTi4Test {

    @Test
    void onlyEndedFogAndLightFogGamesQualify() {
        Game running = fogGame("running", false);
        Game ended = fogGame("ended", true);
        Game endedNormal = new Game();
        endedNormal.setHasEnded(true);
        Game endedLightFog = new Game();
        endedLightFog.setLightFogMode(true);
        endedLightFog.setHasEnded(true);

        assertThat(FogEndedGameStatisticsService.isEndedFogGame(running)).isFalse();
        assertThat(FogEndedGameStatisticsService.isEndedFogGame(ended)).isTrue();
        assertThat(FogEndedGameStatisticsService.isEndedFogGame(endedNormal)).isFalse();
        assertThat(FogEndedGameStatisticsService.isEndedFogGame(endedLightFog)).isTrue();
    }

    @Test
    void tallyCountsVariantsOptionsAndFactions() {
        Game plain = fogGame("plain", true);
        addPlayer(plain, "sol", "red");
        addPlayer(plain, "hacan", "blue");
        plain.setFowOption(FOWOption.HIDE_MAP, true);

        Game plus = fogGame("plus", true);
        addPlayer(plus, "sol", "green");
        plus.setFowOption(FOWOption.FOW_PLUS, true);
        plus.setFowOption(FOWOption.HIDE_MAP, true);

        FogTally tally = new FogTally();
        tally.add(plain);
        tally.add(plus);

        assertThat(tally.games()).isEqualTo(2);
        assertThat(tally.variants()).containsEntry("Fog", 1).containsEntry("Fog+", 1);
        assertThat(tally.options()).containsEntry(FOWOption.HIDE_MAP, 2).containsEntry(FOWOption.FOW_PLUS, 1);
        assertThat(tally.factionGames()).containsEntry("sol", 2).containsEntry("hacan", 1);
        assertThat(tally.report()).contains("**Games:** 2").contains("Hide Unexplored Map: 2/2 (100%)");
    }

    @Test
    void emptyTallyExplainsThatNothingMatched() {
        assertThat(new FogTally().report()).isEqualTo("No ended fog games matched the selected filters.");
    }

    private static Game fogGame(String name, boolean ended) {
        Game game = new Game();
        game.setName(name);
        game.setFowMode(true);
        game.setHasEnded(ended);
        return game;
    }

    private static void addPlayer(Game game, String faction, String color) {
        Player player = game.addPlayer(faction + "-id", faction + "-user");
        player.setFaction(faction);
        player.setColor(color);
    }
}
