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
import ti4.model.MapPositionMapper;
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

    private void neutralBattlefield(String tileId) {
        battlefield = test.place(tileId, battlefield.getPosition());
    }

    // Combat against neutral units is a combat (neutral-units.md II, VIII), so a win over them scores Brave the Void.
    @Test
    void scoresBraveTheVoidAfterBeatingNeutralUnitsInAnAnomaly() {
        neutralBattlefield("42");
        Player neutral = test.game.getNeutral();
        test.nekro.setSecret("btv");
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(battlefield, "space", neutral, UnitType.Destroyer, 1);
        assertThat(tick()).isEmpty();

        roundsRolled(test.nekro, 1);
        roundsRolled(neutral, 1);
        destroy(neutral, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("btv")).map(AiTestGame::pressedId)).contains(scoreId("btv"));
    }

    // Neutral units are not a player, and with every score at zero they would otherwise look like the leader.
    @Test
    void doesNotScoreSparkARebellionAgainstNeutralUnits() {
        Player neutral = test.game.getNeutral();
        test.nekro.setSecret("sar");
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(battlefield, "space", neutral, UnitType.Destroyer, 1);
        assertThat(tick()).isEmpty();

        roundsRolled(test.nekro, 1);
        roundsRolled(neutral, 1);
        destroy(neutral, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("sar"))).isEmpty();
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
        String position = MapPositionMapper.getAdjacentTilePositions(battlefield.getPosition()).stream()
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

    // Fight with Precision: the barrage destroys the last of an opponent's fighters before any combat round is rolled.
    @Test
    void scoresFightWithPrecisionWhenTheBarrageClearsTheFightersBesideAnotherShip() {
        test.nekro.setSecret("fwp");
        test.units(battlefield, "space", test.nekro, UnitType.Destroyer, 2);
        test.units(battlefield, "space", test.sol, UnitType.Fighter, 2);
        test.units(battlefield, "space", test.sol, UnitType.Cruiser, 1);
        tick();

        destroy(test.sol, UnitType.Fighter, 2);
        tick();
        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Cruiser, 1);

        assertThat(tick(scoreButtons("fwp")).map(AiTestGame::pressedId)).contains(scoreId("fwp"));
    }

    // When the fighters were all the opponent had, the barrage ends the combat before the AI can look at the cleared
    // fighters, yet the point is still earned.
    @Test
    void scoresFightWithPrecisionWhenTheFightersWereAllTheOpponentHad() {
        test.nekro.setSecret("fwp");
        test.units(battlefield, "space", test.nekro, UnitType.Destroyer, 2);
        test.units(battlefield, "space", test.sol, UnitType.Fighter, 2);
        tick();

        destroy(test.sol, UnitType.Fighter, 2);

        assertThat(tick(scoreButtons("fwp")).map(AiTestGame::pressedId)).contains(scoreId("fwp"));
    }

    @Test
    void scoresNothingWhenSomeFightersSurviveTheBarrage() {
        test.nekro.setSecret("fwp");
        test.units(battlefield, "space", test.nekro, UnitType.Destroyer, 2);
        test.units(battlefield, "space", test.sol, UnitType.Fighter, 3);
        test.units(battlefield, "space", test.sol, UnitType.Cruiser, 1);
        tick();

        destroy(test.sol, UnitType.Fighter, 2);
        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Cruiser, 1);
        destroy(test.sol, UnitType.Fighter, 1);

        assertThat(tick(scoreButtons("fwp"))).isEmpty();
    }

    private void sharedHomeBattlefield() {
        battlefield = test.place("01", battlefield.getPosition());
        test.nekro.setSecret("dts");
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(battlefield, "space", test.sol, UnitType.Destroyer, 1);
        tick();
    }

    // Darken the Skies: a win in another player's home system.
    @Test
    void scoresDarkenTheSkiesAfterWinningAtAnotherPlayersHome() {
        sharedHomeBattlefield();
        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("dts")).map(AiTestGame::pressedId)).contains(scoreId("dts"));
    }

    @Test
    void scoresNothingForDarkenTheSkiesWhenTheHomeFightIsLost() {
        sharedHomeBattlefield();
        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.nekro, UnitType.Dreadnought, 1);

        assertThat(tick(scoreButtons("dts"))).isEmpty();
    }

    // A home fleet that leaves or is shot down by space cannon before any round is rolled was never fought.
    @Test
    void scoresNothingForDarkenTheSkiesWhenNoCombatWasRolled() {
        sharedHomeBattlefield();
        destroy(test.sol, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("dts"))).isEmpty();
    }

    @Test
    void scoresNothingForDarkenTheSkiesWhenWinningAtOwnHome() {
        battlefield = test.game.getTileByPosition(AiTestGame.HOME);
        test.game.setActiveSystem(battlefield.getPosition());
        test.nekro.setSecret("dts");
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(battlefield, "space", test.sol, UnitType.Destroyer, 1);
        tick();
        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("dts"))).isEmpty();
    }

    private void loneSolDestroyerWithAThirdPartyPds() {
        test.nekro.setSecret("ttfd");
        test.units(battlefield, "space", test.sol, UnitType.Destroyer, 1);
        Player hacan = test.addSeat("7100000555555555", "hacan", "green");
        test.units(battlefield, "space", hacan, UnitType.Pds, 1);
    }

    private void fireSpaceCannon() {
        ActionSecretRules.watchSpaceCannon(
                test.contextAt(test.game.getLastActivePlayerChange().getTime() + 1000), battlefield.getPosition());
    }

    private boolean watching() {
        return test.memory.has("cannonWatch|" + test.game.getRound());
    }

    // Turn Their Fleets to Dust: when the holder is the active player a third party's cannon can only hit the holder
    // (LRR 77.5a), so the watch is recorded and the last ship dying before any combat round scores.
    @Test
    void scoresDustWhenTheActiveHolderKillsTheLastShipDespiteAThirdPartyPds() {
        loneSolDestroyerWithAThirdPartyPds();
        fireSpaceCannon();

        assertThat(watching()).isTrue();
        destroy(test.sol, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("ttfd")).map(AiTestGame::pressedId)).contains(scoreId("ttfd"));
    }

    // A neutral destroyer is another player's ship for the secret's purposes, and the neutral player is not a real
    // seat, so the watch must look past the real players to find it.
    @Test
    void scoresDustWhenTheActiveHolderKillsALoneNeutralDestroyer() {
        test.nekro.setSecret("ttfd");
        Player neutral = test.game.getNeutral();
        test.units(battlefield, "space", neutral, UnitType.Destroyer, 1);
        fireSpaceCannon();

        assertThat(watching()).isTrue();
        destroy(neutral, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("ttfd")).map(AiTestGame::pressedId)).contains(scoreId("ttfd"));
    }

    @Test
    void scoresNothingWhileTheNeutralDestroyerSurvivesTheCannon() {
        test.nekro.setSecret("ttfd");
        Player neutral = test.game.getNeutral();
        test.units(battlefield, "space", neutral, UnitType.Destroyer, 1);
        fireSpaceCannon();

        assertThat(tick(scoreButtons("ttfd"))).isEmpty();
    }

    // Once a combat round was rolled the ship did not die to the cannon.
    @Test
    void scoresNothingForDustOnceACombatRoundWasRolled() {
        loneSolDestroyerWithAThirdPartyPds();
        fireSpaceCannon();

        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Destroyer, 1);

        assertThat(tick(scoreButtons("ttfd"))).isEmpty();
    }

    // When another player is active, every cannon in range hits that player's fleet, so the holder cannot tell whose
    // hit destroyed the last ship and does not watch.
    @Test
    void doesNotWatchWhenAThirdPartyAlsoFiresAtAnotherPlayersActivation() {
        loneSolDestroyerWithAThirdPartyPds();
        test.isActive(test.sol, "action");

        fireSpaceCannon();

        assertThat(watching()).isFalse();
    }

    // A flagship killed by Direct Hit outside a fight still counts, and the holder may have no ship there to watch it:
    // the cannon fire itself opens the watch.
    @Test
    void scoresDestroyTheirGreatestShipWhenAFlagshipDiesToSpaceCannonWhereTheSeatHasNoShips() {
        test.nekro.setSecret("dtgs");
        test.units(battlefield, "space", test.sol, UnitType.Flagship, 1);
        fireSpaceCannon();

        destroy(test.sol, UnitType.Flagship, 1);

        assertThat(tick(scoreButtons("dtgs")).map(AiTestGame::pressedId)).contains(scoreId("dtgs"));
    }

    @Test
    void scoresNothingWhenTheFlagshipSurvivesTheSpaceCannon() {
        test.nekro.setSecret("dtgs");
        test.units(battlefield, "space", test.sol, UnitType.Flagship, 1);
        fireSpaceCannon();
        battlefield.addUnitDamage("space", Units.getUnitKey(UnitType.Flagship, test.sol.getColor()), 1);

        assertThat(tick(scoreButtons("dtgs"))).isEmpty();
    }

    @Test
    void scoresNothingForAFlagshipThatLeftTheSystemAfterTheSpaceCannon() {
        test.nekro.setSecret("dtgs");
        test.units(battlefield, "space", test.sol, UnitType.Flagship, 1);
        fireSpaceCannon();
        destroy(test.sol, UnitType.Flagship, 1);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.sol, UnitType.Flagship, 1);

        assertThat(tick(scoreButtons("dtgs"))).isEmpty();
    }

    @Test
    void doesNotWatchSpaceCannonFireWithoutTheSecret() {
        test.units(battlefield, "space", test.sol, UnitType.Flagship, 1);
        fireSpaceCannon();

        assertThat(test.memory.has("heavyCannonWatch|" + test.game.getRound())).isFalse();
    }

    // The watch starts at the first tick that sees both sides in the system, so a flagship killed afterwards is queued
    // for scoring even when the seat only looks again after the fight.
    @Test
    void queuesDestroyTheirGreatestShipOnceTheFlagshipDiesAfterTheWatchStarted() {
        test.nekro.setSecret("dtgs");
        test.units(battlefield, "space", test.nekro, UnitType.Dreadnought, 2);
        test.units(battlefield, "space", test.sol, UnitType.Flagship, 1);
        ActionSecretRules.observe(
                test.contextAt(test.game.getLastActivePlayerChange().getTime() + 1000));
        roundsRolled(test.nekro, 1);
        roundsRolled(test.sol, 1);
        destroy(test.sol, UnitType.Flagship, 1);

        ActionSecretRules.observe(
                test.contextAt(test.game.getLastActivePlayerChange().getTime() + 2000));

        assertThat(test.memory
                        .get("actionSecretPending|" + test.game.getRound())
                        .orElse(""))
                .startsWith("dtgs|");
    }

    private void looksAtTheBoard(long millisAfterTurnStart) {
        ActionSecretRules.observe(
                test.contextAt(test.game.getLastActivePlayerChange().getTime() + millisAfterTurnStart));
    }

    private String pendingSecrets() {
        return test.memory.get("actionSecretPending|" + test.game.getRound()).orElse("");
    }

    // Losing a planet in another player's home system scores Become a Martyr, however the planet is lost.
    @Test
    void queuesBecomeAMartyrOnceAForeignHomePlanetIsLost() {
        test.nekro.setSecret("bam");
        test.solHomeBeside(battlefield.getPosition());
        test.nekro.addPlanet("jord");
        looksAtTheBoard(1000);
        assertThat(pendingSecrets()).isEmpty();

        test.nekro.removePlanet("jord");
        looksAtTheBoard(2000);

        assertThat(pendingSecrets()).startsWith("bam|");
    }

    @Test
    void queuesBecomeAMartyrWhenTheForeignHomePlanetIsLostOnAnotherPlayersTurn() {
        test.nekro.setSecret("bam");
        test.solHomeBeside(battlefield.getPosition());
        test.nekro.addPlanet("jord");
        test.isActive(test.sol, "action");
        looksAtTheBoard(1000);

        test.nekro.removePlanet("jord");
        looksAtTheBoard(2000);

        assertThat(pendingSecrets()).startsWith("bam|");
    }
}
