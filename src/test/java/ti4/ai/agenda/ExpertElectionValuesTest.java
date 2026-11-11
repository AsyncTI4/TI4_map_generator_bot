package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.secrets.SecretValue;
import ti4.ai.strategy.SpeakerTokenValue;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class ExpertElectionValuesTest extends BaseTi4Test {

    private static final double EPS = 1e-9;

    private AiTestGame test;
    private Player nekro;
    private Player sol;

    // A two-seat table (Nekro, Sol) with Nekro's home on the board, voted on in round 2 unless a test says otherwise.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        nekro = test.nekro;
        sol = test.sol;
        test.nekroHome();
        test.game.setSpeaker(sol);
    }

    private Double ownValue(Player holder, String agenda) {
        return ExpertElectionValues.valueOf(new AgendaView(test.game, holder, true), agenda, holder.getFaction(), "");
    }

    private Double value(Player voter, String agenda, String outcome, boolean ownView) {
        return ExpertElectionValues.valueOf(new AgendaView(test.game, voter, ownView), agenda, outcome, "");
    }

    private Player seat(String userId, String faction, String color) {
        return test.addSeat(userId, faction, color);
    }

    @Test
    void agendasLeftOnTheLegacyFormulaHaveNoExpertValue() {
        assertThat(ownValue(nekro, "standardization")).isNull();
        assertThat(ownValue(nekro, "covert")).isNull();
        assertThat(ownValue(nekro, "minister_war_homebrew")).isNull();
    }

    @Test
    void flatElectionsAreWorthTheUsersNumbers() {
        assertThat(ownValue(nekro, "arbiter")).isCloseTo(5, within(EPS));
        assertThat(ownValue(nekro, "minister_exploration")).isCloseTo(1, within(EPS));
        assertThat(ownValue(nekro, "minister_industry")).isCloseTo(1, within(EPS));
        assertThat(ownValue(nekro, "minister_peace")).isCloseTo(5, within(EPS));
        assertThat(ownValue(nekro, "minister_war")).isCloseTo(5, within(EPS));
        assertThat(ownValue(nekro, "minister_antiquities")).isCloseTo(5, within(EPS));
        assertThat(ownValue(nekro, "prophecy")).isCloseTo(1, within(EPS));
        assertThat(ownValue(nekro, "shard_of_the_throne")).isCloseTo(6, within(EPS));
        assertThat(ownValue(nekro, "crown_of_emphidia")).isCloseTo(1, within(EPS));
        assertThat(ownValue(nekro, "crown_of_thalnos")).isCloseTo(0.25, within(EPS));
        assertThat(ownValue(nekro, "grant_reallocation")).isCloseTo(3, within(EPS));
        assertThat(ownValue(nekro, "minister_policy")).isCloseTo(1, within(EPS));
    }

    // No player outcome (e.g. a For/Against word) is worth nothing.
    @Test
    void anOutcomeThatIsNoPlayerIsWorthNothing() {
        assertThat(value(nekro, "arbiter", "for", true)).isZero();
        assertThat(value(nekro, "execution", "for", true)).isZero();
        assertThat(value(nekro, "shard_of_the_throne", "for", true)).isZero();
    }

    // A rival's gain counts against the voter, scaled by the existing threat weighting.
    @Test
    void electingARivalCostsTheVoter() {
        assertThat(value(nekro, "minister_war", "sol", true)).isNegative();
        test.points(sol, 7);
        double leader = value(nekro, "minister_war", "sol", true);
        assertThat(leader).isLessThan(-2.5);
    }

    @Test
    void committeeFormationFallsByRound() {
        test.game.setRound(1);
        assertThat(ownValue(nekro, "committee")).isCloseTo(4, within(EPS));
        test.game.setRound(2);
        assertThat(ownValue(nekro, "committee")).isCloseTo(4, within(EPS));
        test.game.setRound(3);
        assertThat(ownValue(nekro, "committee")).isCloseTo(2, within(EPS));
        test.game.setRound(5);
        assertThat(ownValue(nekro, "committee")).isCloseTo(1, within(EPS));
    }

    @Test
    void ministerOfCommerceFallsByRoundAndKeleresGetsTwoMore() {
        Player keleres = seat("300000000000000003", "keleresa", "yellow");
        test.game.setRound(2);
        assertThat(ownValue(nekro, "minister_commerce")).isCloseTo(3, within(EPS));
        assertThat(ownValue(keleres, "minister_commerce")).isCloseTo(5, within(EPS));
        test.game.setRound(3);
        assertThat(ownValue(nekro, "minister_commerce")).isCloseTo(2, within(EPS));
        test.game.setRound(4);
        assertThat(ownValue(nekro, "minister_commerce")).isCloseTo(1, within(EPS));
        assertThat(ownValue(keleres, "minister_commerce")).isCloseTo(3, within(EPS));
    }

    // Every official Keleres flavour (Mentak, Xxcha and Argent heritage) is checked; the homebrew Keleres Plus is not.
    @Test
    void everyOfficialKeleresAliasIsAFaction() {
        for (String alias : ExpertElectionValues.KELERES)
            assertThat(Mapper.getFaction(alias)).isNotNull();
        assertThat(ExpertElectionValues.KELERES).containsExactlyInAnyOrder("keleresa", "keleresm", "keleresx");
    }

    @Test
    void ministerOfIndustryIsWorthThreeToKeleresAndCreussBeforeRoundFour() {
        Player keleres = seat("300000000000000003", "keleresm", "yellow");
        Player creuss = seat("300000000000000004", ExpertElectionValues.CREUSS, "green");
        test.game.setRound(3);
        assertThat(ownValue(keleres, "minister_industry")).isCloseTo(3, within(EPS));
        assertThat(ownValue(creuss, "minister_industry")).isCloseTo(3, within(EPS));
        assertThat(ownValue(nekro, "minister_industry")).isCloseTo(1, within(EPS));
        test.game.setRound(4);
        assertThat(ownValue(keleres, "minister_industry")).isCloseTo(1, within(EPS));
        assertThat(ownValue(creuss, "minister_industry")).isCloseTo(1, within(EPS));
    }

    @Test
    void ministerOfPolicyGivesYssarilTwoMoreUnlessTheGameEndsSoon() {
        Player yssaril = seat("300000000000000003", ExpertElectionValues.YSSARIL, "yellow");
        assertThat(ownValue(yssaril, "minister_policy")).isCloseTo(3, within(EPS));
        // Sol at 8 of 10 points: the game likely ends next round, so Policy is worth nothing to anyone.
        test.points(sol, 8);
        assertThat(ownValue(yssaril, "minister_policy")).isZero();
        assertThat(ownValue(nekro, "minister_policy")).isZero();
    }

    @Test
    void ministerOfSciencesLosesOnePerRoundAfterTwoAndJolNarGetsTwoMore() {
        Player jolnar = seat("300000000000000003", ExpertElectionValues.JOL_NAR, "yellow");
        test.game.setRound(1);
        assertThat(ownValue(nekro, "minister_sciences")).isCloseTo(4, within(EPS));
        assertThat(ownValue(jolnar, "minister_sciences")).isCloseTo(6, within(EPS));
        test.game.setRound(3);
        assertThat(ownValue(nekro, "minister_sciences")).isCloseTo(3, within(EPS));
        test.game.setRound(6);
        assertThat(ownValue(nekro, "minister_sciences")).isCloseTo(1, within(EPS));
        assertThat(ownValue(jolnar, "minister_sciences")).isCloseTo(3, within(EPS));
    }

    @Test
    void factionConstantsAreRealFactionAliases() {
        for (String alias : List.of(
                ExpertElectionValues.CREUSS,
                ExpertElectionValues.YSSARIL,
                ExpertElectionValues.JOL_NAR,
                ExpertElectionValues.RAL_NEL)) {
            assertThat(Mapper.getFaction(alias)).as(alias).isNotNull();
        }
    }

    // A rival one point from the goal is never handed a point law at its flat value: the point is the win.
    @Test
    void aRivalIsNeverHandedTheWinningPoint() {
        test.points(sol, 9);
        double winning = value(nekro, "crown_of_emphidia", "sol", true);
        assertThat(winning).isLessThanOrEqualTo(-ScoringStakes.WIN_VALUE + EPS);
        assertThat(value(nekro, "shard_of_the_throne", "sol", true))
                .isLessThanOrEqualTo(-ScoringStakes.WIN_VALUE + EPS);
        assertThat(value(nekro, "censure", "sol", true)).isLessThanOrEqualTo(-ScoringStakes.WIN_VALUE + EPS);
    }

    @Test
    void theVoterTakesItsOwnWinningPoint() {
        test.points(nekro, 9);
        assertThat(ownValue(nekro, "crown_of_emphidia")).isGreaterThanOrEqualTo(ScoringStakes.WIN_VALUE - EPS);
    }

    @Test
    void archivedSecretIsWorthFourWithoutDoableSecretsAndOneWhenAllAreScored() {
        assertThat(ownValue(nekro, "secret")).isCloseTo(4, within(EPS));
        nekro.setSecretScored("ans");
        nekro.setSecretScored("btgk");
        nekro.setSecretScored("ctr");
        assertThat(ownValue(nekro, "secret")).isCloseTo(1, within(EPS));
    }

    // Occupy the Seat of the Empire and Strengthen Bonds are far off on this board, so they are hard.
    @Test
    void hardSecretsInHandLeaveArchivedSecretAtFour() {
        nekro.setSecret("ose");
        nekro.setSecret("sb");
        assertThat(SecretValue.keepValue(test.game, nekro, "ose")).isLessThan(0.6);
        assertThat(SecretValue.keepValue(test.game, nekro, "sb")).isLessThan(0.6);
        assertThat(ownValue(nekro, "secret")).isCloseTo(4, within(EPS));
    }

    @Test
    void searchWarrantWithTwoHardSecretsIsWorthFour() {
        nekro.setSecret("ose");
        nekro.setSecret("sb");
        assertThat(ownValue(nekro, "warrant")).isCloseTo(4, within(EPS));
    }

    // Dictate Policy with three laws already in play is ready to score: Archived Secret drops to 1, and Search Warrant
    // still draws two secrets but loses 1 TG for revealing the easy one.
    @Test
    void anEasySecretInHandLowersArchivedSecretAndSearchWarrant() {
        test.game.getLaws().put("minister_war", 1);
        test.game.getLaws().put("conventions", 2);
        test.game.getLaws().put("regulations", 3);
        nekro.setSecret("dp");
        assertThat(SecretValue.keepValue(test.game, nekro, "dp")).isGreaterThanOrEqualTo(0.6);
        assertThat(ownValue(nekro, "secret")).isCloseTo(1, within(EPS));
        assertThat(ownValue(nekro, "warrant")).isCloseTo(3, within(EPS));
    }

    // Two scored secrets leave room for one more secret, so the second draw is wasted.
    @Test
    void searchWarrantGainsOnlyUpToThreeSecrets() {
        nekro.setSecretScored("ans");
        nekro.setSecretScored("btgk");
        assertThat(ownValue(nekro, "warrant")).isCloseTo(2, within(EPS));
        nekro.setSecretScored("ctr");
        assertThat(ownValue(nekro, "warrant")).isZero();
    }

    // The rival view never reads Sol's secrets: swapping a hard hand for another hard hand of the same size changes
    // nothing, and the count alone drives the value.
    @Test
    void searchWarrantForARivalUsesOnlyPublicCounts() {
        sol.setSecret("ose", 901);
        double before = value(nekro, "warrant", "sol", true);
        sol.removeSecret(901);
        sol.setSecret("sb", 902);
        assertThat(value(nekro, "warrant", "sol", true)).isCloseTo(before, within(EPS));
        assertThat(before).isNegative();
    }

    @Test
    void politicalCensureIsWorthFiveWithAWeakHandAndThreeWithAGoodOne() {
        assertThat(ownValue(nekro, "censure")).isCloseTo(5, within(EPS));
        // Overrule is an S-tier card, so holding it makes the hand good.
        nekro.setActionCard("overrule");
        assertThat(ownValue(nekro, "censure")).isCloseTo(3, within(EPS));
    }

    // A rival's hand is only a count: four unknown cards look like a good hand, two look like a weak one.
    @Test
    void politicalCensureForARivalUsesTheCardCountPrior() {
        // Sol is modelled from outside (ownView false), so even an S-tier Overrule in its hand is not seen.
        sol.setActionCard("overrule");
        sol.setActionCard("sabo2");
        assertThat(value(sol, "censure", "sol", false)).isCloseTo(5, within(EPS));
        sol.setActionCard("sabo3");
        sol.setActionCard("sabo4");
        assertThat(4 * ActionCardPrior.AVERAGE_CARD).isGreaterThanOrEqualTo(4);
        assertThat(value(sol, "censure", "sol", false)).isCloseTo(3, within(EPS));
    }

    @Test
    void publicExecutionCostsTheHolderItsHandAndVotes() {
        nekro.setActionCard("sabo1");
        double loss = -ownValue(nekro, "execution");
        assertThat(loss).isGreaterThan(1);
    }

    // When the speaker is executed every later seat moves up one pick, so the voter gains a share of the speaker
    // token on top of the rival's loss.
    @Test
    void executingTheSpeakerMovesTheVoterUpThePickOrder() {
        Player hacan = seat("300000000000000003", "hacan", "yellow");
        double speaker = value(nekro, "execution", "sol", true);
        double other = value(nekro, "execution", "hacan", true);
        double share = SpeakerTokenValue.of(test.game, nekro) / 2;
        assertThat(speaker).isGreaterThan(other + share - EPS);
        assertThat(hacan.isSpeaker()).isFalse();
    }

    @Test
    void yssarilAndRalNelAreBetterExecutionTargets() {
        Player yssaril = seat("300000000000000003", ExpertElectionValues.YSSARIL, "yellow");
        Player ralnel = seat("300000000000000004", ExpertElectionValues.RAL_NEL, "green");
        Player hacan = seat("300000000000000005", "hacan", "red");
        double plain = value(nekro, "execution", hacan.getFaction(), true);
        assertThat(value(nekro, "execution", yssaril.getFaction(), true)).isGreaterThan(plain);
        assertThat(value(nekro, "execution", ralnel.getFaction(), true)).isGreaterThan(plain);
    }

    // The threat weighting alone makes the points leader the preferred target among otherwise equal rivals.
    @Test
    void thePointsLeaderIsAPreferredExecutionTarget() {
        Player hacan = seat("300000000000000003", "hacan", "yellow");
        test.game.setSpeaker(nekro);
        test.points(sol, 7);
        sol.setActionCard("sabo1");
        hacan.setActionCard("sabo2");
        assertThat(value(nekro, "execution", "sol", true)).isGreaterThan(value(nekro, "execution", "hacan", true));
    }
}
