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
import ti4.image.PositionMapper;
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

    private void enemyBeyondTheBattlefield() {
        String position = PositionMapper.getAdjacentTilePositions(battlefield.getPosition()).stream()
                .filter(candidate -> !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate))
                .findFirst()
                .orElseThrow();
        Tile beyond = test.place("47", position);
        test.units(beyond, "space", test.sol, UnitType.Destroyer, 1);
    }

    // Only one action secret is scored per combat, so when a combat meets two, the rarer one goes first: Darken the
    // Skies (scored by 45% of the copies kept) over Unveil Flagship (65%). The battlefield is Sol's home system, and
    // the seat wins there with its flagship.
    @Test
    void scoresTheRarerOfTwoSecretsACombatMeets() {
        battlefield = test.place("01", battlefield.getPosition());
        test.nekro.setSecret("uf");
        test.nekro.setSecret("dts");
        test.units(battlefield, "space", test.nekro, UnitType.Flagship, 1);
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(battlefield, "space", test.sol, UnitType.Destroyer, 1);
        tick();
        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("uf", "dts")).map(AiTestGame::pressedId)).contains(scoreId("dts"));
    }

    // The same goes for the two a flagship and two dreadnoughts meet at once: Unveil Flagship (65%) before
    // Demonstrate Your Power (70%).
    @Test
    void scoresUnveilFlagshipBeforeDemonstrateYourPower() {
        test.nekro.setSecret("dyp");
        test.nekro.setSecret("uf");
        test.units(battlefield, "space", test.nekro, UnitType.Flagship, 1);
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(battlefield, "space", test.sol, UnitType.Destroyer, 1);
        tick();
        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("uf", "dyp")).map(AiTestGame::pressedId)).contains(scoreId("uf"));
    }

    // Patience is about not going out of the way to meet a condition. Here the seat is on 4 against 3 with many rounds
    // left, three dreadnoughts and an enemy a move away, a position in which it would not hurry to fight for
    // Demonstrate Your Power (see ActionSecretTimingTest). The combat has already met it, so the point is scored.
    @Test
    void scoresASecretACombatHasMetEvenWhenItWouldNotHaveHurriedToMeetIt() {
        test.nekro.setSecret("dyp");
        test.points(test.nekro, 4);
        test.points(test.sol, 3);
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 3);
        enemyBeyondTheBattlefield();
        test.units(battlefield, "space", test.sol, UnitType.Destroyer, 1);
        tick();
        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("dyp")).map(AiTestGame::pressedId)).contains(scoreId("dyp"));
    }
}
