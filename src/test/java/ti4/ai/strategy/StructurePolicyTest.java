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

    // With the Fracture in play, a dock on an ingress in the seat's own slice builds the fleet for Styx where the march
    // starts (the human Ral Nel docked on its ingress in round 1), even on a planet worth a single resource.
    @Test
    void prefersADockOnItsOwnIngressWhileStyxIsOpen() {
        ti4.service.map.FractureService.spawnFracture(null, test.game);
        Tile ingress = test.place("19", AiTestGame.neighbourOf(AiTestGame.HOME));
        ingress.addToken(ti4.helpers.Constants.TOKEN_INGRESS, "space");
        test.nekro.addPlanet("wellon");
        assertThat(StructurePolicy.next(test.game, test.nekro)).contains(StructurePolicy.SPACE_DOCK);

        test.place("26", "102");
        test.nekro.addPlanet("lodor");

        assertThat(StructurePolicy.planetFor(
                        test.game, test.nekro, StructurePolicy.SPACE_DOCK, List.of("lodor", "wellon")))
                .contains("wellon");
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

    // A second home dock is only wanted once home production is the limit: Jol-Nar's dock on Nar alone already covers
    // the 3 resources of its home, so a dock on Jol adds nothing and the next structure is a PDS.
    @Test
    void wantsNoSecondHomeDockWhileHomeProductionCoversTheResources() {
        Player jolnar = jolNarAtHome();

        assertThat(StructurePolicy.isDockSite(
                        test.game, jolnar, test.game.getPlanetsInfo().get("jol")))
                .isFalse();
        assertThat(StructurePolicy.next(test.game, jolnar)).contains(StructurePolicy.PDS);
    }

    // With resources beyond what Nar's dock produces and no forward planet worth a dock, Jol-Nar's second home dock
    // makes every activation of home produce more, and next() agrees with planetFor().
    @Test
    void placesASecondDockOnAnotherHomePlanetWhenHomeProductionIsTheLimit() {
        Player jolnar = jolNarAtHome();
        test.place("19", "101");
        jolnar.addPlanet("wellon");
        test.place("22", "102");
        jolnar.addPlanet("tarmann");
        test.place("21", "103");
        jolnar.addPlanet("thibah");

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

    // New Albion (1 resource) is no dock site, but Starpoint's dock plus a dock on New Albion makes 5 + 3 = 8
    // production in one system, which is Produce en Masse: the build is a dock and Construction is worth following.
    @Test
    void buildsTheDockThatCompletesProduceEnMass() {
        Tile tile = test.place("27", "101");
        test.nekro.addPlanet("starpoint");
        test.nekro.addPlanet("newalbion");
        test.units(tile, "starpoint", test.nekro, UnitType.Spacedock, 1);
        assertThat(StructurePolicy.next(test.game, test.nekro)).contains(StructurePolicy.PDS);
        assertThat(StructurePolicy.nearsStructureObjective(test.game, test.nekro))
                .isFalse();

        test.nekro.setSecret("pem");

        assertThat(StructurePolicy.next(test.game, test.nekro)).contains(StructurePolicy.SPACE_DOCK);
        assertThat(StructurePolicy.nearsStructureObjective(test.game, test.nekro))
                .isTrue();
        assertThat(StructurePolicy.planetFor(
                        test.game, test.nekro, StructurePolicy.SPACE_DOCK, List.of("starpoint", "newalbion")))
                .contains("newalbion");
    }

    // With Starpoint's dock alone nothing a single dock does reaches 8, so Produce en Masse changes nothing.
    @Test
    void ignoresProduceEnMassWhenNoDockReachesIt() {
        Tile tile = test.place("27", "101");
        test.nekro.addPlanet("starpoint");
        test.units(tile, "starpoint", test.nekro, UnitType.Spacedock, 1);
        test.nekro.setSecret("pem");

        assertThat(StructurePolicy.next(test.game, test.nekro)).contains(StructurePolicy.PDS);
        assertThat(StructurePolicy.nearsStructureObjective(test.game, test.nekro))
                .isFalse();
    }

    // Hacan's home holds Arretze (2), Hercant (1) and Kamdorn (0): docks on all three make 4 + 3 + 2 = 9. Two more
    // docks
    // are a build the seat takes, but not one Construction is followed for.
    @Test
    void buildsTowardProduceEnMassTwoDocksAwayWithoutFollowingConstruction() {
        Player hacan = test.addSeat("200000000000000003", "hacan", "red");
        Tile home = test.place("16", "304");
        hacan.addPlanet("arretze");
        hacan.addPlanet("hercant");
        hacan.addPlanet("kamdorn");
        test.units(home, "arretze", hacan, UnitType.Spacedock, 1);
        hacan.setSecret("pem");

        assertThat(StructurePolicy.next(test.game, hacan)).contains(StructurePolicy.SPACE_DOCK);
        assertThat(StructurePolicy.nearsStructureObjective(test.game, hacan)).isFalse();
        assertThat(StructurePolicy.planetFor(
                        test.game, hacan, StructurePolicy.SPACE_DOCK, List.of("arretze", "hercant", "kamdorn")))
                .contains("hercant");
    }

    // With Space Dock II a dock on Jol makes the home system 6 + 5 = 11, so the second home planet beats a richer
    // forward planet that only lifts a different system.
    @Test
    void prefersTheSecondHomePlanetWhileProduceEnMassIsOneDockAway() {
        Player jolnar = jolNarAtHome();
        test.place("26", "101");
        jolnar.addPlanet("lodor");
        jolnar.addTech("sd2");
        jolnar.setSecret("pem");

        assertThat(StructurePolicy.nearsStructureObjective(test.game, jolnar)).isTrue();
        assertThat(StructurePolicy.planetFor(test.game, jolnar, StructurePolicy.SPACE_DOCK, List.of("jol", "lodor")))
                .contains("jol");
    }

    // The 3 resources of Lodor beat the 1 of Jol for the dock, and a second home dock is no site at all while a forward
    // planet is worth more.
    @Test
    void ranksAForwardSiteAboveASecondHomeDock() {
        Player jolnar = jolNarAtHome();
        test.place("26", "101");
        jolnar.addPlanet("lodor");

        assertThat(StructurePolicy.isDockSite(
                        test.game, jolnar, test.game.getPlanetsInfo().get("jol")))
                .isFalse();
        assertThat(StructurePolicy.planetFor(test.game, jolnar, StructurePolicy.SPACE_DOCK, List.of("jol", "lodor")))
                .contains("lodor");
    }

    @Test
    void placesAPdsOutsideHomeWhenAnotherPlanetIsLegal() {
        test.place("26", "101");
        test.nekro.addPlanet("lodor");

        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.PDS, List.of("mordaiii", "lodor")))
                .contains("lodor");
    }

    @Test
    void placesAPdsAtHomeWhenNoOtherPlanetIsLegal() {
        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.PDS, List.of("mordaiii")))
                .contains("mordaiii");
    }

    @Test
    void placesAPdsOnAPlanetWithoutOurStructuresBeforeOneWithAPds() {
        Tile lodor = test.place("26", "101");
        test.nekro.addPlanet("lodor");
        test.place("25", "102");
        test.nekro.addPlanet("quann");
        test.units(lodor, "lodor", test.nekro, UnitType.Pds, 1);

        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.PDS, List.of("lodor", "quann")))
                .contains("quann");
    }

    // Quann sits between the two homes while Lodor is on the home side of the board: with neither holding a structure,
    // the PDS goes to the border planet even though Lodor is worth more.
    @Test
    void placesAPdsOnTheFrontierBeforeTheBackline() {
        test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("lodor");
        String border = AiTestGame.neighbourOf(AiTestGame.neighbourOf(AiTestGame.HOME));
        test.place("25", border);
        test.nekro.addPlanet("quann");
        test.solHomeBeside(border);

        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.PDS, List.of("lodor", "quann")))
                .contains("quann");
    }

    @Test
    void placesAPdsNextToEnemyShipsBeforeTheBackline() {
        String near = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("26", near);
        test.nekro.addPlanet("lodor");
        String far = ti4.model.MapPositionMapper.getAdjacentTilePositions(near).stream()
                .filter(p -> !"x".equals(p) && !AiTestGame.HOME.equals(p) && test.game.getTileByPosition(p) == null)
                .findFirst()
                .orElseThrow();
        test.place("25", far);
        test.nekro.addPlanet("quann");
        String watcher = ti4.model.MapPositionMapper.getAdjacentTilePositions(far).stream()
                .filter(p -> !"x".equals(p)
                        && !near.equals(p)
                        && !AiTestGame.HOME.equals(p)
                        && test.game.getTileByPosition(p) == null)
                .findFirst()
                .orElseThrow();
        Tile enemy = test.place("47", watcher);
        test.units(enemy, "space", test.sol, UnitType.Carrier, 1);

        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.PDS, List.of("lodor", "quann")))
                .contains("quann");
    }

    // A raiding fleet beside home makes the home PDS the one that matters; with the fleet gone the PDS goes to the
    // empty planet outside home again.
    @Test
    void placesAPdsAtHomeWhileAFleetThreatensIt() {
        test.game.setRound(4);
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(5);
        test.place("26", "101");
        test.nekro.addPlanet("lodor");
        Player sardakk = test.addSeat("200000000000000002", "sardakk", "red");
        sardakk.setFleetCC(5);
        test.place("13", "310");
        Tile staging = test.place("46", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(staging, "space", sardakk, UnitType.Carrier, 1);
        test.units(staging, "space", sardakk, UnitType.Cruiser, 2);
        test.units(staging, "space", sardakk, UnitType.Infantry, 4);

        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.PDS, List.of("mordaiii", "lodor")))
                .contains("mordaiii");

        staging.removeAllUnits(sardakk.getColor());

        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.PDS, List.of("mordaiii", "lodor")))
                .contains("lodor");
    }

    // LRR 63.4: a PDS on a planet that holds another player's units and none of the owner's is destroyed.
    @Test
    void placesNoPdsOnAPlanetHeldByEnemyForcesWithoutOurGroundForces() {
        Tile lodor = test.place("26", "101");
        test.nekro.addPlanet("lodor");
        Tile quann = test.place("25", "102");
        test.nekro.addPlanet("quann");
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);

        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.PDS, List.of("lodor", "quann")))
                .contains("quann");

        test.units(lodor, "lodor", test.nekro, UnitType.Infantry, 1);
        test.units(quann, "quann", test.nekro, UnitType.Pds, 1);

        assertThat(StructurePolicy.planetFor(test.game, test.nekro, StructurePolicy.PDS, List.of("lodor", "quann")))
                .contains("lodor");
    }

    // Infrastructure counts planets outside the home system that hold a structure, so while it is short the dock
    // goes to a planet without one even though Lodor is richer.
    @Test
    void spreadsDocksToAFreshPlanetWhileInfrastructureIsShort() {
        Tile lodor = test.place("26", "101");
        test.nekro.addPlanet("lodor");
        test.place("25", "102");
        test.nekro.addPlanet("quann");
        test.units(lodor, "lodor", test.nekro, UnitType.Pds, 1);

        assertThat(StructurePolicy.planetFor(
                        test.game, test.nekro, StructurePolicy.SPACE_DOCK, List.of("lodor", "quann")))
                .contains("lodor");

        test.game
                .getRevealedPublicObjectives()
                .put("infrastructure", test.game.getRevealedPublicObjectives().size() + 1);

        assertThat(StructurePolicy.planetFor(
                        test.game, test.nekro, StructurePolicy.SPACE_DOCK, List.of("lodor", "quann")))
                .contains("quann");
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
