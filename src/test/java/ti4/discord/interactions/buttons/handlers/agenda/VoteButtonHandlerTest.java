package ti4.discord.interactions.buttons.handlers.agenda;

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
 * Pins the pagination fix for the fog Elect Planet vote list: known planets are not naturally bounded (an
 * ally's whole holding list can enter the set through stat-visibility alone), so a big pod routinely exceeds
 * Discord's 25-buttons-per-message cap. Before this, a voter with more than 25 known planets would have some
 * candidates - including the Blind Target button itself, which sorts last - silently pushed onto a second,
 * unlabelled message.
 */
class VoteButtonHandlerTest extends BaseTi4Test {

    @Test
    void fogPlanetOutcomeButtons_paginatesPastTwentyFive() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setFowMode(true);
            game.setCurrentAgendaInfo("agenda_Elect Planet_1_core_mining");
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

            List<Button> page1 = VoteButtonHandler.fogPlanetOutcomeButtons(game, actor, "outcome");

            assertThat(page1).hasSizeLessThanOrEqualTo(25);
            Button nextPage = page1.stream()
                    .filter(b -> b.getCustomId() != null && b.getCustomId().startsWith("votePlanetPage_outcome|page"))
                    .findFirst()
                    .orElse(null);
            assertThat(nextPage)
                    .as("a page-25+ list must offer a Next Page button")
                    .isNotNull();

            var parsed = VoteButtonHandler.parseVotePageID(nextPage.getCustomId());
            assertThat(parsed).isNotNull();
            List<Button> page2 = VoteButtonHandler.votePlanetPageButtons(game, actor, parsed);
            assertThat(page2).isNotEmpty();
            // Blind Target sorts last in the underlying list, so with 26+ real candidates it lands on page 2 -
            // exactly the case that used to go missing.
            assertThat(page2)
                    .anyMatch(b -> b.getCustomId() != null && b.getCustomId().startsWith("blindSelection~MDL"));
        }
    }

    @Test
    void fogPlanetOutcomeButtons_belowTheCapIsUnpaginated() {
        // The default-map fixture is a real, advanced saved game: its first player already knows more than 24
        // planets from ordinary fog visibility and alliances alone, so it cannot stand in for the "small
        // known set" case. A fresh, single-player game with a handful of borrowed real tiles can: no other
        // players means no stat-visibility contamination, and no fog memory means no scouted-tile contamination
        // - only the 5 planets seeded below are ever candidates.
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
            isolated.setName("vote-pagination-boundary-test");
            Player actor = isolated.addPlayer("test-user-id", "winnu");
            actor.setFaction("winnu");
            actor.setColor("red");
            isolated.setFowMode(true);
            isolated.setCurrentAgendaInfo("agenda_Elect Planet_1_core_mining");

            for (Tile tile : someTiles) {
                isolated.setTile(tile);
                String planet = tile.getUnitHolders().values().stream()
                        .filter(uh -> uh instanceof Planet p && !p.isSpaceStation(isolated))
                        .map(UnitHolder::getName)
                        .findFirst()
                        .orElseThrow();
                isolated.setCurrentAgendaVote(planet, "x_1");
            }

            List<Button> buttons = VoteButtonHandler.fogPlanetOutcomeButtons(isolated, actor, "outcome");

            // At most 5 planets + Blind Target: comfortably under the 25 cap.
            assertThat(buttons).hasSizeLessThanOrEqualTo(6);
            assertThat(buttons)
                    .as("no page-nav button when the list already fits in one message")
                    .noneMatch(b -> b.getCustomId() != null && b.getCustomId().contains("votePlanetPage_"));
        }
    }
}
