package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

// How angry a strike makes its victim, by where it lands relative to the victim's home (their home, the systems of
// their slice including their lane to Mecatol Rex, and contested ground: the equidistant systems and Mecatol Rex
// itself), by whether it stops them scoring, and by how much fleet they have free to retaliate with.
class GrievanceTest extends BaseTi4Test {

    private static final List<String> HOME_POSITIONS = List.of("301", "304", "307", "310", "313", "316");
    private static final List<String> FACTIONS = List.of("nekro", "sol", "sardakk", "hacan", "letnev", "l1z1x");
    private static final List<String> COLORS = List.of("black", "blue", "red", "green", "orange", "purple");

    private final AiTestGame test = new AiTestGame();

    // The victim, Sardakk, sits at 307 on the default six-player map; their neighbours sit at 304 and 310.
    private Player sixPlayerMapWithSardakkAt307() {
        Player sardakk = null;
        for (int seat = 0; seat < FACTIONS.size(); seat++) {
            String faction = FACTIONS.get(seat);
            if (!"nekro".equals(faction) && !"sol".equals(faction)) {
                Player added = test.addSeat("20000000000000010" + seat, faction, COLORS.get(seat));
                if ("sardakk".equals(faction)) sardakk = added;
            }
            test.place(Mapper.getFaction(faction).getHomeSystem(), HOME_POSITIONS.get(seat));
        }
        test.place("18", "000");
        return sardakk;
    }

    private double locationAt(Player victim, String position) {
        Tile tile = test.game.getTileByPosition(position);
        return Grievance.location(test.game, tile == null ? new Tile("26", position) : tile, victim);
    }

    @Test
    void raidingTheVictimsHomeIsTheWorstInsult() {
        Player sardakk = sixPlayerMapWithSardakkAt307();

        assertThat(locationAt(sardakk, "307")).isEqualTo(Grievance.HOME_FACTOR);
    }

    // 306 and 308 border the home on the outer ring and 205 sits in front of it on the 200 ring.
    @Test
    void theSystemsAroundTheVictimsHomeUpsetThem() {
        Player sardakk = sixPlayerMapWithSardakkAt307();

        assertThat(locationAt(sardakk, "306")).isEqualTo(Grievance.SLICE_FACTOR);
        assertThat(locationAt(sardakk, "308")).isEqualTo(Grievance.SLICE_FACTOR);
        assertThat(locationAt(sardakk, "205")).isEqualTo(Grievance.SLICE_FACTOR);
    }

    // 103 is the system of the ring around Mecatol Rex between it and the home at 307.
    @Test
    void theMecatolRingSystemClosestToTheVictimUpsetsThem() {
        Player sardakk = sixPlayerMapWithSardakkAt307();

        assertThat(locationAt(sardakk, "103")).isEqualTo(Grievance.SLICE_FACTOR);
        assertThat(locationAt(sardakk, "102")).isEqualTo(Grievance.CONTESTED_FACTOR);
        assertThat(locationAt(sardakk, "104")).isEqualTo(Grievance.CONTESTED_FACTOR);
    }

    // 204 lies as close to 304 as to 307, and 206 as close to 310 as to 307.
    @Test
    void theEquidistantsAreContestedGround() {
        Player sardakk = sixPlayerMapWithSardakkAt307();

        assertThat(locationAt(sardakk, "204")).isEqualTo(Grievance.CONTESTED_FACTOR);
        assertThat(locationAt(sardakk, "206")).isEqualTo(Grievance.CONTESTED_FACTOR);
    }

    @Test
    void mecatolRexIsContestedGroundWithNoExtraGrievance() {
        Player sardakk = sixPlayerMapWithSardakkAt307();

        assertThat(locationAt(sardakk, "000")).isEqualTo(Grievance.CONTESTED_FACTOR);
    }

    @Test
    void aVictimWithoutAHomeSystemIsOnlyUpsetByTheLoss() {
        Player sardakk = test.addSeat("200000000000000102", "sardakk", "red");
        test.place("18", "000");

        assertThat(locationAt(sardakk, "000")).isEqualTo(Grievance.CONTESTED_FACTOR);
        assertThat(locationAt(sardakk, "101")).isEqualTo(Grievance.CONTESTED_FACTOR);
    }

    // Stopping a trailing or level player from scoring makes them really mad; a player three points clear of the field
    // is only a bit upset.
    @Test
    void stoppingAPlayerScoringAngersThemUnlessTheyLeadHeavily() {
        Player sardakk = sixPlayerMapWithSardakkAt307();
        Standings standings = new Standings(test.game, null);
        test.points(test.sol, 4);
        test.points(sardakk, 2);
        double trailingLead = standings.leadOverField(sardakk);
        double trailing = Grievance.scoringDenial(trailingLead);

        test.points(sardakk, 5);
        Standings later = new Standings(test.game, null);
        double leadingLead = later.leadOverField(sardakk);
        double leading = Grievance.scoringDenial(leadingLead);

        assertThat(trailingLead).isEqualTo(-2);
        assertThat(leadingLead).isEqualTo(3);
        assertThat(trailing).isEqualTo(Grievance.SCORING_DENIAL_FACTOR);
        assertThat(leading).isEqualTo(Grievance.LEADER_SCORING_DENIAL_FACTOR);
        assertThat(Grievance.scoringDenial(1.5)).isCloseTo(1.75, within(1e-9));
    }

    // Grudges give way when the strike scores for the seat, when the victim is about to win, or when the seat is.
    @Test
    void scoringAndStoppingAWinOutweighAnger() {
        assertThat(Grievance.relief(0, 0, 0)).isEqualTo(1.0);
        assertThat(Grievance.relief(0, 0, 1.2)).isZero();
        assertThat(Grievance.relief(1, 0, 0)).isZero();
        assertThat(Grievance.relief(0, 0.5, 0.25)).isEqualTo(0.5);
    }

    private static final class RexRaid {
        final DefenseScenario board = new DefenseScenario();
        final Tile sardakkHome = board.test.game.getTileByPosition(DefenseScenario.SARDAKK_HOME_POSITION);

        RexRaid() {
            board.sardakk.setTacticalCC(3);
            Tile rex = board.test.place("18", "000");
            board.sardakk.addPlanet("mr");
            board.test.units(rex, "mr", board.sardakk, UnitType.Infantry, 1);
            String spot = AiTestGame.neighbourOf("000");
            Tile launch = board.test.place("47", spot);
            board.test.units(launch, "space", board.test.nekro, UnitType.Carrier, 1);
            board.test.units(launch, "space", board.test.nekro, UnitType.Cruiser, 2);
            board.test.units(launch, "space", board.test.nekro, UnitType.Infantry, 2);
        }

        RexRaid sardakkFleetAtHome(int dreadnoughts) {
            board.test.units(sardakkHome, "space", board.sardakk, UnitType.Dreadnought, dreadnoughts);
            return this;
        }

        double anger() {
            return TacticalPlanner.explainAttack(board.test.game, board.test.nekro, "000")
                    .orElseThrow()
                    .anger();
        }
    }

    // A victim with no fleet cannot retaliate, so taking their planet costs no goodwill worth pricing.
    @Test
    void aVictimWithNoFleetCannotRetaliate() {
        assertThat(new RexRaid().anger()).isZero();
    }

    // The same fleet locked under the victim's own command token, or with no tactic tokens left, can strike back only
    // later, so it weighs half as much as a free fleet.
    @Test
    void aFreeFleetRetaliatesHarderThanALockedOne() {
        double free = new RexRaid().sardakkFleetAtHome(1).anger();

        RexRaid locked = new RexRaid().sardakkFleetAtHome(1);
        locked.sardakkHome.addCC(Mapper.getCCID(locked.board.sardakk.getColor()));
        double lockedAnger = locked.anger();

        RexRaid outOfTokens = new RexRaid().sardakkFleetAtHome(1);
        outOfTokens.board.sardakk.setTacticalCC(0);

        assertThat(free).isPositive();
        assertThat(lockedAnger).isCloseTo(free / 2, within(1e-9));
        assertThat(outOfTokens.anger()).isCloseTo(free / 2, within(1e-9));
    }
}
