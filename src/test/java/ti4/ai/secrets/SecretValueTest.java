package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.service.info.ListPlayerInfoService;
import ti4.testUtils.BaseTi4Test;

// keepValue is the chance that a secret gets scored, times what a point from its phase is worth. Early on that chance
// leans on how often the secret was finished in real games (SecretStatistics); as the game goes on it leans on the
// board. A secret that can be scored right now outranks every secret that cannot. Each test names the numbers its
// ordering rests on, for a ten point game in round two (five rounds left, so the prior and the board weigh 0.5 each).
class SecretValueTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;
    private String neighbour;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
    }

    private double value(String secret) {
        return SecretValue.keepValue(test.game, test.nekro, secret);
    }

    private String discarded() {
        return SecretValue.lowestValueSecret(test.game, test.nekro).orElseThrow();
    }

    private void aiHoldsInPlayArea(String note) {
        test.sol.addOwnedPromissoryNoteByID(note);
        test.nekro.setPromissoryNote(note);
        test.nekro.addPromissoryNoteToPlayArea(note);
    }

    private void controls(String tileId, String position, String... planets) {
        test.place(tileId, position);
        for (String planet : planets) test.nekro.addPlanet(planet);
    }

    private int progress(String secret) {
        return ListPlayerInfoService.getPlayerProgressOnObjective(secret, test.game, test.nekro);
    }

    // Brave the Void (completed by 62% of the copies that were kept) with ships beside an anomaly: five rounds at 0.18
    // a round is 1 - 0.82^5 = 0.63, so the chance is 0.5 * 0.62 + 0.5 * 0.63 = 0.62 and an action secret's point is
    // worth 1.2 of a status point: 0.75. Establish a Perimeter (45%) with no PDS standing has nothing from the
    // board either: 0.45.
    @Test
    void keepsBraveTheVoidBesideAnAnomalyOverAnUnstartedPdsSecret() {
        test.units(home, "space", test.nekro, UnitType.Destroyer, 1);
        test.place("41", neighbour);
        test.nekro.setSecret("btv");
        test.nekro.setSecret("eap");

        assertThat(value("btv")).isCloseTo(0.75, within(0.01));
        assertThat(value("eap")).isCloseTo(0.45, within(0.01));
        assertThat(discarded()).isEqualTo("eap");
    }

    // Progress beats the prior: a secret that can be scored this status phase is worth a flat 2.0, far above any
    // chance, even a good one like the 0.75 above.
    @Test
    void keepsAScorableSecretOverABetterThanAveragePrior() {
        Tile docked = test.place("19", neighbour);
        test.sol.addPlanet("wellon");
        test.units(docked, "wellon", test.sol, UnitType.Spacedock, 1);
        test.units(docked, "space", test.nekro, UnitType.Destroyer, 1);
        test.place("41", otherNeighbourOf(neighbour));
        test.nekro.setSecret("csl");
        test.nekro.setSecret("btv");

        assertThat(progress("csl")).isEqualTo(1);
        assertThat(value("csl")).isEqualTo(2.0);
        assertThat(value("btv")).isLessThan(1.0);
        assertThat(discarded()).isEqualTo("btv");
    }

    // Strengthen Bonds is a met status secret with a poor prior (it needs a promissory note in the play area, which the
    // old valuation scaled by 0.1 even once met). Hoard Raw Materials at 6 of 12 resources is half done: 0.5 + 0.5 *
    // 0.84 = 0.92, then 0.5 * 0.84 + 0.5 * 0.92 = 0.88. A met secret is 2.0, so the half-done one goes.
    @Test
    void keepsAMetSecretOverAHalfFinishedOne() {
        controls("19", "101", "wellon");
        controls("21", "102", "thibah");
        aiHoldsInPlayArea("blue_cf");
        test.nekro.setSecret("sb");
        test.nekro.setSecret("hrm");

        assertThat(progress("hrm")).isEqualTo(6);
        assertThat(value("sb")).isEqualTo(2.0);
        assertThat(value("hrm")).isCloseTo(0.88, within(0.01));
        assertThat(discarded()).isEqualTo("hrm");
    }

    // Round five, a rival on 8 of 10: one round is left, so only one status secret can still be scored. Establish
    // Hegemony at 10 of 12 influence is 0.83 done: 0.83 + 0.17 * 0.77 * (1 round / 3) = 0.88, then 0.2 * 0.77 + 0.8 *
    // 0.88
    // = 0.85. Demonstrate Your Power with enemy ships a move from a stack of three is 0.6 in the one round left:
    // 0.2 * 0.70 + 0.8 * 0.60 = 0.62, times 1.2 = 0.74. Establish Hegemony is kept, until a second status secret is
    // already scorable: then it cannot be scored in time and loses half its value, 0.43.
    @Test
    void aStatusSecretLosesValueWhenAnotherCompetesForTheLastStatusPhase() {
        test.game.setRound(5);
        test.points(test.sol, 8);
        controls("37", "101", "arinam", "meer");
        controls("24", "102", "meharxull");
        controls("21", "103", "thibah");
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        Tile battlefield = test.place("46", neighbour);
        test.units(battlefield, "space", test.sol, UnitType.Destroyer, 1);
        test.nekro.setSecret("eh");
        test.nekro.setSecret("dyp");
        assertThat(progress("eh")).isEqualTo(10);

        assertThat(value("eh")).isCloseTo(0.85, within(0.01));
        assertThat(value("dyp")).isCloseTo(0.74, within(0.01));
        assertThat(discarded()).isEqualTo("dyp");

        aiHoldsInPlayArea("blue_cf");
        test.nekro.setSecret("sb");

        assertThat(value("sb")).isEqualTo(2.0);
        assertThat(value("eh")).isCloseTo(0.43, within(0.01));
        assertThat(discarded()).isEqualTo("eh");
    }

    // Drive the Debate needs an election to go its way, which the Nekro Virus (Galactic Threat) can hardly expect: 0.5
    // *
    // 0.47 + 0.5 * 0.02 = 0.245, far below Establish a Perimeter (0.45) and Mine Rare Metals (0.46) with no progress.
    @Test
    void ranksDriveTheDebateBelowStatusSecretsWithNoProgress() {
        placeBeside(AiTestGame.HOME, "35", "38", "20");
        test.nekro.setSecret("dtd");
        test.nekro.setSecret("eap");
        test.nekro.setSecret("mrm");

        assertThat(value("dtd")).isCloseTo(0.245, within(0.01));
        assertThat(value("eap")).isCloseTo(0.45, within(0.01));
        assertThat(value("mrm")).isCloseTo(0.46, within(0.01));
        assertThat(discarded()).isEqualTo("dtd");
    }

    private double solValue(String secret) {
        return SecretValue.keepValue(test.game, test.sol, secret);
    }

    private void custodiansTaken() {
        test.game.addCustomPO("Custodians", 0);
        test.game.scorePublicObjective(test.nekro.getUserID(), 0);
    }

    // Custodians are taken in round 2 in nearly every game, so before they go Drive the Debate only loses the round
    // that has no agenda phase yet. Sol holds all the influence at the table, so each of the roughly eight agendas left
    // is an election about a third of the time that it wins: 1 - 0.9^8 = 0.57 is capped at 0.5, and 0.5 * 0.47 + 0.5 *
    // 0.5 = 0.485 outranks Establish a Perimeter and Mine Rare Metals with no progress, before and after.
    @Test
    void keepsDriveTheDebateWhileAgendaPhasesRemain() {
        test.solHomeBeside(neighbour);
        test.sol.setSecret("dtd");
        test.sol.setSecret("eap");
        test.sol.setSecret("mrm");
        double beforeCustodians = solValue("dtd");
        String discardedBefore =
                SecretValue.lowestValueSecret(test.game, test.sol).orElseThrow();

        custodiansTaken();

        assertThat(beforeCustodians).isCloseTo(0.485, within(0.01));
        assertThat(discardedBefore).isNotEqualTo("dtd");
        assertThat(solValue("dtd")).isCloseTo(0.485, within(0.01));
        assertThat(solValue("dtd")).isGreaterThan(solValue("eap")).isGreaterThan(solValue("mrm"));
        assertThat(SecretValue.lowestValueSecret(test.game, test.sol).orElseThrow())
                .isNotEqualTo("dtd");
    }

    // A seat with a sliver of the table's influence rarely wins an election: its chance falls with the share of the
    // influence squared, so Drive the Debate is worth less to it than to the seat that holds the votes.
    @Test
    void valuesDriveTheDebateByTheSeatsShareOfTheVotes() {
        custodiansTaken();
        test.solHomeBeside(neighbour);
        test.sol.setSecret("dtd");
        double leading = solValue("dtd");

        controls("37", "101", "arinam", "meer");
        controls("24", "102", "meharxull");
        double outvoted = solValue("dtd");

        assertThat(leading).isGreaterThan(outvoted);
    }

    // Betray a Friend: with another player's promissory note in the play area, 0.125 a round is 1 - 0.875^5 = 0.49
    // and the value 1.2 * (0.5 * 0.49 + 0.5 * 0.49) = 0.59; without it 0.05 a round is 0.23, 1.2 * (0.245 + 0.115) =
    // 0.43; Turn Their Fleets to Dust with no PDS (36%, 0.03 a round) is 1.2 * (0.18 + 0.07) = 0.30.
    @Test
    void ranksBetrayAFriendByWhetherANoteIsInPlay() {
        test.nekro.setSecret("baf");
        test.nekro.setSecret("ttfd");
        double without = value("baf");
        aiHoldsInPlayArea("blue_cf");
        double with = value("baf");

        assertThat(with).isCloseTo(0.59, within(0.01));
        assertThat(without).isCloseTo(0.43, within(0.01));
        assertThat(value("ttfd")).isCloseTo(0.30, within(0.01));
        assertThat(with).isGreaterThan(without);
        assertThat(without).isGreaterThan(value("ttfd"));
    }

    // At the start of the game the keep choice also counts how often a secret is scored by the second status phase
    // (SecretStatistics.openingCompletion): Threaten Enemies is scored early about two times in three, Demonstrate
    // Your Power almost never, so Threaten Enemies is kept even though the whole-game model alone ranks Demonstrate
    // Your Power higher. From round two on the opening bonus is gone and the whole-game model decides alone.
    @Test
    void keepsASecretThatIsScoredEarlyWhenChoosingAtTheStart() {
        test.nekro.setSecret("te");
        test.nekro.setSecret("dyp");
        assertThat(discarded()).isEqualTo("te");

        test.game.setRound(1);

        assertThat(value("te") - value("dyp")).isGreaterThan(0.4);
        assertThat(discarded()).isEqualTo("dyp");
    }

    // Seize an Icon needs a legendary planet. With none on the board, only a lucky explore could still make one, so the
    // secret keeps a tenth of its value. One that another player holds can be taken by force, which keeps four tenths.
    @Test
    void valuesSeizeAnIconByWhetherALegendaryPlanetIsWithinGrasp() {
        test.nekro.setSecret("sai");
        double without = value("sai");
        test.place("65", neighbour);
        double with = value("sai");
        test.sol.addPlanet("primor");
        double taken = value("sai");

        assertThat(without).isCloseTo(with * 0.1, within(0.01));
        assertThat(taken).isCloseTo(with * 0.4, within(0.01));
    }

    private double dfatValue(String nexusTile, Runnable setup) {
        setUp();
        test.place(nexusTile, neighbour);
        test.nekro.setSecret("dfat");
        setup.run();
        return value("dfat");
    }

    private void gammaTokenAt(String token) {
        Tile far = test.place("41", otherNeighbourOf(neighbour));
        far.addToken(Mapper.getTokenID(token), "space");
    }

    // Defy Space and Time needs units in the nexus, and the locked nexus (82a) only has a gamma wormhole: with nothing
    // to open it the secret keeps 0.12 of what it is worth once the nexus is active (82b).
    @Test
    void scalesDefySpaceAndTimeDownWhileTheNexusIsLockedWithNoWayIn() {
        double open = dfatValue("82b", () -> {});
        double locked = dfatValue("82a", () -> {});

        assertThat(open).isGreaterThan(0);
        assertThat(locked).isCloseTo(open * 0.12, within(0.005));
    }

    @Test
    void keepsDefySpaceAndTimeWhileAGhostShipCouldOpenTheNexus() {
        double open = dfatValue("82b", () -> {});

        assertThat(dfatValue("82a", () -> test.nekro.setActionCard("ghost_ship", 101)))
                .isCloseTo(open, within(0.005));
    }

    @Test
    void keepsDefySpaceAndTimeWhileACreussSeatCouldOpenTheNexus() {
        double open = dfatValue("82b", () -> {});

        assertThat(dfatValue("82a", () -> test.addSeat("200000000000000003", "ghost", "purple")))
                .isCloseTo(open, within(0.005));
    }

    @Test
    void keepsDefySpaceAndTimeWhileAGammaWormholeTokenIsOnTheBoard() {
        double open = dfatValue("82b", () -> {});

        assertThat(dfatValue("82a", () -> gammaTokenAt("gamma"))).isCloseTo(open, within(0.005));
    }

    // The gamma_* data set is homebrew: its wormhole tokens are not a way to open the nexus.
    @Test
    void ignoresAHomebrewGammaWormholeTokenWhenOpeningTheNexus() {
        double open = dfatValue("82b", () -> {});

        assertThat(dfatValue("82a", () -> gammaTokenAt("whgamma"))).isCloseTo(open * 0.12, within(0.005));
    }

    @Test
    void discardsLockedDefySpaceAndTimeForAnOrdinarySecret() {
        test.place("82a", neighbour);
        test.nekro.setSecret("dfat");
        test.nekro.setSecret("eap");

        assertThat(discarded()).isEqualTo("dfat");
    }

    // Secrets the table has no statistics for (homebrew, or ids that are not cards at all) fall back to even odds
    // and never throw.
    @Test
    void valuesAnUnknownSecretAtEvenOdds() {
        test.nekro.setSecret("survey");

        assertThat(value("survey")).isCloseTo(0.5, within(0.01));
    }

    private List<String> placeBeside(String center, String... tileIds) {
        List<String> positions = MapPositionMapper.getAdjacentTilePositions(center).stream()
                .filter(candidate -> !"x".equals(candidate) && test.game.getTileByPosition(candidate) == null)
                .limit(tileIds.length)
                .toList();
        for (int i = 0; i < positions.size(); i++) test.place(tileIds[i], positions.get(i));
        return positions;
    }

    private double traitValue(String secret, Runnable board) {
        setUp();
        test.nekro.setSecret(secret);
        board.run();
        return value(secret);
    }

    private static final String[] RICH_INDUSTRIAL = {"36", "19", "21", "22"};

    // Monopolize Production needs four industrial planets. Five reachable ones (Arnor, Lor, Wellon, Thibah, Tarmann
    // beside the home system, none held) is the full value, 0.5 * 0.51 + 0.5 * 0.51 = 0.51. Four reachable is
    // 0.1 + 0.9 * (4 - 2) / 3 = 0.7 of it, and two or fewer is the 0.1 floor.
    @Test
    void scalesATraitSecretBySoManyOfItsPlanetsAreReachable() {
        double rich = traitValue("mp", () -> placeBeside(AiTestGame.HOME, RICH_INDUSTRIAL));
        double four = traitValue("mp", () -> placeBeside(AiTestGame.HOME, "36", "19", "21"));
        double thin = traitValue("mp", () -> placeBeside(AiTestGame.HOME, "19"));

        assertThat(rich).isCloseTo(0.51, within(0.01));
        assertThat(four).isCloseTo(rich * 0.7, within(0.005));
        assertThat(thin).isCloseTo(rich * 0.1, within(0.005));
    }

    // Reachable is not owned: a trait planet on the far side of a rival home counts half, and one a rival already
    // holds 0.3. Four industrial planets beside the home system are worth 4; the same four beside Sol's home are
    // 4 * 0.5 = 2, and four that Sol holds 4 * 0.3 = 1.2, both at the floor.
    @Test
    void weighsTraitPlanetsByWhoCanReachThem() {
        double near = traitValue("mp", () -> placeBeside(AiTestGame.HOME, "36", "19", "21"));
        double foreign = traitValue("mp", () -> {
            Tile solHome = test.solHomeBeside(neighbour);
            placeBeside(solHome.getPosition(), "36", "19", "21");
        });
        double rivalHeld = traitValue("mp", () -> {
            placeBeside(AiTestGame.HOME, "36", "19", "21");
            for (String planet : List.of("arnor", "lor", "wellon", "thibah")) test.sol.addPlanet(planet);
        });

        assertThat(near).isCloseTo(0.51 * 0.7, within(0.01));
        assertThat(foreign).isCloseTo(0.51 * 0.1, within(0.005));
        assertThat(rivalHeld).isCloseTo(0.51 * 0.1, within(0.005));
    }

    // Planets the seat already owns count in full, so a trait secret half built stays at full value on a map with few
    // free planets left.
    @Test
    void countsTheTraitPlanetsTheSeatOwnsInFull() {
        double owned = traitValue("mp", () -> {
            placeBeside(AiTestGame.HOME, "36", "19", "21");
            for (String planet : List.of("arnor", "lor", "wellon", "thibah")) test.nekro.addPlanet(planet);
        });

        assertThat(owned).isGreaterThan(0.51);
    }

    @Test
    void discardsAThinTraitSecretButKeepsOneOnARichBoard() {
        traitValue("mp", () -> {
            test.nekro.setSecret("eap");
            placeBeside(AiTestGame.HOME, "19");
        });
        assertThat(discarded()).isEqualTo("mp");

        traitValue("mp", () -> {
            test.nekro.setSecret("eap");
            placeBeside(AiTestGame.HOME, RICH_INDUSTRIAL);
        });
        assertThat(discarded()).isEqualTo("eap");
    }

    @Test
    void scalesForgeAnAllianceAndMineRareMetalsTheSameWay() {
        double richCultural = traitValue("faa", () -> placeBeside(AiTestGame.HOME, "32", "33", "25"));
        double thinCultural = traitValue("faa", () -> placeBeside(AiTestGame.HOME, "25"));
        double richHazardous = traitValue("mrm", () -> placeBeside(AiTestGame.HOME, "35", "38", "20"));
        double thinHazardous = traitValue("mrm", () -> placeBeside(AiTestGame.HOME, "20"));

        assertThat(thinCultural).isCloseTo(richCultural * 0.1, within(0.005));
        assertThat(thinHazardous).isCloseTo(richHazardous * 0.1, within(0.005));
        assertThat(richHazardous).isCloseTo(0.46, within(0.01));
    }

    private String otherNeighbourOf(String position) {
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate))
                .findFirst()
                .orElseThrow();
    }
}
