package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.AgendaModel;
import ti4.model.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.testUtils.BaseTi4Test;

class AgendaValuesTest extends BaseTi4Test {

    private static final String LODOR = "lodor";
    private static final String MEHAR_XULL = "meharxull";

    private AiTestGame test;
    private Player nekro;
    private Player sol;
    private Player hacan;
    private Tile lodorTile;

    // A simple three-seat table: Nekro and Sol at home, Hacan seated without anything on the board, Lodor (cultural,
    // with an alpha wormhole) beside Nekro's home and Mehar Xull (hazardous) somewhere unowned.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        nekro = test.nekro;
        sol = test.sol;
        hacan = test.addSeat("300000000000000003", "hacan", "yellow");
        test.nekroHome();
        String lodorPosition = AiTestGame.neighbourOf(AiTestGame.HOME);
        lodorTile = test.place("26", lodorPosition);
        test.place("24", "105");
        test.place("18", "000");
        test.solHomeBeside(lodorPosition);
        test.game.setSpeaker(sol);
    }

    private double value(Player voter, String agenda, String outcome, String ownVote) {
        OptionalDouble value = AgendaValues.of(test.game, voter, agenda, outcome, ownVote);
        assertThat(value).as("%s / %s", agenda, outcome).isPresent();
        return value.getAsDouble();
    }

    private static List<String> officialAgendas() {
        return Mapper.getAgendas().values().stream()
                .filter(model -> model.getSource() == ComponentSource.base || model.getSource() == ComponentSource.pok)
                .map(AgendaModel::getAlias)
                .toList();
    }

    // Every base and Prophecy of Kings agenda is in the catalog and survives every kind of outcome string: the
    // For/Against words, each seat, an owned and an unowned planet, a strategy card number and a law in play.
    @Test
    void everyOfficialAgendaHasAFiniteValue() {
        nekro.addPlanet(LODOR);
        test.game.getLaws().put("minister_war", 1);
        test.game.getLawsInfo().put("minister_war", sol.getFaction());
        test.game.getLaws().put("conventions", 2);
        List<String> outcomes = List.of(
                "for",
                "against",
                "nekro",
                "sol",
                "hacan",
                LODOR,
                MEHAR_XULL,
                "jord",
                "3",
                "minister_war",
                "conventions");

        List<String> agendas = officialAgendas();
        assertThat(agendas).hasSize(63);
        for (String agenda : agendas) {
            for (String outcome : outcomes) {
                for (String ownVote : List.of("", "for", "against", outcome)) {
                    assertThatCode(() -> assertThat(value(nekro, agenda, outcome, ownVote))
                                    .isFinite())
                            .as("%s / %s / %s", agenda, outcome, ownVote)
                            .doesNotThrowAnyException();
                }
            }
        }
    }

    @Test
    void homebrewAgendaHasNoValue() {
        assertThat(AgendaValues.of(test.game, nekro, "absol_revolution", "for", "for"))
                .isEmpty();
    }

    @Test
    void onlyAgendasThatSingleOutTheirVotersDependOnTheOwnVote() {
        assertThat(AgendaValues.dependsOnOwnVote("mutiny")).isTrue();
        assertThat(AgendaValues.dependsOnOwnVote("rep_govt")).isTrue();
        assertThat(AgendaValues.dependsOnOwnVote("minister_war")).isFalse();
    }

    // Mutiny: every seat that voted For gains a point if For wins and loses one if Against wins. A voter that voted
    // For stands to gain or lose its own point; an abstaining voter does not.
    @Test
    void mutinyIsWorthAPointToAForVoterAndCostsOneWhenAgainstWins() {
        test.points(nekro, 3);

        assertThat(value(nekro, "mutiny", "for", "for")).isPositive();
        assertThat(value(nekro, "mutiny", "against", "for")).isNegative();
        assertThat(value(nekro, "mutiny", "for", "")).isZero();
        assertThat(AgendaValues.ownVoteAdjustment(test.game, nekro, "mutiny", "against", "for"))
                .isNegative();
    }

    // A minister office is a gain for whoever holds it, so electing the voter is good and handing it to the rival
    // who leads on points is bad, worse than handing it to a trailing rival.
    @Test
    void ministerOfficeHelpsTheVoterAndHurtsWhenTheLeaderGetsIt() {
        test.points(sol, 8);

        assertThat(value(nekro, "minister_sciences", "nekro", "")).isPositive();
        double leader = value(nekro, "minister_sciences", "sol", "");
        double trailer = value(nekro, "minister_sciences", "hacan", "");
        assertThat(leader).isNegative();
        assertThat(leader).isLessThan(trailer);
    }

    // The Peace and War ministers are strong one-shot powers: worth chasing for itself, and when a rival must get
    // one, a rival far behind on points is the cheaper hand to put it in.
    @Test
    void peaceAndWarMinistersAreSoughtAndBetterGivenToATrailingRival() {
        test.points(sol, 8);
        test.points(nekro, 7);

        for (String minister : List.of("minister_peace", "minister_war")) {
            assertThat(value(nekro, minister, "nekro", "")).as(minister).isGreaterThanOrEqualTo(10);
            double leader = value(nekro, minister, "sol", "");
            double trailer = value(nekro, minister, "hacan", "");
            assertThat(trailer).as(minister).isNegative().isGreaterThan(leader);
        }
    }

    // Mutiny shares its point among everyone on "for", so it stays on the group weighting: with one rival already on
    // "for", joining it for a point of its own is still better than voting it down to take that rival's point away.
    @Test
    void mutinyIsStillWorthJoiningWhenARivalVotedFor() {
        test.points(sol, 3);
        test.points(nekro, 3);
        test.game.setCurrentAgendaVote("for", "blue_3");

        assertThat(value(nekro, "mutiny", "for", "for")).isGreaterThan(value(nekro, "mutiny", "against", "against"));
    }

    // Demilitarized Zone destroys every unit on the planet and forbids rebuilding, so it costs the owner of a planet
    // with a space dock and a PDS on it; on a rival's fortified planet it is good for the voter.
    @Test
    void demilitarizedZoneOnTheVotersFortifiedPlanetIsBad() {
        nekro.addPlanet(LODOR);
        test.units(lodorTile, LODOR, nekro, UnitType.Spacedock, 1);
        test.units(lodorTile, LODOR, nekro, UnitType.Pds, 1);
        test.units(lodorTile, LODOR, nekro, UnitType.Infantry, 2);

        assertThat(value(nekro, "demilitarized_zone", LODOR, "")).isNegative();
        assertThat(value(sol, "demilitarized_zone", LODOR, "")).isPositive();
    }

    // Core Mining gives the planet two resources for the rest of the game at the cost of one infantry, which the
    // catalog counts as good for the owner.
    @Test
    void coreMiningOnTheVotersHazardousPlanetIsGood() {
        nekro.addPlanet(MEHAR_XULL);

        assertThat(value(nekro, "core_mining", MEHAR_XULL, "")).isPositive();
        assertThat(value(sol, "core_mining", MEHAR_XULL, "")).isNegative();
        assertThat(value(nekro, "core_mining", LODOR, "")).isZero();
    }

    // Judicial Abolishment of a law is the negative of that law's value while it stays in play.
    @Test
    void abolishingTheVotersOwnMinistryIsBad() {
        test.game.getLaws().put("minister_war", 1);
        test.game.getLawsInfo().put("minister_war", nekro.getFaction());

        assertThat(value(nekro, "abolishment", "minister_war", "")).isNegative();
        assertThat(value(sol, "abolishment", "minister_war", "")).isPositive();
    }
}
