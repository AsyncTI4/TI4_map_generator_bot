package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.scoring.Footprint;
import ti4.ai.tactical.IncomingForces.Horizon;
import ti4.ai.tactical.IncomingForces.IncomingForce;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class IncomingForcesTest extends BaseTi4Test {

    private static final int POK_WARFARE = 6;

    private DefenseScenario board;

    @BeforeEach
    void setUp() {
        board = new DefenseScenario();
    }

    private IncomingForce force(Horizon horizon) {
        return new IncomingForces(board.test.game).of(board.sardakk, board.home, horizon);
    }

    // A carrier moves one system and carries four ground forces; the cruisers have no capacity, so two of the six
    // infantry waiting beside them stay behind.
    @Test
    void carriesOnlyWhatItsShipsCanHold() {
        board.raidingFleet(1, 2, 6);

        IncomingForce force = force(Horizon.IMMEDIATE);

        assertThat(force.isEmpty()).isFalse();
        assertThat(force.ships()).containsEntry(UnitType.Carrier, 1).containsEntry(UnitType.Cruiser, 2);
        assertThat(force.ground()).containsEntry(UnitType.Infantry, 4);
        assertThat(force.fleetCost()).isGreaterThan(0);
        assertThat(force.groundCost()).isGreaterThan(0);
        assertThat(force.moves()).contains(new Footprint.Move(board.stagingPosition, UnitType.Carrier, 1));
    }

    @Test
    void ignoresFleetsThatCannotReach() {
        Tile far = board.test.place("47", board.beyondStaging());
        board.test.units(far, "space", board.sardakk, UnitType.Carrier, 1);

        assertThat(force(Horizon.IMMEDIATE).isEmpty()).isTrue();
        assertThat(force(Horizon.NEXT_ROUND).isEmpty()).isTrue();
    }

    // Fleet supply caps the ships that can end up in one system; the most expensive are kept.
    @Test
    void keepsTheMostExpensiveShipsThatFleetSupplyAllows() {
        board.raidingFleet(1, 2, 4);
        board.sardakk.setFleetCC(1);

        IncomingForce force = force(Horizon.IMMEDIATE);

        assertThat(force.ships()).containsEntry(UnitType.Carrier, 1).doesNotContainKey(UnitType.Cruiser);
    }

    // Each origin's ships carry only that origin's units: cruisers cannot take the infantry of the carrier's system.
    @Test
    void doesNotPoolCargoAcrossOrigins() {
        board.raidingFleet(1, 0, 4);
        String second = board.otherNeighbourOfHome();
        Tile cruisers = board.test.place("47", second);
        board.test.units(cruisers, "space", board.sardakk, UnitType.Cruiser, 2);
        board.test.units(cruisers, "space", board.sardakk, UnitType.Infantry, 4);

        IncomingForce force = force(Horizon.IMMEDIATE);

        assertThat(force.ships()).containsEntry(UnitType.Cruiser, 2);
        assertThat(force.ground()).containsEntry(UnitType.Infantry, 4);
    }

    // Test 6: a fleet standing under its own command token cannot be activated this turn, but it is back next round.
    @Test
    void aFleetUnderItsOwnTokenIsOnlyAThreatNextRound() {
        board.raidingFleet(1, 2, 4);
        board.staging.addCC(Mapper.getCCID(board.sardakk.getColor()));

        assertThat(force(Horizon.IMMEDIATE).isEmpty()).isTrue();
        assertThat(force(Horizon.NEXT_ROUND).isEmpty()).isFalse();
    }

    @Test
    void aPassedEnemyIsOnlyAThreatNextRound() {
        board.raidingFleet(1, 2, 4);
        board.sardakk.setPassed(true);

        assertThat(force(Horizon.IMMEDIATE).isEmpty()).isTrue();
        assertThat(force(Horizon.NEXT_ROUND).isEmpty()).isFalse();
    }

    // Test 6: without tactic tokens an enemy cannot activate anything. Only Thunder's Edge Warfare is a tactical action
    // without a token.
    @Test
    void onlyAThundersEdgeWarfareCardUnlocksAFleetWithoutTokens() {
        board.raidingFleet(1, 2, 4);
        board.sardakk.setTacticalCC(0);
        board.test.game.setStrategyCardSet("pok");
        board.sardakk.addSC(POK_WARFARE);
        assertThat(force(Horizon.IMMEDIATE).isEmpty()).isTrue();

        board.test.game.setStrategyCardSet("te");

        assertThat(force(Horizon.IMMEDIATE).isEmpty()).isFalse();
    }

    // Test 7: Nekro's destroyer in the only system between them stops the cruisers, which have no way to move through
    // it.
    @Test
    void ourShipsBlockTheEnemysOnlyPath() {
        Tile far = board.test.place("47", board.beyondStaging());
        board.test.units(far, "space", board.sardakk, UnitType.Cruiser, 2);
        assertThat(force(Horizon.IMMEDIATE).isEmpty()).isFalse();

        board.test.units(board.staging, "space", board.test.nekro, UnitType.Destroyer, 1);

        assertThat(force(Horizon.IMMEDIATE).isEmpty()).isTrue();
        assertThat(force(Horizon.NEXT_ROUND).isEmpty()).isTrue();
    }

    // Gravity Drive stretches one ship's move: a carrier two systems away reaches home with it and not without it.
    @Test
    void gravityDriveBringsOneMoreShipInRange() {
        Tile far = board.test.place("47", board.beyondStaging());
        board.test.units(far, "space", board.sardakk, UnitType.Carrier, 2);
        assertThat(force(Horizon.IMMEDIATE).isEmpty()).isTrue();

        board.sardakk.addTech("gd");

        IncomingForce force = force(Horizon.IMMEDIATE);
        assertThat(force.isEmpty()).isFalse();
        assertThat(force.ships()).containsEntry(UnitType.Carrier, 1);
    }

    // Test 20: a ship that starts in a gravity rift moves with +1, so the fleet parked in the rift next to home counts.
    @Test
    void aFleetParkedInAnAdjacentGravityRiftIsIncoming() {
        Tile rift = board.test.place("41", board.stagingPosition);
        board.test.units(rift, "space", board.sardakk, UnitType.Carrier, 1);
        board.test.units(rift, "space", board.sardakk, UnitType.Infantry, 3);

        IncomingForce force = force(Horizon.IMMEDIATE);

        assertThat(force.isEmpty()).isFalse();
        assertThat(force.ground()).containsEntry(UnitType.Infantry, 3);
    }

    @Test
    void theRiftBonusCarriesAMoveOneShipTwoSystems() {
        Tile rift = board.test.place("41", board.beyondStaging());
        board.test.units(rift, "space", board.sardakk, UnitType.Carrier, 1);

        assertThat(force(Horizon.IMMEDIATE).isEmpty()).isFalse();
    }
}
