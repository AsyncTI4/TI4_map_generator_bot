package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.secrets.SecretOdds;
import ti4.ai.strategy.CommandTokenValue;
import ti4.ai.strategy.ResearchPolicy;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class ExpertCardValuesTest extends BaseTi4Test {

    private static final String LODOR = "lodor";
    private static final String MEHAR_XULL = "meharxull";
    private static final double EPS = 1e-9;

    private AiTestGame test;
    private Player nekro;
    private Player sol;
    private Player hacan;
    private Tile lodorTile;

    // Nekro and Sol at home (Mordai II 4/0 and Jord 4/2), Hacan seated with nothing on the board. Lodor (3/1) sits
    // between the homes and Mehar Xull (1/3) is unowned elsewhere. Round 3, so no opening-round secret tempo.
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
        test.game.setRound(3);
    }

    private AgendaView ownView(Player seat) {
        return new AgendaView(test.game, seat, true);
    }

    private double value(Player voter, String agenda, String outcome) {
        OptionalDouble value = AgendaValues.of(test.game, voter, true, agenda, outcome, outcome);
        assertThat(value).as("%s / %s", agenda, outcome).isPresent();
        return value.getAsDouble();
    }

    // The user's number: having your own scored secret made public is worth about 3 TG to you (a freed slot). The
    // rivals' chance to score the new public objective is then weighed against it as usual.
    @Test
    void ownLeakedSecretIsWorthThreeTradeGoods() {
        sol.setSecretScored("gamf");
        AgendaView view = ownView(sol);

        assertThat(ExpertCardValues.leakGain(view, sol, sol, "gamf")).isEqualTo(ExpertCardValues.OWN_SECRET_LEAKED);
        double rivalCost =
                (ExpertCardValues.leakGain(view, nekro, sol, "gamf") * AgendaValueMath.threat(test.game, nekro)
                                + ExpertCardValues.leakGain(view, hacan, sol, "gamf")
                                        * AgendaValueMath.threat(test.game, hacan))
                        / 2;
        assertThat(value(sol, "classified", "gamf"))
                .isCloseTo(ExpertCardValues.OWN_SECRET_LEAKED - rivalCost, within(EPS));
    }

    // A seat whose own secret is ready (Nekro meets Occupy the Fringe) has an easy secret, so no hard-hand bonus.
    @Test
    void readyOwnSecretIsNotAHardHand() {
        nekro.addPlanet(LODOR);
        test.units(lodorTile, LODOR, nekro, UnitType.Infantry, 9);
        nekro.setSecret("otf");

        assertThat(ExpertCardValues.hardSecretsChance(ownView(nekro), nekro)).isZero();
    }

    @Test
    void unscoredOutcomeIsWorthNothing() {
        assertThat(value(nekro, "classified", "gamf")).isZero();
    }

    // Nekro holds only hard secrets (5 dreadnoughts, 3 space docks) and already meets Occupy the Fringe, which Sol
    // scored. Making that easy status secret public is worth the point plus the hard-hand bonus.
    @Test
    void hardSecretsMakeAnEasyStatusSecretWorthMore() {
        sol.setSecretScored("otf");
        nekro.addPlanet(LODOR);
        test.units(lodorTile, LODOR, nekro, UnitType.Infantry, 9);
        nekro.setSecret("gamf");
        nekro.setSecret("fwm");

        AgendaView view = ownView(nekro);
        assertThat(ExpertCardValues.hardSecretsChance(view, nekro)).isEqualTo(1.0);
        assertThat(ExpertCardValues.leakWeight(view, nekro, "otf")).isEqualTo(1 + ExpertCardValues.HARD_SECRETS_BONUS);
        assertThat(ExpertCardValues.leakGain(view, nekro, sol, "otf"))
                .isCloseTo(
                        ScoringStakes.scoreValue(test.game, nekro, 1) * (1 + ExpertCardValues.HARD_SECRETS_BONUS),
                        within(EPS));
    }

    @Test
    void seatWithoutUnscoredSecretsGetsNoHardHandBonus() {
        AgendaView view = ownView(nekro);

        assertThat(ExpertCardValues.hardSecretsChance(view, nekro)).isZero();
        assertThat(ExpertCardValues.leakWeight(view, nekro, "otf")).isEqualTo(1.0);
    }

    // Leak test: Nekro judges Sol's hand only by how many secrets Sol holds, never by which ones.
    @Test
    void rivalSecretsAreJudgedByCountOnly() {
        AgendaView view = ownView(nekro);
        sol.setSecret("gamf");
        double withHardHand = ExpertCardValues.hardSecretsChance(view, sol);
        sol.removeSecret(sol.getSecretsUnscored().get("gamf"));
        sol.setSecret("hrm");
        double withOtherHand = ExpertCardValues.hardSecretsChance(view, sol);

        assertThat(withHardHand).isEqualTo(withOtherHand).isCloseTo(1 - SecretOdds.BASE_RATE, within(EPS));
    }

    // Demonstrate Your Power is an action-phase secret: the leader (Nekro, 5) and the seat far behind (Hacan, 0)
    // like it more; Sol, one point off the lead, gets no bonus.
    @Test
    void actionSecretFavoursLeaderAndFarBehind() {
        test.points(nekro, 5);
        test.points(sol, 4);
        AgendaView view = ownView(nekro);
        double bonus = 1 + ExpertCardValues.ACTION_CONTENDER_BONUS;

        assertThat(ExpertCardValues.leakWeight(view, nekro, "dyp")).isEqualTo(bonus);
        assertThat(ExpertCardValues.leakWeight(view, hacan, "dyp")).isEqualTo(bonus);
        assertThat(ExpertCardValues.leakWeight(view, sol, "dyp")).isEqualTo(1.0);
    }

    @Test
    void diplomacyIsUselessInTheAgendaPhase() {
        assertThat(ExpertCardValues.secondaryGain(ownView(nekro), nekro, ExpertCardValues.DIPLOMACY))
                .isZero();
        assertThat(value(nekro, "crisis", "2")).isZero();
    }

    @Test
    void nonCardOutcomeIsWorthNothing() {
        assertThat(value(nekro, "crisis", "sol")).isZero();
    }

    // Sol holding only Jord exhausts it to vote, so nothing is left to pay for Technology, Leadership or Warfare.
    @Test
    void votingWithYourOnlyPlanetLeavesNoSecondary() {
        AgendaView view = ownView(sol);

        assertThat(ExpertCardValues.resourcesLeftAfterVoting(test.game, sol)).isZero();
        assertThat(ExpertCardValues.secondaryGain(view, sol, ExpertCardValues.TECHNOLOGY))
                .isZero();
        assertThat(ExpertCardValues.secondaryGain(view, sol, ExpertCardValues.LEADERSHIP))
                .isZero();
        assertThat(ExpertCardValues.secondaryGain(view, sol, ExpertCardValues.WARFARE))
                .isZero();
    }

    // Sol with Jord (4/2), Lodor (3/1) and Mehar Xull (1/3): half the influence (3) goes on votes, so Mehar Xull is
    // exhausted and Jord plus Lodor stay ready: 7 resources and 3 influence left.
    @Test
    void secondariesUsePlanetsLeftAfterVoting() {
        sol.addPlanet(LODOR);
        sol.addPlanet(MEHAR_XULL);
        AgendaView view = ownView(sol);

        assertThat(ExpertCardValues.resourcesLeftAfterVoting(test.game, sol)).isEqualTo(7);
        assertThat(ExpertCardValues.secondaryGain(view, sol, ExpertCardValues.TECHNOLOGY))
                .isCloseTo(
                        Math.max(
                                0,
                                ResearchPolicy.freeResearchWorth(test.game, sol)
                                        - ExpertCardValues.TECHNOLOGY_RESOURCES * ValueScale.RESOURCE),
                        within(EPS));
        assertThat(ExpertCardValues.secondaryGain(view, sol, ExpertCardValues.LEADERSHIP))
                .isCloseTo(CommandTokenValue.tactic(test.game, sol, 1), within(EPS));
        assertThat(ExpertCardValues.secondaryGain(view, sol, ExpertCardValues.WARFARE))
                .isEqualTo(ExpertCardValues.WARFARE_SECONDARY);
    }

    // Exhausted planets are not ready, so they neither vote nor pay.
    @Test
    void exhaustedPlanetsAreNotCounted() {
        sol.addPlanet(LODOR);
        sol.addPlanet(MEHAR_XULL);
        sol.exhaustPlanet("jord");

        assertThat(ExpertCardValues.resourcesLeftAfterVoting(test.game, sol)).isEqualTo(3);
        assertThat(ExpertCardValues.secondaryGain(ownView(sol), sol, ExpertCardValues.TECHNOLOGY))
                .isZero();
    }

    // Leak test: Nekro prices Sol's Politics and Imperial secondaries from public priors, so swapping Sol's hand
    // and secrets does not move them.
    @Test
    void rivalSecondariesUsePublicPriors() {
        AgendaView view = ownView(nekro);
        sol.setSecret("gamf");
        double imperial = ExpertCardValues.secondaryGain(view, sol, ExpertCardValues.IMPERIAL);
        sol.removeSecret(sol.getSecretsUnscored().get("gamf"));
        sol.setSecret("hrm");

        assertThat(ExpertCardValues.secondaryGain(view, sol, ExpertCardValues.IMPERIAL))
                .isEqualTo(imperial)
                .isCloseTo(
                        ExpertCardValues.RIVAL_SECRET_DRAW_POINTS * ScoringStakes.scoreValue(test.game, sol, 1),
                        within(EPS));
        assertThat(ExpertCardValues.secondaryGain(view, sol, ExpertCardValues.POLITICS))
                .isCloseTo(2 * ActionCardPrior.AVERAGE_CARD, within(EPS));
    }
}
