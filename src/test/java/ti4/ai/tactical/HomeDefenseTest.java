package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The home posture is a scale, not a flat garrison: from no standing defence (round 1, nobody in reach) to a heavily
// defended home, moving up with the round, the enemy fleet that can reach home and the seat's lead over the table. A
// multi-planet home is held in space; ground is kept only where it would hold.
class HomeDefenseTest extends BaseTi4Test {

    private static final String HOME_PLANET = "mordaiii";
    private static final String HACAN_HOME_TILE = "16";
    private static final String HACAN_DOCK_PLANET = "arretze";
    private static final List<String> HACAN_PLANETS = List.of(HACAN_DOCK_PLANET, "hercant", "kamdorn");

    private DefenseScenario board = new DefenseScenario();

    private DefenseOutlook outlook() {
        return new DefenseOutlook(board.test.game, board.test.nekro);
    }

    private static UnitMove fromHome(UnitType type, int count) {
        return new UnitMove(AiTestGame.HOME, "space", type, count);
    }

    private static int count(List<UnitMove> moves) {
        return moves.stream().mapToInt(UnitMove::count).sum();
    }

    private Planet planet(String name) {
        return (Planet) board.home.getUnitHolders().get(name);
    }

    private static Tile hacanHome(AiTestGame test) {
        Tile home = test.place(HACAN_HOME_TILE, AiTestGame.HOME);
        HACAN_PLANETS.forEach(test.nekro::addPlanet);
        test.units(home, HACAN_DOCK_PLANET, test.nekro, UnitType.Spacedock, 1);
        return home;
    }

    // Round 1 and no enemy ship can reach home: the posture is zero, every ship may leave and no infantry has to stay,
    // not even one.
    @Test
    void roundOneWithNobodyInReachHoldsNothingBack() {
        board.test.game.setRound(1);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Carrier, 2);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 1);
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, 3);
        List<UnitMove> fleet = List.of(fromHome(UnitType.Carrier, 2), fromHome(UnitType.Dreadnought, 1));
        DefenseOutlook outlook = outlook();

        assertThat(outlook.homeDefenseLevel()).isZero();
        assertThat(outlook.holdBack(board.home, List.of(), fleet)).isEqualTo(fleet);
        assertThat(outlook.groundToKeep(board.home, planet(HOME_PLANET), fleet)).isZero();
    }

    // Two Sardakk dreadnoughts and two cruisers next door carry no ground forces, so the threat model sees nothing
    // to lose. Late in the game a battle fleet in reach of home is still reason to keep part of the home fleet there:
    // it would otherwise blockade the dock and clear the way for a landing. With nothing to land, its motive is at the
    // floor, so part of the fleet may still go.
    @Test
    void aStrongFleetNextDoorLateInTheGameKeepsShipsHome() {
        board.test.game.setRound(5);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Dreadnought, 2);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Cruiser, 2);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 4);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Cruiser, 2);
        List<UnitMove> fleet = List.of(fromHome(UnitType.Dreadnought, 4), fromHome(UnitType.Cruiser, 2));
        DefenseOutlook outlook = outlook();

        List<UnitMove> mayLeave = outlook.holdBack(board.home, List.of(), fleet);

        assertThat(outlook.cost(board.home, board.defenders().minus(fleet))).isZero();
        assertThat(count(mayLeave)).isBetween(1, count(fleet) - 1);
        assertThat(outlook.leaveCost(board.home, fleet)).isGreaterThan(DefenseOutlook.GARRISON_TOLERANCE);
    }

    // A smaller fleet next door: early in the game the scale starts low, so the same threat holds back less.
    @Test
    void theSameFleetHoldsBackMoreAsTheRoundsGoBy() {
        board.test.units(board.staging, "space", board.sardakk, UnitType.Dreadnought, 1);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Cruiser, 1);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Cruiser, 2);
        List<UnitMove> fleet = List.of(fromHome(UnitType.Dreadnought, 2), fromHome(UnitType.Cruiser, 2));

        board.test.game.setRound(1);
        DefenseOutlook early = outlook();
        int keptEarly = count(fleet) - count(early.holdBack(board.home, List.of(), fleet));
        board.test.game.setRound(5);
        DefenseOutlook late = outlook();
        int keptLate = count(fleet) - count(late.holdBack(board.home, List.of(), fleet));

        assertThat(late.homeDefenseLevel()).isGreaterThan(early.homeDefenseLevel());
        assertThat(keptLate).isGreaterThan(keptEarly);
    }

    // Hacan's three-planet home, with a Sardakk landing party next door in round 4. The old rule kept one infantry on
    // the home system whatever the threat; the posture keeps the dreadnoughts home instead, and with them there the
    // planets need no infantry of their own.
    @Test
    void aMultiPlanetHomeIsHeldInSpaceNotByInfantryOnItsPlanets() {
        board = new DefenseScenario(HomeDefenseTest::hacanHome);
        board.raidingFleet(1, 2, 4);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Carrier, 1);
        for (String name : HACAN_PLANETS) board.test.units(board.home, name, board.test.nekro, UnitType.Infantry, 1);
        List<UnitMove> fleet = List.of(fromHome(UnitType.Dreadnought, 2), fromHome(UnitType.Carrier, 1));
        List<UnitMove> carrierOnly = List.of(fromHome(UnitType.Carrier, 1));
        DefenseOutlook outlook = outlook();

        List<UnitMove> mayLeave = outlook.holdBack(board.home, List.of(), fleet);
        int groundKept = HACAN_PLANETS.stream()
                .mapToInt(name -> outlook.groundToKeep(board.home, planet(name), carrierOnly))
                .sum();

        assertThat(outlook.homeDefenseLevel()).isPositive();
        assertThat(mayLeave).noneMatch(move -> move.type() == UnitType.Dreadnought && move.count() == 2);
        assertThat(groundKept).isZero();
    }

    // When the whole fleet leaves a multi-planet home anyway, late and in the lead, the posture asks for ground too,
    // and
    // puts it on the dock planet: a garrison that holds one planet, not one infantry spread on each. The other planets
    // keep only what the threat model wants against a landing on whichever planet is left empty.
    @Test
    void anEmptiedMultiPlanetHomeConcentratesItsGroundOnTheDockPlanet() {
        board = new DefenseScenario(HomeDefenseTest::hacanHome);
        board.test.game.setRound(6);
        board.test.points(board.test.nekro, 3);
        board.raidingFleet(1, 2, 2);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Dreadnought, 1);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Carrier, 1);
        for (String name : HACAN_PLANETS) board.test.units(board.home, name, board.test.nekro, UnitType.Infantry, 3);
        List<UnitMove> fleet = List.of(fromHome(UnitType.Carrier, 1));
        DefenseOutlook outlook = outlook();

        int onDock = outlook.groundToKeep(board.home, planet(HACAN_DOCK_PLANET), fleet);

        assertThat(outlook.anchorPlanet().getName()).isEqualTo(HACAN_DOCK_PLANET);
        assertThat(outlook.homeDefenseLevel()).isGreaterThan(0.6);
        assertThat(onDock).isGreaterThanOrEqualTo(2);
        assertThat(outlook.groundToKeep(board.home, planet("hercant"), fleet)).isLessThanOrEqualTo(onDock);
        assertThat(outlook.groundToKeep(board.home, planet("kamdorn"), fleet)).isLessThanOrEqualTo(onDock);
    }

    // The round-1 opening simulator (OpeningOutlook) asks the planner how much ground stays home while the carriers
    // leave: nothing with nobody in reach, and what the posture and threat ask for once a landing party is next door.
    @Test
    void theOpeningSimulatorKeepsTheGroundThePlannerKeeps() {
        board.test.game.setRound(1);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Carrier, 1);
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, 6);
        assertThat(TacticalPlanner.groundKeptHomeWhileTransportsLeave(board.test.game, board.test.nekro))
                .isZero();

        board.test.game.setRound(4);
        board.raidingFleet(1, 2, 4);

        int kept = TacticalPlanner.groundKeptHomeWhileTransportsLeave(board.test.game, board.test.nekro);
        assertThat(kept).isPositive();
        assertThat(kept)
                .isEqualTo(outlook()
                        .groundToKeep(board.home, planet(HOME_PLANET), List.of(fromHome(UnitType.Carrier, 1))));
    }

    // The same Sardakk landing party next door, once with nothing better to do and once with Bereg and Lirta IV free
    // beside it: an easier prize lowers its motive to attack home, so less of the home fleet has to stay. The motive
    // has a floor, so the fleet is never released entirely.
    @Test
    void aFleetWithAJuicierTargetHoldsLessOfTheHomeFleet() {
        int focused = keptAgainstALandingParty(false);
        int distracted = keptAgainstALandingParty(true);

        // With influence worth as much as a resource a home planet is worth less, so the motive floor already binds.
        assertThat(distracted).isLessThanOrEqualTo(focused);
        assertThat(distracted).isPositive();
    }

    private int keptAgainstALandingParty(boolean alternative) {
        board = new DefenseScenario();
        board.test.game.setRound(5);
        board.raidingFleet(1, 2, 4);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Dreadnought, 1);
        if (alternative) board.test.place("35", board.beyondStaging());
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Cruiser, 3);
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, 4);
        List<UnitMove> fleet = List.of(fromHome(UnitType.Dreadnought, 2), fromHome(UnitType.Cruiser, 3));
        DefenseOutlook outlook = outlook();
        return count(fleet) - count(outlook.holdBack(board.home, List.of(), fleet));
    }

    // Round 1 must stay free for the opening. Nobody within two moves of home: nothing at all. A neighbour's whole
    // starting fleet two moves out: the scale barely moves, and no ship or infantry is held for it.
    @Test
    void roundOneOpeningsStayFree() {
        board.test.game.setRound(1);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Carrier, 2);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 1);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Destroyer, 1);
        board.test.units(board.home, HOME_PLANET, board.test.nekro, UnitType.Infantry, 4);
        List<UnitMove> fleet = List.of(
                fromHome(UnitType.Carrier, 2), fromHome(UnitType.Dreadnought, 1), fromHome(UnitType.Destroyer, 1));
        Tile far = board.test.place("47", board.beyondStaging());
        board.test.units(far, "space", board.sardakk, UnitType.Carrier, 2);
        board.test.units(far, "space", board.sardakk, UnitType.Dreadnought, 1);
        board.test.units(far, "space", board.sardakk, UnitType.Destroyer, 1);
        board.test.units(far, "space", board.sardakk, UnitType.Infantry, 4);
        board.test.units(far, "space", board.sardakk, UnitType.Fighter, 2);
        DefenseOutlook outlook = outlook();

        assertThat(outlook.homeDefenseLevel()).isPositive().isLessThan(0.2);
        assertThat(outlook.holdBack(board.home, List.of(), fleet)).isEqualTo(fleet);
        assertThat(outlook.groundToKeep(board.home, planet(HOME_PLANET), fleet)).isZero();
    }

    // Three points ahead of Sardakk the seat is the table's target and keeps more of its fleet home against two
    // dreadnoughts and a cruiser next door than it does three points behind.
    @Test
    void theLeaderDefendsHomeMoreThanATrailingSeat() {
        int trailing = keptAgainstTheSameFleet(false);
        int leading = keptAgainstTheSameFleet(true);

        assertThat(leading).isGreaterThan(trailing);
    }

    private int keptAgainstTheSameFleet(boolean leading) {
        board = new DefenseScenario();
        board.test.points(leading ? board.test.nekro : board.sardakk, 3);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Dreadnought, 2);
        board.test.units(board.staging, "space", board.sardakk, UnitType.Cruiser, 1);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Dreadnought, 2);
        board.test.units(board.home, "space", board.test.nekro, UnitType.Cruiser, 2);
        List<UnitMove> fleet = List.of(fromHome(UnitType.Dreadnought, 2), fromHome(UnitType.Cruiser, 2));
        return count(fleet) - count(outlook().holdBack(board.home, List.of(), fleet));
    }
}
