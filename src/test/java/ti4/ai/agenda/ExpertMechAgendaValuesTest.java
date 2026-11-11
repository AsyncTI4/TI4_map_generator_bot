package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ti4.ai.AiTestGame;
import ti4.ai.eval.MechRating;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class ExpertMechAgendaValuesTest extends BaseTi4Test {

    private static final String FAR_AWAY = "105";

    private AiTestGame test;
    private Tile fieldTile;
    private int seats;

    // Nekro and Sol are always seated by AiTestGame; tests add the faction they are about. Mechs are parked in the
    // space area of an empty system nobody else uses unless a test says otherwise.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        fieldTile = test.place("26", "000");
    }

    private double value(Player voter, String agenda, String outcome, String ownVote) {
        OptionalDouble value = AgendaValues.of(test.game, voter, agenda, outcome, ownVote);
        assertThat(value).as("%s / %s", agenda, outcome).isPresent();
        return value.getAsDouble();
    }

    private Player seat(String faction, String color) {
        return test.addSeat("3000000000000000" + (10 + ++seats), faction, color);
    }

    private void mechsOut(Player seat, int count) {
        test.units(fieldTile, "space", seat, UnitType.Mech, count);
    }

    @Test
    void expertValueReplacesTheLegacyFormulaForBothMechAgendas() {
        Player arborec = seat("arborec", "green");
        mechsOut(arborec, 2);

        assertThat(value(arborec, "articles_war", "for", ""))
                .isCloseTo(ExpertMechAgendaValues.articlesOfWar(test.game, arborec, "for", ""), within(1e-9));
        assertThat(value(arborec, "rearmament", "against", ""))
                .isCloseTo(ExpertMechAgendaValues.rearmamentAgreement(test.game, arborec, "against"), within(1e-9));
    }

    @Test
    void otherAgendasStayOnTheLegacyFormula() {
        Player arborec = seat("arborec", "green");
        assertThat(ExpertMechAgendaValues.valueOf(new AgendaView(test.game, arborec, false), "minister_war", "for", ""))
                .isNull();
    }

    // Arborec loses production, shield and deploy when Articles of War passes, more so with several mechs out.
    @Test
    void arborecVotesAgainstArticlesOfWar() {
        Player arborec = seat("arborec", "green");
        mechsOut(arborec, 3);

        assertThat(value(arborec, "articles_war", "for", "")).isNegative();
        assertThat(ExpertMechAgendaValues.articlesStake(test.game, arborec))
                .isGreaterThan(MechRating.articlesStake(arborec) * AgendaValueMath.lawRounds(test.game));
    }

    // Articles stake for a deploy mech is flat: mechs on the board don't change it.
    @Test
    void deployMechStakeDoesNotScaleWithMechsOut() {
        Player letnev = seat("letnev", "red");
        double none = ExpertMechAgendaValues.articlesStake(test.game, letnev);
        mechsOut(letnev, 4);

        assertThat(ExpertMechAgendaValues.articlesStake(test.game, letnev)).isEqualTo(none);
    }

    // Pattern (1): a weak-mech seat votes For to shut down a strong rival mech, and more so when that rival is its
    // neighbour (Hacan next to Xxcha's cannon).
    @Test
    void hacanWantsArticlesMoreWhenXxchaIsItsNeighbour() {
        Player hacan = seat("hacan", "yellow");
        Player xxcha = seat("xxcha", "orange");
        Tile hacanHome = test.place("16", AiTestGame.HOME);
        hacan.addPlanet("arretze");
        test.units(hacanHome, "arretze", hacan, UnitType.Infantry, 1);
        Tile xxchaHome = test.place("14", FAR_AWAY);
        xxcha.addPlanet("archonren");
        test.units(xxchaHome, "archonren", xxcha, UnitType.Infantry, 1);
        double apart = value(hacan, "articles_war", "for", "");

        test.game.removeTile(FAR_AWAY);
        Tile besideHacan = test.place("14", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(besideHacan, "archonren", xxcha, UnitType.Infantry, 1);
        assertThat(hacan.getNeighbouringPlayers(true)).contains(xxcha);
        double beside = value(hacan, "articles_war", "for", "");

        assertThat(apart).isPositive();
        assertThat(beside).isGreaterThan(apart);
    }

    // Each seat that voted For gains 3 TG when Against wins; an abstainer or Against voter gains nothing.
    @Test
    void againstPaysForVotersThreeTradeGoods() {
        Player hacan = seat("hacan", "yellow");

        assertThat(value(hacan, "articles_war", "against", "for")).isEqualTo(3.0);
        assertThat(value(hacan, "articles_war", "against", "against")).isZero();
        assertThat(value(hacan, "articles_war", "against", "")).isZero();
    }

    // Rearmament Against replaces every mech with an infantry: Arborec, Saar and Naaz with several out fight it.
    @ParameterizedTest
    @ValueSource(strings = {"arborec", "saar", "naaz"})
    void mechHeavySeatsFightRearmamentAgainst(String faction) {
        Player seat = seat(faction, "green");
        mechsOut(seat, 3);

        assertThat(value(seat, "rearmament", "against", "")).as(faction).isNegative();
    }

    // Hacan, Nekro and Jol-Nar with no mechs out support Against cheaply to hurt a mech-heavy rival.
    @Test
    void seatsWithoutMechsOutSupportRearmamentAgainst() {
        Player arborec = seat("arborec", "green");
        Player hacan = seat("hacan", "yellow");
        Player jolnar = seat("jolnar", "purple");
        mechsOut(arborec, 4);

        for (Player voter : List.of(hacan, test.nekro, jolnar)) {
            assertThat(value(voter, "rearmament", "against", ""))
                    .as(voter.getFaction())
                    .isPositive();
        }
    }

    // A good mech hurts more to lose than a weak one: per mech, Arborec's downgrade beats Nekro's plain body.
    @Test
    void rearmamentDowngradeAddsAbilityQuality() {
        Player arborec = seat("arborec", "green");
        mechsOut(arborec, 2);
        mechsOut(test.nekro, 2);

        assertThat(ExpertMechAgendaValues.mechDowngrades(test.game, arborec))
                .isGreaterThan(ExpertMechAgendaValues.mechDowngrades(test.game, test.nekro));
        assertThat(ExpertMechAgendaValues.mechDowngrades(test.game, test.nekro)).isCloseTo(3.2, within(1e-9));
    }

    // For places a mech at home: worth the home mech rating, but only with a mech in reinforcements and a home planet.
    @Test
    void rearmamentForIsTheHomeMechWhenOneIsInReinforcements() {
        Player arborec = seat("arborec", "green");
        test.place("05", AiTestGame.HOME);
        arborec.addPlanet("nestphar");
        mechsOut(arborec, 3);

        assertThat(ExpertMechAgendaValues.homeMechGain(test.game, arborec)).isEqualTo(MechRating.homeWorth(arborec));

        mechsOut(arborec, 1);
        assertThat(ExpertMechAgendaValues.homeMechGain(test.game, arborec)).isZero();
    }

    @Test
    void rearmamentForGivesNothingWithoutAHomePlanet() {
        Player arborec = seat("arborec", "green");
        test.place("05", AiTestGame.HOME);

        assertThat(ExpertMechAgendaValues.homeMechGain(test.game, arborec)).isZero();
    }

    // Xxcha's home cannon mech makes For good for it when its rivals hold no home planet on the board.
    @Test
    void xxchaLikesRearmamentFor() {
        Player xxcha = seat("xxcha", "orange");
        test.place("14", AiTestGame.HOME);
        xxcha.addPlanet("archonren");

        assertThat(value(xxcha, "rearmament", "for", "")).isPositive();
    }
}
