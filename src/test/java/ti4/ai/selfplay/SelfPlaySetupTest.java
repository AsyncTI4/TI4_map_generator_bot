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

        assertThat(tiles.get("000")).isEqualTo("18");
        assertThat(List.of("301", "304", "310", "313"))
                .allSatisfy(home -> assertThat(tiles.get(home)).isEqualTo("0g"));
        assertThat(tiles.get("307")).isEqualTo("-1");
        assertThat(tiles.get("316")).isEqualTo("-1");
    }

    @Test
    void givesTwoStrategyCardsEachAtFourSeatsOrFewer() {
        assertThat(SelfPlaySetup.strategyCardsPerPlayer(3)).isEqualTo(2);
        assertThat(SelfPlaySetup.strategyCardsPerPlayer(4)).isEqualTo(2);
        assertThat(SelfPlaySetup.strategyCardsPerPlayer(5)).isEqualTo(1);
        assertThat(SelfPlaySetup.strategyCardsPerPlayer(6)).isEqualTo(1);
    }

    @Test
    void picksDistinctSupportedFactions() {
        List<String> factions = SelfPlaySetup.pickFactions(6, new Random(7));

        assertThat(factions).hasSize(6).doesNotHaveDuplicates();
        assertThat(AiSettings.SUPPORTED_FACTIONS).containsAll(factions);
    }

    // Names follow the bot's game-name rules and stay clear of the numbered pbd and fow series.
    @Test
    void acceptsOnlyPlainLowercaseGameNames() {
        assertThat(SelfPlaySetup.isValidName("aiwatch7")).isTrue();
        assertThat(List.of("AiWatch", "ai-watch", "pbd123", "fow4", "ab", "a234567890123456789012"))
                .allSatisfy(name -> assertThat(SelfPlaySetup.isValidName(name)).isFalse());
    }
}
