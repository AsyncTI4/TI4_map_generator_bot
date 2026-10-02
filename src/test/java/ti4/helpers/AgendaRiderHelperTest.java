package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.game.persistence.TestGameHarness;
import ti4.testUtils.BaseTi4Test;

/**
 * Pins the pagination fix for the fog agenda rider planet list ({@code AgendaRiderHelper.getPlanetOutcomeButtons},
 * displayed by the {@code planetRider} handler). Same unbounded shape as the vote-outcome list this
 * pagination pattern was originally built for - a known-planet set can exceed 25 through alliance
 * stat-visibility alone - but this consumer can't route through the shared {@code PlanetTargetService}
 * pagination because its button ids are shaped {@code prefix + "rider_planet;" + planet + "_" + rider}, not
 * the {@code prefix + "_" + planetId} shape that service hardcodes.
 */
class AgendaRiderHelperTest extends BaseTi4Test {

    private static final String RIDER = "Test Rider";

    @Test
    void paginatedPlanetOutcomeButtons_paginatesPastTwentyFive() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            Player actor = game.getRealPlayers().getFirst();

            // alwaysInclude bypasses fog knowledge entirely, so this is a deterministic way to force a large
            // candidate set regardless of what the actor has actually scouted.
            List<String> onMapPlanets = game.getTileMap().values().stream()
                    .flatMap(t -> t.getUnitHolders().values().stream())
                    .filter(uh -> uh instanceof Planet p && !p.isSpaceStation(game))
                    .map(UnitHolder::getName)
                    .distinct()
                    .limit(26)
                    .toList();
            assertThat(onMapPlanets)
                    .as("default test map should have at least 26 non-space-station planets")
                    .hasSizeGreaterThanOrEqualTo(26);
            for (String planet : onMapPlanets) {
                game.setCurrentAgendaVote(planet, "x_1");
            }

            String navPrefix = actor.factionButtonChecker() + "planetRider_" + actor.getColor() + "_" + RIDER;
            List<Button> page1 = AgendaRiderHelper.paginatedPlanetOutcomeButtons(
                    null, actor, game, actor.factionButtonChecker(), RIDER, navPrefix, 0);

            assertThat(page1).hasSizeLessThanOrEqualTo(25);
            Button nextPage = page1.stream()
                    .filter(b -> b.getCustomId() != null && b.getCustomId().startsWith(navPrefix + "page"))
                    .findFirst()
                    .orElse(null);
            assertThat(nextPage)
                    .as("a page-25+ list must offer a Next Page button")
                    .isNotNull();
            assertThat(page1)
                    .as("this flow never offers Blind Target - its id shape can't express one")
                    .noneMatch(b -> b.getCustomId() != null && b.getCustomId().startsWith("blindSelection~MDL"));

            List<Button> page2 = AgendaRiderHelper.paginatedPlanetOutcomeButtons(
                    null, actor, game, actor.factionButtonChecker(), RIDER, navPrefix, 1);
            assertThat(page2).isNotEmpty();
        }
    }

    @Test
    void paginatedPlanetOutcomeButtons_belowTheCapIsUnpaginated() {
        // Same isolation trick as VoteButtonHandlerTest's equivalent test: the default-map fixture's first
        // player already knows more than 24 planets from ordinary fog visibility and alliances alone, so a
        // fresh single-player game with a handful of borrowed real tiles is the only reliable way to pin a
        // small known set.
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game sourceMap = harness.load();
            List<Tile> someTiles = sourceMap.getTileMap().values().stream()
                    .filter(t -> t.getUnitHolders().values().stream()
                            .anyMatch(uh -> uh instanceof Planet p && !p.isSpaceStation(sourceMap)))
                    .limit(5)
                    .toList();
            assertThat(someTiles)
                    .as("default test map should have at least 5 planet-bearing tiles")
                    .hasSize(5);

            Game isolated = new Game();
            isolated.setName("rider-pagination-boundary-test");
            Player actor = isolated.addPlayer("test-user-id", "winnu");
            actor.setFaction("winnu");
            actor.setColor("red");
            isolated.setFowMode(true);

            for (Tile tile : someTiles) {
                isolated.setTile(tile);
                String planet = tile.getUnitHolders().values().stream()
                        .filter(uh -> uh instanceof Planet p && !p.isSpaceStation(isolated))
                        .map(UnitHolder::getName)
                        .findFirst()
                        .orElseThrow();
                isolated.setCurrentAgendaVote(planet, "x_1");
            }

            String navPrefix = actor.factionButtonChecker() + "planetRider_" + actor.getColor() + "_" + RIDER;
            List<Button> buttons = AgendaRiderHelper.paginatedPlanetOutcomeButtons(
                    null, actor, isolated, actor.factionButtonChecker(), RIDER, navPrefix, 0);

            // At most 5 planets: comfortably under the 25 cap.
            assertThat(buttons).hasSizeLessThanOrEqualTo(5);
            assertThat(buttons)
                    .as("no page-nav button when the list already fits in one message")
                    .noneMatch(b -> b.getCustomId() != null && b.getCustomId().startsWith(navPrefix + "page"));
        }
    }
}
