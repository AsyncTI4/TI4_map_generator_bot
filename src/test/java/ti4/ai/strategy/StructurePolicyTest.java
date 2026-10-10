package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class StructurePolicyTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
    }

    // A forward dock does not need 3 resources, but prefers them: Quann (2/1) is good enough on its own, and Lodor
    // (3/1) wins when both are there.
    @Test
    void prefersARichPlanetForAForwardDock() {
        test.place("25", "101");
        test.nekro.addPlanet("quann");
        assertThat(StructurePolicy.next(test.game, test.nekro)).contains(StructurePolicy.SPACE_DOCK);

        test.place("26", "102");
        test.nekro.addPlanet("lodor");

        assertThat(StructurePolicy.planetFor(
                        test.game, test.nekro, StructurePolicy.SPACE_DOCK, List.of("quann", "lodor")))
                .contains("lodor");
    }

    // A planet worth a single resource gets a PDS rather than a dock that could never build 4 units.
    @Test
    void placesNoForwardDockOnAPoorPlanet() {
        test.place("19", "101");
        test.nekro.addPlanet("wellon");

        assertThat(StructurePolicy.next(test.game, test.nekro)).contains(StructurePolicy.PDS);
    }

    // Forward docks belong in the seat's own slice: Lodor next to home beats Atlas across the board, though both
    // are worth 3 resources.
    @Test
    void prefersADockInsideItsSlice() {
        test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("lodor");
        test.place("64", "310");
        test.nekro.addPlanet("atlas");

        assertThat(StructurePolicy.planetFor(
                        test.game, test.nekro, StructurePolicy.SPACE_DOCK, List.of("atlas", "lodor")))
                .contains("lodor");
    }

    // A home system with a second planet takes a second dock: Jol-Nar's dock on Nar plus one on Jol makes every
    // activation of home produce far more.
    @Test
    void placesASecondDockOnAnotherHomePlanet() {
        Player jolnar = jolNarAtHome();

        assertThat(StructurePolicy.next(test.game, jolnar)).contains(StructurePolicy.SPACE_DOCK);
        assertThat(StructurePolicy.planetFor(test.game, jolnar, StructurePolicy.SPACE_DOCK, List.of("jol")))
                .contains("jol");
    }

    // Nekro's home has a single planet, so without a rich planet elsewhere there is nowhere worth a dock.
    @Test
    void wantsNoSecondDockInASinglePlanetHome() {
        assertThat(StructurePolicy.next(test.game, test.nekro)).contains(StructurePolicy.PDS);
    }

    // Between a second home dock on Jol (1 resource) and a forward dock on Lodor (3), the richer planet adds more
    // production.
    @Test
    void prefersTheSiteThatAddsMoreProduction() {
        Player jolnar = jolNarAtHome();
        test.place("26", "101");
        jolnar.addPlanet("lodor");

        assertThat(StructurePolicy.planetFor(test.game, jolnar, StructurePolicy.SPACE_DOCK, List.of("jol", "lodor")))
                .contains("lodor");
    }

    private Player jolNarAtHome() {
        Player jolnar = test.addSeat("200000000000000002", "jolnar", "green");
        Tile home = test.place("12", "304");
        jolnar.addPlanet("jol");
        jolnar.addPlanet("nar");
        test.units(home, "nar", jolnar, UnitType.Spacedock, 1);
        return jolnar;
    }
}
