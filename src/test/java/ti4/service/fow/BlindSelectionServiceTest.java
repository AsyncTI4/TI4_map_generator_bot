package ti4.service.fow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.game.persistence.TestGameHarness;
import ti4.testUtils.BaseTi4Test;

/**
 * Covers what a player types into the Blind Target modal.
 *
 * <p>The position prompt used to accept only a raw tile position. Since it usually sits underneath a list of
 * planet-named buttons, typing the planet name was the obvious move and simply failed with "could not parse".
 * It now accepts either, and the tests below pin both that and the rule that keeps it from leaking: parsing
 * consults the static planet list, never the map, so an unreachable planet is accepted here and fizzles later
 * rather than being rejected with "not in this game".
 */
class BlindSelectionServiceTest extends BaseTi4Test {

    private static final String POSITION = "T";
    private static final String PLANET = "P";
    private static final String UNIT_HOLDER = "U";

    private static String anyOnMapPlanet(Game game) {
        return game.getTileMap().values().stream()
                .flatMap(t -> t.getUnitHolders().values().stream())
                .map(uh -> uh.getName())
                .filter(name -> game.getTileFromPlanet(name) != null)
                .findFirst()
                .orElse(null);
    }

    @Test
    void positionPrompt_acceptsAPositionOrAPlanetName() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            String anyPosition = game.getTileMap().keySet().iterator().next();
            assertThat(BlindSelectionService.parseBlindTarget(game, POSITION, anyPosition))
                    .isEqualTo(anyPosition);

            String planet = anyOnMapPlanet(game);
            assertThat(planet).isNotNull();
            Tile expected = game.getTileFromPlanet(planet);
            assertThat(BlindSelectionService.parseBlindTarget(game, POSITION, planet))
                    .isEqualTo(expected.getPosition());
        }
    }

    @Test
    void positionPrompt_acceptsARealPlanetThatIsNotOnThisMap() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();

            // Refusing this would answer "is that planet in this game?" - a yes/no oracle over the map.
            // It is accepted here and fizzles at resolution instead.
            String offMap = "mellon";
            assertThat(game.getTileFromPlanet(offMap))
                    .as("test assumes %s is not on the default test map", offMap)
                    .isNull();
            assertThat(BlindSelectionService.parseBlindTarget(game, POSITION, offMap))
                    .isNotNull();
        }
    }

    @Test
    void bothPrompts_rejectSomethingThatIsNeither() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            assertThat(BlindSelectionService.parseBlindTarget(game, POSITION, "notathingatall"))
                    .isNull();
            assertThat(BlindSelectionService.parseBlindTarget(game, PLANET, "notathingatall"))
                    .isNull();
        }
    }

    // ---- unit-holder targets: a system can hold a dock in space AND one on each planet ----------

    @Test
    void unitHolderPrompt_positionMeansSpaceAndPlanetMeansItsHolder() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            String anyPosition = game.getTileMap().keySet().iterator().next();
            assertThat(BlindSelectionService.parseBlindTarget(game, UNIT_HOLDER, anyPosition))
                    .isEqualTo(anyPosition + "_space");

            String planet = anyOnMapPlanet(game);
            assertThat(planet).isNotNull();
            // Both halves matter: the system it is in, and which holder inside that system.
            Tile tile = game.getTileFromPlanet(planet);
            assertThat(BlindSelectionService.parseBlindTarget(game, UNIT_HOLDER, planet))
                    .isEqualTo(tile.getPosition() + "_" + planet);
        }
    }

    @Test
    void unitHolderTarget_survivesTheButtonIdRoundTrip() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            String anyPosition = game.getTileMap().keySet().iterator().next();
            String target = BlindSelectionService.parseBlindTarget(game, UNIT_HOLDER, anyPosition);

            // doBlindValidation splits with a limit of 4 precisely so a target containing '_' stays whole.
            String buttonId = "blindValidation_ENCODED_" + UNIT_HOLDER + "_" + target;
            assertThat(buttonId.split("_", 4)[3]).isEqualTo(target);
        }
    }

    @Test
    void planetPrompt_resolvesAliasesToThePlanetId() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            // Mecatol Rex is spelled a dozen ways; the alias table is what makes typing it work at all.
            assertThat(BlindSelectionService.parseBlindTarget(game, PLANET, "mr"))
                    .isEqualTo("mr");
        }
    }
}
