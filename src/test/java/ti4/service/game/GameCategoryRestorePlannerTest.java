package ti4.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import ti4.service.game.GameCategoryRestorePlanner.CategoryRange;
import ti4.service.game.GameCategoryRestorePlanner.Placement;

class GameCategoryRestorePlannerTest {

    private static final Function<String, String> NAME = Function.identity();

    @Test
    void readsGameNumbersOnlyFromPbdChannels() {
        assertThat(GameCategoryRestorePlanner.gameNumber("pbd30801-actions")).hasValue(30_801);
        assertThat(GameCategoryRestorePlanner.gameNumber("pbd30801-the-fun-name"))
                .hasValue(30_801);
        assertThat(GameCategoryRestorePlanner.gameNumber("pbd30801")).hasValue(30_801);
        assertThat(GameCategoryRestorePlanner.gameNumber("pbd23536c-actions")).hasValue(23_536);
        assertThat(GameCategoryRestorePlanner.gameNumber("pbd24323d-habs-win-it-all"))
                .hasValue(24_323);
        assertThat(GameCategoryRestorePlanner.gameNumber("pbd-general")).isEmpty();
        assertThat(GameCategoryRestorePlanner.gameNumber("fow123-actions")).isEmpty();
    }

    @Test
    void parsesRangedAndSingleGameCategories() {
        assertThat(GameCategoryRestorePlanner.parseCategory("PBD #22195-22199"))
                .contains(new CategoryRange("PBD #22195-22199", 22_195, 22_199));
        assertThat(GameCategoryRestorePlanner.parseCategory("PBD #500"))
                .contains(new CategoryRange("PBD #500", 500, 500));
        assertThat(GameCategoryRestorePlanner.parseCategory("The in-limbo PBD Archive"))
                .isEmpty();
    }

    @Test
    void infersTheMostCommonCategoryWidth() {
        List<CategoryRange> ranges = List.of(range(100, 104), range(105, 109), range(110, 119));
        assertThat(GameCategoryRestorePlanner.inferGamesPerCategory(ranges, 10)).isEqualTo(5);
        assertThat(GameCategoryRestorePlanner.inferGamesPerCategory(List.of(), 10))
                .isEqualTo(10);
    }

    @Test
    void groupsOrphansIntoRecreatedCategoriesInGameOrder() {
        // The cron deleted PBD #30800-30804 and its channels were left at the top of the server.
        List<String> orphans =
                List.of("pbd30803-actions", "pbd30801-actions", "pbd30801-fun", "pbd30803-fun", "pbd30815-actions");

        List<Placement<String>> plan = GameCategoryRestorePlanner.plan(orphans, NAME, List.of(), 5);

        assertThat(plan)
                .containsExactly(
                        new Placement<>(
                                "PBD #30800-30804",
                                true,
                                List.of("pbd30801-fun", "pbd30801-actions", "pbd30803-fun", "pbd30803-actions")),
                        new Placement<>("PBD #30815-30819", true, List.of("pbd30815-actions")));
    }

    @Test
    void placesRematchesInTheOriginalGamesCategoryAfterIt() {
        // Rematches keep the original number with a letter suffix, e.g. pbd23536c.
        List<String> orphans = List.of("pbd23536c-actions", "pbd23536-actions", "pbd23536c-redshift", "pbd23536-fun");

        List<Placement<String>> plan = GameCategoryRestorePlanner.plan(orphans, NAME, List.of(), 5);

        assertThat(plan)
                .containsExactly(new Placement<>(
                        "PBD #23535-23539",
                        true,
                        List.of("pbd23536-fun", "pbd23536-actions", "pbd23536c-redshift", "pbd23536c-actions")));
    }

    @Test
    void usesAnExistingCategoryThatCoversTheGame() {
        List<Placement<String>> plan =
                GameCategoryRestorePlanner.plan(List.of("pbd107-actions", "lounge"), NAME, List.of(range(100, 109)), 5);

        assertThat(plan).containsExactly(new Placement<>("PBD #100-109", false, List.of("pbd107-actions")));
    }

    @Test
    void newCategoriesNeverOverlapExistingOnes() {
        // A 10-wide guess for game 103 would be 100-109, but 105-109 still exists.
        List<Placement<String>> plan =
                GameCategoryRestorePlanner.plan(List.of("pbd103-actions"), NAME, List.of(range(105, 109)), 10);

        assertThat(plan).extracting(Placement::categoryName).containsExactly("PBD #100-104");
    }

    @Test
    void sortsGameCategoriesWithinTheirOwnSlots() {
        List<String> current = List.of("Welcome", "PBD #200-204", "Lounge", "PBD #100-104", "PBD #150-154");

        assertThat(GameCategoryRestorePlanner.categoryOrder(current, NAME))
                .containsExactly("Welcome", "PBD #100-104", "Lounge", "PBD #150-154", "PBD #200-204");
    }

    private static CategoryRange range(int lower, int upper) {
        return new CategoryRange("PBD #" + lower + "-" + upper, lower, upper);
    }
}
