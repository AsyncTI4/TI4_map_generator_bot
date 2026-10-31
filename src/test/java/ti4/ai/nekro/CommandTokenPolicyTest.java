package ti4.ai.nekro;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The status phase lets every player gain two command tokens; some abilities and technologies add one.
class CommandTokenPolicyTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        rivalTactics(0);
    }

    private void rivalTactics(int tactics) {
        for (Player other : test.game.getRealPlayers()) {
            if (other != test.nekro) other.setTacticalCC(tactics);
        }
    }

    @Test
    void gainsTwoTokensByDefault() {
        assertThat(CommandTokenPolicy.statusPhaseGain(test.nekro)).isEqualTo(2);
    }

    @Test
    void solsVersatileGainsAThirdToken() {
        assertThat(test.sol.hasAbility("versatile")).isTrue();

        assertThat(CommandTokenPolicy.statusPhaseGain(test.sol)).isEqualTo(3);
    }

    @Test
    void hyperMetabolismGainsAThirdToken() {
        test.nekro.addTech("hm");

        assertThat(CommandTokenPolicy.statusPhaseGain(test.nekro)).isEqualTo(3);
    }

    @Test
    void versatileAndHyperMetabolismStack() {
        test.sol.addTech("hm");

        assertThat(CommandTokenPolicy.statusPhaseGain(test.sol)).isEqualTo(4);
    }

    // With few tokens and no rival able to out-stall it, one tactic token comes first, then two strategy tokens to
    // follow next round's Technology or Imperial, then a second tactic token. A held Strategize adds a third strategy
    // token after that (see StrategizeTokensTest).
    @Test
    void growsATacticThenTwoStrategyTokensThenTactics() {
        test.nekro.setTacticalCC(0);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(0);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");

        test.nekro.setTacticalCC(1);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_strategy_cc");

        test.nekro.setStrategicCC(1);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_strategy_cc");

        test.nekro.setStrategicCC(2);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");
    }

    // Players keep the fleet pool at their largest stack early on rather than at a standing three, and spend the rest
    // on one tactic and two strategy tokens, the second tactic token coming after them: 1/2/2, 1/1/2 and 1/2/0.
    @Test
    void statusPhaseKeepsTheFleetAtTheLargestStackAndFollowsNextRound() {
        test.game.setPhaseOfGame("statusHomework");
        test.game.setRound(1);
        test.units(test.nekroHome(), "space", test.nekro, UnitType.Carrier, 2);

        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 5)).isEqualTo(split(1, 2, 2));
        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 3)).isEqualTo(split(1, 2, 0));
    }

    @Test
    void statusPhaseWithASingleShipStackKeepsOneFleetToken() {
        test.game.setPhaseOfGame("statusHomework");
        test.game.setRound(1);
        test.units(test.nekroHome(), "space", test.nekro, UnitType.Carrier, 1);

        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 4)).isEqualTo(split(1, 1, 2));
        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 5)).isEqualTo(split(2, 1, 2));
    }

    // Past two tactic and two strategy tokens, a second fleet token comes before more tactics, leaving room to build or
    // gather a second ship.
    @Test
    void growsTheFleetToTwoOnceTwoTacticAndTwoStrategyTokensAreKept() {
        test.game.setPhaseOfGame("statusHomework");
        test.game.setRound(1);
        test.units(test.nekroHome(), "space", test.nekro, UnitType.Carrier, 1);

        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 6)).isEqualTo(split(2, 2, 2));
    }

    // A rival holding more tactic tokens and other stalls than this seat could make it pass first, so it keeps two
    // tactic tokens for every strategy token: 2/1/0, 3/1/1, then 4/1/1 and 4/1/2.
    @Test
    void keepsTwoTacticsPerStrategyTokenWhenARivalCouldOutStallIt() {
        test.game.setPhaseOfGame("statusHomework");
        test.game.setRound(1);
        test.units(test.nekroHome(), "space", test.nekro, UnitType.Carrier, 1);
        rivalTactics(2);

        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 3)).isEqualTo(split(2, 1, 0));
        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 5)).isEqualTo(split(3, 1, 1));
        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 6)).isEqualTo(split(4, 1, 1));
        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 7)).isEqualTo(split(4, 1, 2));
        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 8)).isEqualTo(split(4, 2, 2));
    }

    // One rival tactic token is no threat: a seat keeping one of its own can match it.
    @Test
    void aSingleRivalTacticTokenIsNoStallThreat() {
        test.game.setPhaseOfGame("statusHomework");
        test.game.setRound(1);
        test.units(test.nekroHome(), "space", test.nekro, UnitType.Carrier, 1);
        rivalTactics(1);

        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 4)).isEqualTo(split(1, 1, 2));
    }

    // Round two rarely needs a stack bigger than three, so ships that could join a stack of three grow the fleet pool
    // only from the round two status phase on.
    @Test
    void growsTheFleetPastThreeOnlyAfterRoundTwo() {
        test.game.setPhaseOfGame("statusHomework");
        test.units(test.nekroHome(), "space", test.nekro, UnitType.Carrier, 3);
        Tile away = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(away, "space", test.nekro, UnitType.Destroyer, 1);

        test.game.setRound(1);
        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 9)).containsEntry(CommandTokenPolicy.Pool.FLEET, 3);

        test.game.setRound(2);
        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 9)).containsEntry(CommandTokenPolicy.Pool.FLEET, 4);
    }

    private static java.util.Map<CommandTokenPolicy.Pool, Integer> split(int tactic, int fleet, int strategy) {
        return java.util.Map.of(
                CommandTokenPolicy.Pool.TACTIC, tactic,
                CommandTokenPolicy.Pool.FLEET, fleet,
                CommandTokenPolicy.Pool.STRATEGY, strategy);
    }

    // Raise a Fleet needs five non-fighter ships in one system, which fleet supply only allows with five fleet tokens.
    // Those tokens are worth more than strategy tokens once a stack can grow into them, but not before.
    @Test
    void growsTheFleetPoolForRaiseAFleetOnceAStackCanReachIt() {
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        test.nekro.setTacticalCC(2);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(1);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_strategy_cc");

        test.units(home, "space", test.nekro, UnitType.Destroyer, 2);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_fleet_cc");
    }

    // During the action phase a fleet token for a public objective comes before the strategy and tactic tokens the
    // coming turns would use.
    @Test
    void aFleetTokenForAnObjectiveComesBeforeTheTokensTheTurnsWouldUseDuringTheActionPhase() {
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        test.game.setPhaseOfGame("action");
        test.nekro.setTacticalCC(1);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(0);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 3);

        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_fleet_cc");
    }

    // Scoring happens before the status phase hands out tokens, so the fifth fleet token has to be in place during the
    // round the stack is built. One tactic token is enough to produce into the stack; the fleet comes before a second.
    @Test
    void growsTheFleetForRaiseAFleetOnceOneTacticTokenIsKept() {
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        test.nekro.setTacticalCC(0);
        test.nekro.setFleetCC(3);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 3);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");

        test.nekro.setTacticalCC(1);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_fleet_cc");

        test.nekro.setFleetCC(5);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");
    }

    // A status phase scores one public objective. With Engineer a Marvel already met, that slot is taken next round
    // whatever happens, so Raise a Fleet would add nothing then and the second tactic token comes first again.
    @Test
    void keepsTheSecondTacticTokenWhenAnotherPublicAlreadyTakesTheSlot() {
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        test.game.getRevealedPublicObjectives().put("engineer_marvel", 1);
        test.nekro.setTacticalCC(1);
        test.nekro.setFleetCC(3);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Flagship, 1);

        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");
    }

    // Fleet beyond three only pays off when there are ships to use it: a stack of three with more ships elsewhere
    // that could join it. Spare tokens then go there before piling up as tactics.
    @Test
    void growsTheFleetForABiggerStackOnlyWhenThereAreShipsToFillIt() {
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(2);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Carrier, 3);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");

        Tile away = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.units(away, "space", test.nekro, UnitType.Destroyer, 1);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_fleet_cc");
    }

    // Fleet Regulations caps the fleet pool at four, even with a five-ship stack left over from before the law, and
    // makes Raise a Fleet (five ships in one system) impossible, so no token goes toward it.
    @Test
    void keepsTheFleetPoolWithinFleetRegulations() {
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        test.game.addLaw("regulations", null);
        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(4);
        test.nekro.setStrategicCC(2);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 5);

        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 9)).containsEntry(CommandTokenPolicy.Pool.FLEET, 4);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isNotEqualTo("increase_fleet_cc");
    }

    // Late in the game, with tactics piling up unused, a third strategy token is kept for follows, never a fourth.
    @Test
    void keepsAtMostThreeStrategyTokens() {
        test.nekro.setTacticalCC(5);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(2);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_strategy_cc");

        test.nekro.setStrategicCC(3);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");
    }

    // Redistribution moves a token out of whichever pool holds more than the priorities give it, least important pool
    // first. Here the fifth fleet token is no longer needed once Raise a Fleet is not on the table.
    @Test
    void redistributesSurplusTokensTowardTheIdealPools() {
        test.units(test.nekroHome(), "space", test.nekro, UnitType.Carrier, 3);
        test.nekro.setTacticalCC(1);
        test.nekro.setFleetCC(5);
        test.nekro.setStrategicCC(2);
        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 8))
                .containsEntry(CommandTokenPolicy.Pool.TACTIC, 3)
                .containsEntry(CommandTokenPolicy.Pool.FLEET, 3)
                .containsEntry(CommandTokenPolicy.Pool.STRATEGY, 2);
        assertThat(CommandTokenPolicy.poolToRedistributeFrom(test.game, test.nekro))
                .contains("decrease_fleet_cc");

        test.nekro.setTacticalCC(3);
        test.nekro.setFleetCC(3);
        assertThat(CommandTokenPolicy.poolToRedistributeFrom(test.game, test.nekro))
                .isEmpty();
    }

    // Each move takes a token from a pool above its ideal and gives it back to one below, so redistribution always
    // ends: replaying the moves from any starting pools reaches the ideal within a few presses.
    @Test
    void redistributionAlwaysSettles() {
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        Tile home = test.nekroHome();
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 3);
        for (int tactic = 0; tactic <= 6; tactic++) {
            for (int fleet = 3; fleet <= 6; fleet++) {
                for (int strategy = 0; strategy <= 4; strategy++) {
                    test.nekro.setTacticalCC(tactic);
                    test.nekro.setFleetCC(fleet);
                    test.nekro.setStrategicCC(strategy);
                    int moves = 0;
                    java.util.Optional<String> from;
                    while ((from = CommandTokenPolicy.poolToRedistributeFrom(test.game, test.nekro)).isPresent()) {
                        assertThat(moves++).isLessThan(20);
                        adjust(from.get(), -1);
                        adjust(CommandTokenPolicy.poolToGrow(test.game, test.nekro), 1);
                    }
                    assertThat(test.nekro.getFleetCC()).isGreaterThanOrEqualTo(3);
                }
            }
        }
    }

    private void adjust(String handler, int delta) {
        if (handler.contains("tactic")) test.nekro.setTacticalCC(test.nekro.getTacticalCC() + delta);
        if (handler.contains("fleet")) test.nekro.setFleetCC(test.nekro.getFleetCC() + delta);
        if (handler.contains("strategy")) test.nekro.setStrategicCC(test.nekro.getStrategicCC() + delta);
    }

    // Once the cards are picked, the strategy pool only needs one token per secondary the AI means to follow: here
    // Sol's Imperial, for a secret objective. The rest of the round's tokens are worth more as tactic tokens.
    @Test
    void midRoundStrategyPoolMatchesTheSecondariesItMeansToFollow() {
        tokens(3, 3, 3);
        test.units(test.nekroHome(), "space", test.nekro, UnitType.Carrier, 3);
        test.aiIsActive("action");
        test.sol.addSC(IMPERIAL);

        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 9))
                .containsEntry(CommandTokenPolicy.Pool.STRATEGY, 1)
                .containsEntry(CommandTokenPolicy.Pool.FLEET, 3)
                .containsEntry(CommandTokenPolicy.Pool.TACTIC, 5);
        assertThat(CommandTokenPolicy.midRoundSurplus(test.game, test.nekro)).contains("decrease_strategy_cc");
    }

    // With nothing to follow, the strategy pool is empty and every spare token goes to tactics.
    @Test
    void midRoundSurplusGoesToTactics() {
        tokens(3, 3, 3);
        test.units(test.nekroHome(), "space", test.nekro, UnitType.Carrier, 3);
        test.aiIsActive("action");

        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 9))
                .containsEntry(CommandTokenPolicy.Pool.STRATEGY, 0)
                .containsEntry(CommandTokenPolicy.Pool.TACTIC, 6);
        assertThat(CommandTokenPolicy.poolToGrow(test.game, test.nekro)).isEqualTo("increase_tactic_cc");
    }

    // Before the next round's cards are known, the status phase keeps its standing split.
    @Test
    void statusPhaseKeepsItsStandingSplit() {
        tokens(3, 3, 3);
        test.game.setPhaseOfGame("statusHomework");
        test.sol.addSC(IMPERIAL);

        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 5))
                .containsEntry(CommandTokenPolicy.Pool.STRATEGY, 2);
    }

    // Mid-round only the tactic and strategy pools move, so the split they are measured against leaves the fleet
    // pool where it is, however many fleet tokens the standing split would like.
    @Test
    void midRoundSplitKeepsTheFleetPoolWhereItIs() {
        test.game.getRevealedPublicObjectives().put("raise_fleet", 1);
        test.aiIsActive("action");
        tokens(3, 3, 2);
        test.units(test.nekroHome(), "space", test.nekro, UnitType.Dreadnought, 4);

        assertThat(CommandTokenPolicy.ideal(test.game, test.nekro, 8)).containsEntry(CommandTokenPolicy.Pool.FLEET, 5);
        assertThat(CommandTokenPolicy.idealKeepingFleet(test.game, test.nekro, 8))
                .containsEntry(CommandTokenPolicy.Pool.FLEET, 3);
        assertThat(CommandTokenPolicy.midRoundSurplus(test.game, test.nekro)).contains("decrease_strategy_cc");
        test.nekro.setStrategicCC(0);
        test.nekro.setTacticalCC(5);
        assertThat(CommandTokenPolicy.midRoundSurplus(test.game, test.nekro)).isEmpty();
    }

    private static final int IMPERIAL = 8;

    private void tokens(int tactic, int fleet, int strategy) {
        test.nekro.setTacticalCC(tactic);
        test.nekro.setFleetCC(fleet);
        test.nekro.setStrategicCC(strategy);
    }
}
