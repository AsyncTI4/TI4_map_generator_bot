package ti4.ai.selfplay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import ti4.ai.AiSettings;
import ti4.game.Game;
import ti4.service.testbed.TestBedPresetService;
import ti4.testUtils.BaseTi4Test;

class SelfPlaySetupTest extends BaseTi4Test {

    // Fewer seats spread around the standard map rather than bunching up on one side.
    @Test
    void spreadsTheHomeSystemsForFewerSeats() {
        assertThat(SelfPlaySetup.homePositions(6)).isEqualTo(TestBedPresetService.DEFAULT_HOME_POSITIONS);
        assertThat(SelfPlaySetup.homePositions(5)).containsExactly("301", "304", "307", "310", "313");
        assertThat(SelfPlaySetup.homePositions(4)).containsExactly("301", "304", "310", "313");
        assertThat(SelfPlaySetup.homePositions(3)).containsExactly("301", "307", "313");
        assertThatThrownBy(() -> SelfPlaySetup.homePositions(2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SelfPlaySetup.homePositions(7)).isInstanceOf(IllegalArgumentException.class);
    }

    // The used home slots keep their placeholder tile for the AI's home system; an unused one would stay on the map as
    // a
    // phantom home system, so it is removed instead.
    @Test
    void leavesNoPlaceholderHomeSystemForAnEmptySeat() {
        Map<String, String> tiles = SelfPlaySetup.tiles(4, new Game());

        assertThat(tiles.get("000")).isEqualTo("112");
        assertThat(List.of("301", "304", "310", "313"))
                .allSatisfy(home -> assertThat(tiles.get(home)).isEqualTo("0g"));
        assertThat(tiles.get("307")).isEqualTo("-1");
        assertThat(tiles.get("316")).isEqualTo("-1");
    }

    // "Thunder Dreaming", the six-player map in the Thunder's Edge rulebook. Neighbours read off the rulebook picture
    // must be neighbours on the bot's board: Gral (43) touches 107, 109, 26, 28, 35 and 101; 30 touches 37 and 47.
    @Test
    void laysOutTheThunderDreamingMapFromTheRulebook() {
        Map<String, String> tiles = SelfPlaySetup.tiles(6, new Game());
        Map<String, String> positionOf = new java.util.HashMap<>();
        tiles.forEach((position, tile) -> positionOf.putIfAbsent(tile, position));

        assertThat(tiles.get("000")).isEqualTo("112");
        assertThat(tiles.values().stream().filter("0g"::equals)).hasSize(6);
        assertThat(neighbours(positionOf, "43")).contains("107", "109", "26", "28", "35", "101");
        assertThat(neighbours(positionOf, "30")).contains("37", "47", "112", "98", "44", "35");
        assertThat(neighbours(positionOf, "115")).contains("29", "28", "46");
    }

    private static List<String> neighbours(Map<String, String> positionOf, String tile) {
        Map<String, String> tileAt = new java.util.HashMap<>();
        positionOf.forEach((t, position) -> tileAt.put(position, t));
        return ti4.model.MapPositionMapper.getAdjacentTilePositions(positionOf.get(tile)).stream()
                .map(tileAt::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Test
    void givesTwoStrategyCardsEachAtFourSeatsOrFewer() {
        assertThat(SelfPlaySetup.strategyCardsPerPlayer(3)).isEqualTo(2);
        assertThat(SelfPlaySetup.strategyCardsPerPlayer(4)).isEqualTo(2);
        assertThat(SelfPlaySetup.strategyCardsPerPlayer(5)).isEqualTo(1);
        assertThat(SelfPlaySetup.strategyCardsPerPlayer(6)).isEqualTo(1);
    }

    @Test
    void picksDistinctDefaultFactions() {
        List<String> factions = SelfPlaySetup.pickFactions(6, new Random(7));

        assertThat(factions).hasSize(6).doesNotHaveDuplicates();
        assertThat(AiSettings.DEFAULT_FACTIONS).containsAll(factions);
    }

    // Names follow the bot's game-name rules and stay clear of the numbered pbd and fow series.
    @Test
    void acceptsOnlyPlainLowercaseGameNames() {
        assertThat(SelfPlaySetup.isValidName("aiwatch7")).isTrue();
        assertThat(List.of("AiWatch", "ai-watch", "pbd123", "fow4", "ab", "a234567890123456789012"))
                .allSatisfy(name -> assertThat(SelfPlaySetup.isValidName(name)).isFalse());
    }

    // A human at the table takes one of the seats and the AIs fill the rest, every faction different.
    @Test
    void seatsTheHumanAmongTheAiFactions() {
        List<String> opponents = SelfPlaySetup.pickOpponentFactions(5, "nekro", new Random(3));

        SelfPlaySetup.Table table = SelfPlaySetup.Table.withHuman("nekro", "red", opponents, new Random(3));

        assertThat(table.hasHuman()).isTrue();
        assertThat(table.humanColor()).isEqualTo("red");
        assertThat(table.factions()).hasSize(6).doesNotHaveDuplicates();
        assertThat(table.factions().get(table.humanSeat())).isEqualTo("nekro");
        assertThat(table.factions()).containsAll(opponents);
        assertThat(table.speakerSeat()).isBetween(0, 5);
    }

    @Test
    void neverGivesAnOpponentTheHumansFaction() {
        for (String humanFaction : List.of("nekro", "yin", "l1z1x")) {
            for (int seed = 0; seed < 20; seed++) {
                List<String> opponents = SelfPlaySetup.pickOpponentFactions(5, humanFaction, new Random(seed));

                assertThat(opponents).hasSize(5).doesNotHaveDuplicates().doesNotContain(humanFaction);
                assertThat(AiSettings.DEFAULT_FACTIONS).containsAll(opponents);
            }
        }
    }

    @Test
    void anAllAiTableHasNoHumanAndTheFirstSeatSpeaks() {
        SelfPlaySetup.Table table = SelfPlaySetup.Table.allAi(List.of("nekro", "sol", "hacan"));

        assertThat(table.hasHuman()).isFalse();
        assertThat(table.speakerSeat()).isZero();
    }
}
