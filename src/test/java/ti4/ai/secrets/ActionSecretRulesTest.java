package ti4.ai.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class ActionSecretRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile battlefield;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
        battlefield = test.place("46", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.game.setActiveSystem(battlefield.getPosition());
    }

    private Optional<AiDecision> tick(AiPrompt... prompts) {
        return ActionSecretRules.next(
                test.contextAt(test.game.getLastActivePlayerChange().getTime() + 1000, prompts));
    }

    private AiPrompt scoreButtons(String... secrets) {
        String[] ids = new String[secrets.length];
        for (int i = 0; i < secrets.length; i++) {
            ids[i] = "so_score_hand_" + test.nekro.getSecretsUnscored().get(secrets[i]);
        }
        return prompt("score", PromptSource.AI_THREAD, NOW, ids);
    }

    private String scoreId(String secret) {
        return "so_score_hand_" + test.nekro.getSecretsUnscored().get(secret);
    }

    // The bot counts the rounds each side has rolled in a combat; a tracker only exists once a round is rolled.
    private void roundsRolled(Player player, int rounds) {
        test.game.setStoredValue(
                "combatRoundTracker" + player.getFaction() + battlefield.getPosition() + "space",
                String.valueOf(rounds));
    }

    private void destroy(Player player, UnitType type, int count) {
        battlefield.removeUnit("space", Units.getUnitKey(type, player.getColor()), count);
    }

    // Demonstrate Your Power: three or more non-fighter ships in the active system when a space combat there ends.
    // The AI notices the combat while it runs, judges the result when it ends, and then scores the secret.
    @Test
    void scoresDemonstrateYourPowerAfterASpaceCombatWithThreeShips() {
        test.nekro.setSecret("dyp");
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 3);
        test.units(battlefield, "space", test.sol, UnitType.Destroyer, 1);
        assertThat(tick()).isEmpty();

        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("dyp")).map(AiTestGame::pressedId)).contains(scoreId("dyp"));
    }

    // Spark a Rebellion needs a win against the player with the most points; a lost combat scores nothing.
    @Test
    void scoresNothingAfterLosingTheCombat() {
        test.nekro.setSecret("sar");
        test.units(battlefield, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(battlefield, "space", test.sol, UnitType.Dreadnought, 1);
        assertThat(tick()).isEmpty();

        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.nekro, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("sar"))).isEmpty();
    }

    // An opponent that leaves before any round is rolled (its ships destroyed by space cannon, say) was never fought,
    // so nothing was won.
    @Test
    void scoresNothingWhenNoCombatRoundWasRolled() {
        test.nekro.setSecret("sar");
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(battlefield, "space", test.sol, UnitType.Destroyer, 1);
        assertThat(tick()).isEmpty();

        destroy(test.sol, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("sar"))).isEmpty();
    }

    // A combat that started and ended between two of the AI's looks is still recognised from the round trackers.
    @Test
    void recognisesACombatItOnlySawAfterItEnded() {
        test.nekro.setSecret("sar");
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 1);
        roundsRolled(test.nekro, 2);
        roundsRolled(test.sol, 2);

        assertThat(tick(scoreButtons("sar")).map(AiTestGame::pressedId)).contains(scoreId("sar"));
    }

    // Only one action-phase secret per combat: winning a space combat that meets two held secrets scores one of them.
    @Test
    void scoresAtMostOneSecretPerCombat() {
        test.nekro.setSecret("dyp");
        test.nekro.setSecret("sar");
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 3);
        test.units(battlefield, "space", test.sol, UnitType.Destroyer, 1);
        tick();
        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Destroyer, 1);
        AiPrompt both = scoreButtons("dyp", "sar");

        Optional<AiDecision> first = tick(both);
        assertThat(first).isPresent();
        String scored = AiTestGame.pressedId(first.get());
        String secret =
                scored.endsWith(String.valueOf(test.nekro.getSecretsUnscored().get("dyp"))) ? "dyp" : "sar";
        test.game.scoreSecretObjective(
                test.nekro.getUserID(), test.nekro.getSecretsUnscored().get(secret));

        assertThat(tick(both)).isEmpty();
    }

    // Destroy Their Greatest Ship needs the flagship destroyed: one that retreats out of the system still exists.
    @Test
    void doesNotCountARetreatingFlagshipAsDestroyed() {
        test.nekro.setSecret("dtgs");
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(battlefield, "space", test.sol, UnitType.Flagship, 1);
        tick();
        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Flagship, 1);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.sol, UnitType.Flagship, 1);

        assertThat(tick(scoreButtons("dtgs"))).isEmpty();
    }

    @Test
    void scoresDestroyTheirGreatestShipWhenTheFlagshipIsGone() {
        test.nekro.setSecret("dtgs");
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(battlefield, "space", test.sol, UnitType.Flagship, 1);
        tick();
        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Flagship, 1);

        assertThat(tick(scoreButtons("dtgs")).map(AiTestGame::pressedId)).contains(scoreId("dtgs"));
    }
}
