package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.RaceTable;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The race for the custodians in the strategy phase: Leadership's initiative, Politics' speaker token, and the
// research of Gravity Drive are each worth what they do for a seat's arrival at Mecatol Rex and its chance to win it.
class CustodiansStrategyTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int POLITICS = 3;
    private static final String EMPTY_SYSTEM = "46";

    private AiTestGame test;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.game.setRound(2);
        test.game.setPhaseOfGame("strategy");
        Tile rex = test.place("18", "000");
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        test.place(EMPTY_SYSTEM, "101");
        test.place(EMPTY_SYSTEM, "201");
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private void loadedCarrier(Player owner, String position) {
        Tile tile = test.game.getTileByPosition(position);
        if (tile == null) tile = test.place(EMPTY_SYSTEM, position);
        test.units(tile, "space", owner, UnitType.Carrier, 1);
        test.units(tile, "space", owner, UnitType.Infantry, 1);
        owner.setTg(6);
        changed();
    }

    private double benefit(Player seat, int initiative, CardUse use) {
        return StrategyCardPlanner.plan(PlanInputs.of(test.game, seat, PlanMoment.PICK), initiative)
                .benefit(use)
                .map(CardPlan.Benefit::value)
                .orElse(0.0);
    }

    private double tempo(Player seat) {
        return benefit(seat, LEADERSHIP, CardUse.LEADERSHIP_TEMPO);
    }

    private double speaker(Player seat) {
        return benefit(seat, POLITICS, CardUse.POLITICS_SPEAKER);
    }

    // Both seats could land this round. Taking Leadership makes the seat first, which beats the coin flip it is
    // otherwise; with no rival, a Naalu rival (whose 0 token goes first anyway) or as the Naalu seat it is worth
    // nothing.
    @Test
    void leadershipIsWorthActingFirstAgainstARivalThatLandsThisRound() {
        loadedCarrier(test.nekro, "101");
        loadedCarrier(test.sol, "102");

        assertThat(tempo(test.nekro)).isPositive();
    }

    @Test
    void leadershipIsWorthNothingWithoutARivalInTheRace() {
        loadedCarrier(test.nekro, "101");

        assertThat(tempo(test.nekro)).isZero();
    }

    @Test
    void leadershipIsWorthNothingAgainstTheNaaluZeroToken() {
        Player naalu = test.addSeat("200000000000000007", "naalu", "yellow");
        loadedCarrier(test.nekro, "101");
        loadedCarrier(naalu, "102");

        assertThat(tempo(test.nekro)).isCloseTo(0, within(1e-9));
    }

    @Test
    void leadershipIsWorthNothingToTheNaaluSeat() {
        Player naalu = test.addSeat("200000000000000007", "naalu", "yellow");
        loadedCarrier(naalu, "101");
        loadedCarrier(test.sol, "102");

        assertThat(tempo(naalu)).isZero();
    }

    private double speakerRace(boolean rivalOnTheSameRound, String rivalPosition) {
        double alone = speaker(test.nekro);
        if (rivalOnTheSameRound) loadedCarrier(test.sol, rivalPosition);
        return speaker(test.nekro) - alone;
    }

    // Two systems out without Gravity Drive the seat arrives next round, and so does Sol. The speaker picks first the
    // round after: the token is worth a fifth of a victory point.
    @Test
    void politicsIsWorthTheSpeakerTokenWhenARivalArrivesInTheSameRoundAfterThis() {
        loadedCarrier(test.nekro, "201");

        assertThat(speakerRace(true, "202")).isCloseTo(0.2 * 8, within(1e-9));
    }

    @Test
    void politicsGivesTheCurrentSpeakerNoRaceBenefit() {
        loadedCarrier(test.nekro, "201");
        test.game.setSpeaker(test.nekro);
        changed();

        assertThat(speakerRace(true, "202")).isZero();
    }

    @Test
    void politicsGivesNothingWhenTheOnlyRivalArrivesEarlier() {
        loadedCarrier(test.nekro, "201");

        assertThat(speakerRace(true, "102")).isZero();
    }

    @Test
    void aRivalThatLandsThisRoundTakesMostOfTheSpeakersRaceBenefit() {
        loadedCarrier(test.nekro, "201");
        Player hacan = test.addSeat("200000000000000008", "hacan", "green");
        loadedCarrier(hacan, "102");

        assertThat(speakerRace(true, "202")).isCloseTo(0.2 * 8 * 0.2, within(1e-9));
    }

    // Hacan starts with Antimass Deflectors, so researching Gravity Drive gets it to Mecatol Rex a round sooner, ahead
    // of
    // Letnev's coin flip. Once the custodians are taken the drive has no such use.
    @Test
    void gravityDriveIsWorthMoreWhileTheRaceIsOpen() {
        RaceTable table = new RaceTable();
        Player hacan = table.seat("hacan");
        double racing = ResearchPolicy.value(table.test.game, hacan, "gd");

        Tile rex = table.test.game.getTileByPosition(RaceTable.MECATOL);
        rex.getUnitHolders().get("mr").removeToken(Constants.CUSTODIAN_TOKEN_PNG);
        double afterwards = ResearchPolicy.value(table.test.game, hacan, "gd");

        assertThat(racing).isGreaterThan(afterwards);
    }
}
