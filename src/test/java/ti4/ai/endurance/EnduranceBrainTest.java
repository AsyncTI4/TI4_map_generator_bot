package ti4.ai.endurance;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.CardPlan;
import ti4.ai.strategy.CardUse;
import ti4.ai.strategy.ResearchPolicy;
import ti4.ai.strategy.StrategyCardRanking;
import ti4.ai.tactical.StallPlans;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tech.TechRules;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// With Prove Endurance in hand a seat that can outlast the others takes its last actions instead of passing, values
// stalling technologies and the tokens of Leadership a little higher, and refills Sling Relay with Bio-Stims.
class EnduranceBrainTest extends BaseTi4Test {

    private final NekroBrain brain = new NekroBrain();
    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
        test.nekro.setSecret(Endurance.SECRET);
        test.nekro.setTacticalCC(3);
        test.nekro.setStrategicCC(2);
        test.sol.setTacticalCC(1);
    }

    private AiPrompt turn() {
        return prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_passForRound");
    }

    private void withoutTheSecret() {
        test.nekro.removeSecret(test.nekro.getSecrets().get(Endurance.SECRET));
    }

    @Test
    void takesAnActionInsteadOfPassingWhileItCanOutlastTheOthers() {
        withoutTheSecret();
        assertThat(pressedId(brain.decide(test.context(turn())))).isEqualTo("FFCC_nekro_passForRound");

        test.nekro.setSecret(Endurance.SECRET);
        assertThat(pressedId(brain.decide(test.context(turn())))).isEqualTo("FFCC_nekro_tacticalAction");
    }

    @Test
    void passesWhenAnotherPlayerCannotBeOutlasted() {
        test.nekro.setTacticalCC(1);
        test.sol.setTacticalCC(6);

        assertThat(pressedId(brain.decide(test.context(turn())))).isEqualTo("FFCC_nekro_passForRound");
    }

    @Test
    void stopsStallingOnceEveryoneElseHasPassed() {
        test.sol.setPassed(true);

        assertThat(pressedId(brain.decide(test.context(turn())))).isEqualTo("FFCC_nekro_passForRound");
    }

    @Test
    void stallsInItsHomeSystemAndNeverInOneThatHoldsItsOwnToken() {
        TacticalPlan plan = StallPlans.enduranceStall(test.game, test.nekro).orElseThrow();

        assertThat(plan.target()).isEqualTo(AiTestGame.HOME);
        assertThat(plan.moves()).isEmpty();

        test.game.getTileByPosition(AiTestGame.HOME).addCC(Mapper.getCCID(test.nekro.getColor()));
        assertThat(StallPlans.enduranceStall(test.game, test.nekro).map(TacticalPlan::target))
                .isNotEqualTo(Optional.of(AiTestGame.HOME));
    }

    // The system picker only offers systems near the seat's units, so a stall never targets an empty system such as
    // Mecatol Rex: with its home locked and nothing else held, there is no stall.
    @Test
    void neverStallsInASystemItHasNothingIn() {
        test.game.getTileByPosition(AiTestGame.HOME).addCC(Mapper.getCCID(test.nekro.getColor()));

        StallPlans.enduranceStall(test.game, test.nekro)
                .map(plan -> test.game.getTileByPosition(plan.target()))
                .ifPresent(tile -> assertThat(tile.getPlanetUnitHolders().stream()
                                        .anyMatch(planet ->
                                                test.nekro.getPlanets().contains(planet.getName()))
                                || ti4.ai.eval.BoardView.hasOwnShips(test.nekro, tile))
                        .isTrue());
    }

    // An owned planet with no unit on it is not a unit tile, so the system picker can only reach it from another unit
    // tile.
    // With the home locked and nothing else holding units there is no way to pick it, and stalling there would stick.
    @Test
    void neverStallsOnAnEmptyPlanetWhenNoUnlockedSystemHoldsUnits() {
        String neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("01", neighbour);
        test.nekro.addPlanet("jord");

        assertThat(StallPlans.enduranceStall(test.game, test.nekro).map(TacticalPlan::target))
                .isEqualTo(Optional.of(AiTestGame.HOME));

        test.game.getTileByPosition(AiTestGame.HOME).addCC(Mapper.getCCID(test.nekro.getColor()));

        assertThat(StallPlans.enduranceStall(test.game, test.nekro)).isEmpty();
    }

    @Test
    void hasNoStallWithoutATokenToSpendOrAWinnableRace() {
        test.nekro.setTacticalCC(0);
        assertThat(StallPlans.enduranceStall(test.game, test.nekro)).isEmpty();

        test.nekro.setTacticalCC(1);
        test.sol.setTacticalCC(6);
        assertThat(StallPlans.enduranceStall(test.game, test.nekro)).isEmpty();
    }

    @Test
    void valuesSlingRelayAndBioStimsMoreWhileHoldingTheSecret() {
        double sling = ResearchPolicy.value(test.game, test.nekro, "sr");
        double stims = ResearchPolicy.value(test.game, test.nekro, "bs");
        double drive = ResearchPolicy.value(test.game, test.nekro, "gd");
        withoutTheSecret();

        assertThat(sling).isGreaterThan(ResearchPolicy.value(test.game, test.nekro, "sr"));
        assertThat(stims).isGreaterThan(ResearchPolicy.value(test.game, test.nekro, "bs"));
        assertThat(drive).isEqualTo(ResearchPolicy.value(test.game, test.nekro, "gd"));
    }

    @Test
    void valuesTheTokensOfLeadershipWhileHoldingTheSecret() {
        test.game.setPhaseOfGame("strategy");
        test.game.setLastModifiedDate(1);
        double with = leadershipEndurance();
        withoutTheSecret();
        test.game.setLastModifiedDate(test.game.getLastModifiedDate() + 1);

        assertThat(with).isPositive();
        assertThat(leadershipEndurance()).isZero();
    }

    private double leadershipEndurance() {
        CardPlan plan = StrategyCardRanking.pickPlan(test.game, test.nekro, 1);
        return plan.benefit(CardUse.LEADERSHIP_ENDURANCE)
                .map(CardPlan.Benefit::value)
                .orElse(0.0);
    }

    @Test
    void readiesSlingRelayWithBioStimsWhileItCanWinTheRace() {
        test.nekro.addTech("sr");
        test.nekro.addTech("bs");
        test.nekro.exhaustTech("sr");
        AiPrompt end = prompt("end", PromptSource.PUBLIC, NOW, "FFCC_nekro_endOfTurnAbilities");
        AiPrompt abilities =
                prompt("abilities", PromptSource.PUBLIC, NOW, "FFCC_nekro_turnEnd", "FFCC_nekro_exhaustTech_bs");
        AiPrompt ready =
                prompt("ready", PromptSource.PUBLIC, NOW, "biostimsReady_planet_mordaiii", "biostimsReady_tech_sr");

        assertThat(pressedId(
                        TechRules.endOfTurn(test.context(end), List.of(end)).orElseThrow()))
                .isEqualTo("FFCC_nekro_endOfTurnAbilities");
        assertThat(pressedId(TechRules.endOfTurn(test.context(abilities), List.of(abilities))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_exhaustTech_bs");
        assertThat(pressedId(
                        TechRules.endOfTurn(test.context(ready), List.of(ready)).orElseThrow()))
                .isEqualTo("biostimsReady_tech_sr");
    }

    @Test
    void leavesBioStimsAloneWhenSlingRelayIsReadyOrTheRaceIsLost() {
        test.nekro.addTech("sr");
        test.nekro.addTech("bs");
        AiPrompt end = prompt("end", PromptSource.PUBLIC, NOW, "FFCC_nekro_endOfTurnAbilities");
        assertThat(TechRules.endOfTurn(test.context(end), List.of(end))).isEmpty();

        test.nekro.exhaustTech("sr");
        test.nekro.setTacticalCC(0);
        test.sol.setTacticalCC(6);
        assertThat(TechRules.endOfTurn(test.context(end), List.of(end))).isEmpty();
    }
}
