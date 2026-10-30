package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.eval.CustodiansRace.Drive;
import ti4.ai.eval.CustodiansRace.Order;
import ti4.ai.eval.CustodiansRace.Snapshot;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// The race for the custodians: when each seat could land on Mecatol Rex with a loaded transport, from public state
// only. Boards place only what the case needs; the full-map case uses the default six-seat map and the starting kits.
class CustodiansRaceTest extends BaseTi4Test {

    private static final String MECATOL = "000";
    private static final String ONE_AWAY = "101";
    private static final String TWO_AWAY = "201";
    private static final String EMPTY_SYSTEM = "46";

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(2);
        Tile rex = test.place("18", MECATOL);
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
    }

    private Tile transportAt(String position, Player owner, int infantry) {
        Tile tile = test.game.getTileByPosition(position);
        if (tile == null) tile = test.place(EMPTY_SYSTEM, position);
        test.units(tile, "space", owner, UnitType.Carrier, 1);
        if (infantry > 0) test.units(tile, "space", owner, UnitType.Infantry, infantry);
        return tile;
    }

    private void emptyPath() {
        test.place(EMPTY_SYSTEM, ONE_AWAY);
    }

    private OptionalInt arrival(Player player) {
        return CustodiansRace.of(test.game, test.nekro).arrival(player);
    }

    private void actionPhase() {
        test.game.setPhaseOfGame("action");
        test.nekro.setTacticalCC(3);
        test.sol.setTacticalCC(3);
    }

    // A carrier with an infantry, two moves from Mecatol Rex, with Gravity Drive: it lands this round. The seat has the
    // custodians' 6 influence in trade goods. With its own command token in the system the carrier cannot leave this
    // round, so it lands next round.
    @Test
    void landsThisRoundWithGravityDriveAndNextRoundWhenTheSystemHoldsItsToken() {
        emptyPath();
        Tile carrier = transportAt(TWO_AWAY, test.nekro, 1);
        test.nekro.addTech("gd");
        test.nekro.setTg(6);
        actionPhase();

        assertThat(arrival(test.nekro)).hasValue(2);
        assertThat(CustodiansRace.of(test.game, test.nekro).canLandThisRound(test.nekro))
                .isTrue();

        carrier.addCC(Mapper.getCCID(test.nekro.getColor()));

        assertThat(arrival(test.nekro)).hasValue(3);
        assertThat(CustodiansRace.of(test.game, test.nekro).canLandThisRound(test.nekro))
                .isFalse();
    }

    @Test
    void aSeatWithoutGravityDriveNeedsTwoRoundsFromTwoMovesAway() {
        emptyPath();
        transportAt(TWO_AWAY, test.nekro, 1);
        test.nekro.setTg(6);
        actionPhase();

        assertThat(arrival(test.nekro)).hasValue(3);
    }

    @Test
    void aTransportWithoutGroundForcesIsNoTransport() {
        emptyPath();
        transportAt(ONE_AWAY, test.nekro, 0);
        actionPhase();

        assertThat(arrival(test.nekro)).isEmpty();
        assertThat(CustodiansRace.of(test.game, test.nekro).canLandThisRound(test.nekro))
                .isFalse();
    }

    @Test
    void thereIsNoRaceOnceTheCustodiansAreGone() {
        Tile rex = test.game.getTileByPosition(MECATOL);
        transportAt(ONE_AWAY, test.nekro, 1);
        test.nekro.setTg(6);
        assertThat(CustodiansRace.open(test.game)).isTrue();
        assertThat(arrival(test.nekro)).isPresent();

        rex.getUnitHolders().get("mr").removeToken(Constants.CUSTODIAN_TOKEN_PNG);

        assertThat(CustodiansRace.open(test.game)).isFalse();
        assertThat(arrival(test.nekro)).isEmpty();
    }

    // A seat that cannot pay the 6 influence now lands a round later, and one that could not pay then either (nothing
    // to ready, no free planet to take on the way) a round after that: an arrival is never dropped for influence.
    // Whether it could land is a question about reach only.
    @Test
    void anArrivalItCannotPayForMovesBackAndIsNeverEmpty() {
        emptyPath();
        transportAt(ONE_AWAY, test.nekro, 1);
        actionPhase();
        assertThat(arrival(test.nekro)).hasValue(4);
        assertThat(CustodiansRace.of(test.game, test.nekro).canLandThisRound(test.nekro))
                .isTrue();

        test.nekro.setTg(6);

        assertThat(arrival(test.nekro)).hasValue(2);
    }

    @Test
    void exhaustedPlanetsReadyInTheStatusPhaseSoTheArrivalMovesBackOnlyOneRound() {
        emptyPath();
        transportAt(ONE_AWAY, test.nekro, 1);
        test.nekro.addPlanet("rarron");
        test.nekro.addPlanet("meer");
        test.nekro.exhaustPlanet("rarron");
        test.nekro.exhaustPlanet("meer");
        test.place("29", "202");
        test.place("37", "203");
        actionPhase();

        assertThat(arrival(test.nekro)).hasValue(3);
    }

    @Test
    void aLaterArrivalWithTooLittleInfluenceComesOneRoundLater() {
        emptyPath();
        transportAt(TWO_AWAY, test.nekro, 1);
        actionPhase();
        test.nekro.setTg(6);
        int enough = arrival(test.nekro).orElseThrow();

        test.nekro.setTg(5);

        assertThat(arrival(test.nekro).orElseThrow()).isEqualTo(enough + 1);
    }

    // A rival with an earlier arrival takes it first four times in five. With the same arrival the order decides: a
    // rival with the lower initiative in the action phase, a coin flip while the order is not yet known, and the
    // Naalu 0 token always goes first.
    @Test
    void winChanceFollowsTheOrderOfPlay() {
        emptyPath();
        test.place(EMPTY_SYSTEM, "102");
        transportAt(ONE_AWAY, test.nekro, 1);
        transportAt("102", test.sol, 1);
        test.nekro.setTg(6);
        test.sol.setTg(6);
        actionPhase();
        test.nekro.addSC(5);
        test.sol.addSC(1);
        Snapshot race = CustodiansRace.of(test.game, test.nekro);

        assertThat(race.arrival(test.sol)).isEqualTo(race.arrival(test.nekro));
        int round = race.arrival(test.nekro).getAsInt();
        assertThat(race.winChance(test.nekro, round, Order.KNOWN)).isCloseTo(0.2, within(1e-9));
        assertThat(race.winChance(test.nekro, round, Order.UNKNOWN)).isEqualTo(0.5);
        assertThat(race.winChance(test.nekro, round, Order.SEAT_FIRST)).isEqualTo(0.95);
        assertThat(race.winChance(test.sol, round, Order.KNOWN)).isEqualTo(0.95);
        assertThat(race.winChance(test.nekro, round + 1, Order.UNKNOWN)).isCloseTo(0.2, within(1e-9));
        assertThat(race.winChance(test.nekro, round - 1, Order.UNKNOWN)).isEqualTo(1.0);
    }

    @Test
    void theNaaluZeroTokenAlwaysGoesFirst() {
        emptyPath();
        test.place(EMPTY_SYSTEM, "102");
        Player naalu = test.addSeat("200000000000000007", "naalu", "yellow");
        transportAt(ONE_AWAY, test.nekro, 1);
        transportAt("102", naalu, 1);
        test.nekro.setTg(6);
        naalu.setTg(6);
        Snapshot race = CustodiansRace.of(test.game, test.nekro);
        assertThat(naalu.hasTheZeroToken()).isTrue();
        int round = race.arrival(test.nekro).getAsInt();
        assertThat(race.arrival(naalu)).hasValue(round);

        assertThat(race.winChance(test.nekro, round, Order.UNKNOWN)).isCloseTo(0.2, within(1e-9));
        assertThat(race.winChance(test.nekro, round, Order.SEAT_FIRST)).isCloseTo(0.2, within(1e-9));
        assertThat(race.winChance(naalu, round, Order.UNKNOWN)).isEqualTo(0.95);
    }

    @Test
    void theRaceIsWorthTheCustodiansPlusWhatHoldingMecatolRexPays() {
        emptyPath();
        transportAt(ONE_AWAY, test.nekro, 1);
        test.nekro.setTg(6);
        Snapshot race = CustodiansRace.of(test.game, test.nekro);
        OptionalInt arrival = race.arrival(test.nekro);

        double alone = race.raceValue(test.nekro, arrival, Order.UNKNOWN);

        double planet = test.game.getTileByPosition(MECATOL).getPlanetUnitHolders().stream()
                .mapToDouble(BoardView::planetValue)
                .sum();
        assertThat(alone).isEqualTo(8.0 + MecatolStake.holdValue(test.game, test.nekro) + planet);
        assertThat(race.raceValue(test.nekro, OptionalInt.empty(), Order.UNKNOWN))
                .isZero();
    }

    // Researching Gravity Drive moves every later hop one system farther: a seat with a propulsion technology and a
    // loaded carrier three systems out arrives a round sooner than without the drive.
    @Test
    void gravityDriveIsLikelyForASeatWithAPropulsionTechnology() {
        emptyPath();
        test.place(EMPTY_SYSTEM, TWO_AWAY);
        transportAt("301", test.nekro, 1);
        test.nekro.setTg(6);
        test.nekro.addTech("amd");
        actionPhase();
        Snapshot race = CustodiansRace.of(test.game, test.nekro);

        int without = race.arrivalWith(test.nekro, Drive.OWNED).getAsInt();
        int with = race.arrivalWith(test.nekro, Drive.LIKELY).getAsInt();

        assertThat(with).isLessThan(without);
    }

    // The default six-seat map: every home is three systems from Mecatol Rex. In the round 1 strategy phase, the seats
    // that start with a propulsion technology (Hacan and Letnev) can get there in round 2 by researching Gravity Drive;
    // Sardakk needs three rounds.
    @Test
    void startingKitsOnTheDefaultMapDecideWhoArrivesWhen() {
        RaceTable table = new RaceTable();
        Snapshot race = CustodiansRace.of(table.test.game, table.test.nekro);

        assertThat(race.arrival(table.seat("hacan"))).hasValue(2);
        assertThat(race.arrival(table.seat("letnev"))).hasValue(2);
        assertThat(race.arrival(table.seat("sardakk"))).hasValue(3);
    }

    // Moving a loaded carrier one system towards Mecatol Rex is worth the race it wins: Hacan, with Letnev arriving in
    // round 2 as well, gains by arriving in round 2 itself instead of round 3.
    @Test
    void hacanGainsByApproachingMecatolRexInRoundOne() {
        RaceTable table = new RaceTable();
        Player hacan = table.seat("hacan");
        Snapshot race = CustodiansRace.of(table.test.game, table.test.nekro);
        Tile closer = table.test.game.getTileByPosition(
                closerToMecatol(hacan.getHomeSystemTile().getPosition()));

        assertThat(approachGain(race, hacan, closer)).isPositive();
    }

    // Short of the influence for the custodians the arrival comes a round later whichever way the carrier goes, so
    // moving it gains less than with the influence in hand.
    @Test
    void approachingGainsLessWithTooLittleInfluence() {
        emptyPath();
        test.place(EMPTY_SYSTEM, TWO_AWAY);
        transportAt("301", test.nekro, 1);
        transportAt("202", test.sol, 1);
        test.nekro.addTech("amd");
        test.sol.setTg(6);
        test.game.setPhaseOfGame("strategy");
        Tile closer = test.game.getTileByPosition(TWO_AWAY);

        test.nekro.setTg(6);
        double enough = approachGain(CustodiansRace.of(test.game, test.nekro), test.nekro, closer);
        test.nekro.setTg(0);
        double tooLittle = approachGain(CustodiansRace.of(test.game, test.nekro), test.nekro, closer);

        assertThat(enough).isPositive();
        assertThat(tooLittle).isLessThan(enough);
    }

    private static double approachGain(Snapshot race, Player seat, Tile destination) {
        OptionalInt idle = race.idleArrival(seat);
        OptionalInt moved = race.arrivalFrom(seat, destination);
        OptionalInt best = idle.isEmpty() || moved.isEmpty()
                ? (idle.isEmpty() ? moved : idle)
                : OptionalInt.of(Math.min(idle.getAsInt(), moved.getAsInt()));
        return race.raceValue(seat, best, Order.UNKNOWN) - race.raceValue(seat, idle, Order.UNKNOWN);
    }

    private static String closerToMecatol(String position) {
        Tile origin = null;
        for (String neighbour : ti4.model.MapPositionMapper.getAdjacentTilePositions(position)) {
            if (!"x".equals(neighbour) && ringOf(neighbour) < ringOf(position)) return neighbour;
        }
        throw new IllegalStateException("nothing closer to Mecatol Rex than " + position + origin);
    }

    private static int ringOf(String position) {
        return Integer.parseInt(position.substring(0, 1));
    }

    // Reading another seat's arrival is reading its units, planets, trade goods and technologies, never its hand.
    @Test
    void readsNoHiddenInformation() {
        emptyPath();
        test.place(EMPTY_SYSTEM, "102");
        transportAt(ONE_AWAY, test.nekro, 1);
        transportAt("102", test.sol, 1);
        test.nekro.setTg(6);
        test.sol.setTg(6);
        test.sol.setSecret("dhw");
        test.sol.setActionCard("sabo1");
        test.sol.setPromissoryNote("blue_ta", 17);
        Player spy = Mockito.spy(test.sol);
        test.game.getPlayers().put(spy.getUserID(), spy);
        Snapshot race = CustodiansRace.of(test.game, test.nekro);

        race.arrival(spy);
        race.arrivalWith(spy, Drive.LIKELY);
        race.idleArrival(spy);
        race.arrivalFrom(spy, test.game.getTileByPosition(ONE_AWAY));
        race.canLandThisRound(spy);
        race.winChance(test.nekro, 2, Order.KNOWN);
        race.raceValue(test.nekro, race.arrival(test.nekro), Order.UNKNOWN);

        verify(spy, never()).getSecretsUnscored();
        verify(spy, never()).getSecrets();
        verify(spy, never()).getActionCards();
        verify(spy, never()).getPromissoryNotes();
    }
}
