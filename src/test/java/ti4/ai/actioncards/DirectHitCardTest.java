package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.actioncards.DirectHitCards.Hurt;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.CombatMoment;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class DirectHitCardTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final int HAND_INDEX = 412;
    private static final String SPACE = "space";
    private static final String TITLE = "Direct Hit";

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;
    private String position;
    private Tile tile;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        position = CardTable.SOL_SPOT;
        tile = table.system("26", position);
        table.game.setActiveSystem(position);
        messages = Mockito.mockStatic(GameMessageManager.class);
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1),
                        GameMessageType.ACTION_CARD,
                        new LinkedHashSet<>(List.of("nekro", "sol")),
                        0L)));
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private CombatMoment moment(int round) {
        return new CombatMoment(position, SPACE, false, round);
    }

    private AiPrompt hand() {
        return prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX, "cardsInfo");
    }

    private Optional<AiDecision> atRoundStart(int round) {
        return CombatCardRules.atRoundStart(table.test.context(hand()), moment(round));
    }

    private void holdDirectHit() {
        table.nekro.setActionCard("dh1", HAND_INDEX);
    }

    private UnitKey solUnit(UnitType type) {
        return Units.getUnitKey(type, table.sol.getColor());
    }

    private void damageSol(UnitType type, int count) {
        tile.addUnitDamage(SPACE, solUnit(type), count);
    }

    private void closeFightWithADamagedSolFlagship() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 2);
        table.ships(table.sol, tile, UnitType.Flagship, 1);
        damageSol(UnitType.Flagship, 1);
    }

    private Optional<Stage> stage(int round) {
        return table.test
                .memory
                .get(PlayMemory.combatKey(position, "directhit", round))
                .flatMap(CardPlay::decode)
                .map(CardPlay::stage);
    }

    @Test
    void isPlayedOnAnEnemyFlagshipThatJustSustainedDamage() {
        closeFightWithADamagedSolFlagship();
        holdDirectHit();

        assertThat(pressedId(atRoundStart(1).orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        assertThat(stage(1)).contains(Stage.PLAYED);
    }

    @Test
    void isPlayedOnAWarSunEvenWhenTheFightIsWonAnyway() {
        table.sol.addOwnedUnitByID("warsun");
        table.ships(table.nekro, tile, UnitType.Dreadnought, 8);
        table.ships(table.sol, tile, UnitType.Warsun, 1);
        damageSol(UnitType.Warsun, 1);
        holdDirectHit();

        assertThat(atRoundStart(1)).isPresent();
    }

    @Test
    void isNotPlayedOnAShipThatWasAlreadyDamagedBeforeTheFight() {
        closeFightWithADamagedSolFlagship();
        holdDirectHit();

        assertThat(atRoundStart(0)).isEmpty();
        assertThat(atRoundStart(1)).isEmpty();
    }

    @Test
    void isPlayedOnAShipThatSustainedAfterTheFightBegan() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 2);
        table.ships(table.sol, tile, UnitType.Flagship, 1);
        holdDirectHit();
        assertThat(atRoundStart(0)).isEmpty();

        damageSol(UnitType.Flagship, 1);

        assertThat(atRoundStart(1)).isPresent();
    }

    @Test
    void isNotPlayedOnADreadnoughtIIWhichDirectHitCannotDestroy() {
        table.sol.removeOwnedUnitByID("dreadnought");
        table.sol.addOwnedUnitByID("dreadnought2");
        table.ships(table.nekro, tile, UnitType.Dreadnought, 2);
        table.ships(table.sol, tile, UnitType.Dreadnought, 2);
        damageSol(UnitType.Dreadnought, 1);
        holdDirectHit();

        assertThat(atRoundStart(1)).isEmpty();
    }

    @Test
    void isPlayedOnADreadnoughtWhenTheFightIsStillOpen() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 2);
        table.ships(table.sol, tile, UnitType.Dreadnought, 2);
        damageSol(UnitType.Dreadnought, 1);
        holdDirectHit();

        assertThat(atRoundStart(1)).isPresent();
    }

    @Test
    void isKeptForADreadnoughtWhenTheFightIsDecisivelyWonAnyway() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 8);
        table.ships(table.sol, tile, UnitType.Dreadnought, 1);
        damageSol(UnitType.Dreadnought, 1);
        holdDirectHit();

        assertThat(atRoundStart(1)).isEmpty();
    }

    @Test
    void aCheapShipIsNotWorthTheCardWhenTheFightIsDecidedAnyway() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 8);
        table.ships(table.sol, tile, UnitType.Cruiser, 1);
        Hurt cruiser = new Hurt(table.sol, UnitType.Cruiser, table.sol.getUnitByType(UnitType.Cruiser), 1);

        assertThat(DirectHitCards.worthPlaying(table.game, table.nekro, position, cruiser))
                .isFalse();
    }

    @Test
    void isNotPlayedInAFogOfWarGame() {
        closeFightWithADamagedSolFlagship();
        holdDirectHit();
        table.game.setFowMode(true);

        assertThat(atRoundStart(1)).isEmpty();
    }

    @Test
    void isNotPlayedWithoutTheCard() {
        closeFightWithADamagedSolFlagship();

        assertThat(atRoundStart(1)).isEmpty();
    }

    @Test
    void isPlayedAtMostOncePerRound() {
        closeFightWithADamagedSolFlagship();
        holdDirectHit();
        atRoundStart(1);

        assertThat(atRoundStart(1)).isEmpty();
    }

    @Test
    void isNotPlayedOnAnAlliedShip() {
        closeFightWithADamagedSolFlagship();
        holdDirectHit();
        table.nekro.setAllianceMembers("nekrosol");
        table.sol.setAllianceMembers("nekrosol");

        assertThat(DirectHitCards.hurtShips(table.game, table.nekro, tile)).isEmpty();
    }

    @Test
    void theRollWaitsWhileTheCardIsBeingPlayed() {
        closeFightWithADamagedSolFlagship();
        holdDirectHit();
        atRoundStart(1);

        assertThat(CombatCardRules.hold(table.test.context(hand()))).isPresent();
    }

    private AiPrompt announcement() {
        return prompt(
                "announce",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_" + TITLE,
                "no_sabotage",
                "sabotage_ac_" + TITLE + "_nekro");
    }

    private AiPrompt resolveButton() {
        return prompt("resolve", PromptSource.PUBLIC, NOW, "FFCC_nekro_resolveDirectHit");
    }

    private String targetId(UnitType type) {
        UnitKey key = solUnit(type);
        return "FFCC_nekro_directHitOn_" + position + "_" + key.unitName() + "_" + key.getColor();
    }

    private AiPrompt targets(UnitType... types) {
        String[] ids = new String[types.length];
        for (int i = 0; i < types.length; i++) ids[i] = targetId(types[i]);
        return AiTestGame.withContent(
                prompt("targets", PromptSource.PUBLIC, SETTLED, ids),
                table.nekro.getRepresentationUnfogged() + ", please choose.");
    }

    private void playsThroughItsWindow() {
        assertThat(pressedId(atRoundStart(1).orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        table.nekro.getActionCards().clear();
        table.game.getGameStats().recordAcPlay(TITLE, table.nekro);
        ActionCardRules.continuePlay(table.test.context(announcement()));
        ActionCardRules.continuePlay(table.test.context(announcement(), resolveButton()));
    }

    @Test
    void resolvesAfterTheSabotageWindowAndThenPicksTheShipItAimedAt() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 2);
        table.ships(table.sol, tile, UnitType.Flagship, 1);
        table.ships(table.sol, tile, UnitType.Dreadnought, 1);
        damageSol(UnitType.Flagship, 1);
        damageSol(UnitType.Dreadnought, 1);
        holdDirectHit();
        playsThroughItsWindow();

        assertThat(pressedId(
                        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announcement(), resolveButton()))
                                .orElseThrow()))
                .isEqualTo("FFCC_nekro_resolveDirectHit");
        assertThat(stage(1)).contains(Stage.TARGET);

        Optional<AiDecision> choice = ActionCardRules.continuePlay(
                table.test.contextAt(SETTLED + 1_000L, targets(UnitType.Dreadnought, UnitType.Flagship)));

        assertThat(pressedId(choice.orElseThrow())).isEqualTo(targetId(UnitType.Flagship));
        assertThat(stage(1)).contains(Stage.DONE);
    }

    @Test
    void whenNothingWasPlannedItStillPicksTheMostValuableOfferedShip() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 2);
        table.ships(table.sol, tile, UnitType.Flagship, 1);
        table.ships(table.sol, tile, UnitType.Dreadnought, 1);
        damageSol(UnitType.Flagship, 1);
        damageSol(UnitType.Dreadnought, 1);
        CardPlay unplanned = new CardPlay(Stage.TARGET, NOW, NOW, "dh1", "", false, 0, "");
        table.test.memory.put(PlayMemory.combatKey(position, "directhit", 1), unplanned.encode());

        Optional<AiDecision> choice = ActionCardRules.continuePlay(
                table.test.contextAt(SETTLED, targets(UnitType.Dreadnought, UnitType.Flagship)));

        assertThat(pressedId(choice.orElseThrow())).isEqualTo(targetId(UnitType.Flagship));
    }

    @Test
    void aFlagshipIsWorthMoreWhileHoldingDestroyTheirGreatestShip() {
        closeFightWithADamagedSolFlagship();
        Hurt flagship = new Hurt(table.sol, UnitType.Flagship, table.sol.getUnitByType(UnitType.Flagship), 1);
        double without = DirectHitCards.value(table.game, table.nekro, position, flagship);

        table.nekro.setSecret("dtgs");

        assertThat(DirectHitCards.value(table.game, table.nekro, position, flagship))
                .isGreaterThan(without);
    }

    @Test
    void theCardIsKeptHigherWhileHoldingDestroyTheirGreatestShip() {
        holdDirectHit();
        double plain = ActionCardValue.keepValue(table.game, table.nekro, "dh1");

        table.nekro.setSecret("dtgs");

        assertThat(plain).isGreaterThan(0.0);
        assertThat(ActionCardValue.keepValue(table.game, table.nekro, "dh1")).isGreaterThan(plain);
    }

    @Test
    void everyOfficialCopyIsRegistered() {
        assertThat(DirectHitCards.ALIASES).allMatch(AiActionCards::usable);
    }

    private void spaceCannonFiredAtASolFlagship() {
        table.ships(table.sol, tile, UnitType.Flagship, 1);
        holdDirectHit();
        CombatCardRules.watchSpaceCannon(table.test.context(), position);
    }

    @Test
    void isPlayedAfterTheOpponentSustainedAgainstSpaceCannonOffense() {
        spaceCannonFiredAtASolFlagship();
        damageSol(UnitType.Flagship, 1);

        Optional<AiDecision> decision = CombatCardRules.afterSpaceCannon(table.test.contextAt(SETTLED, hand()));

        assertThat(pressedId(decision.orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    @Test
    void waitsForTheOpponentsHitsToBeAssignedAfterSpaceCannonOffense() {
        spaceCannonFiredAtASolFlagship();
        damageSol(UnitType.Flagship, 1);
        AiPrompt assignment = prompt(
                "assign",
                PromptSource.COMBAT_THREAD,
                NOW,
                "FFCC_sol_autoAssignSpaceCannonOffenceHits_" + position + "_2");

        assertThat(CombatCardRules.afterSpaceCannon(table.test.contextAt(SETTLED, hand(), assignment)))
                .isEmpty();
        assertThat(CombatCardRules.afterSpaceCannon(table.test.contextAt(SETTLED, hand())))
                .isPresent();
    }

    @Test
    void doesNothingAfterSpaceCannonOffenseWhenNothingSustained() {
        spaceCannonFiredAtASolFlagship();

        assertThat(CombatCardRules.afterSpaceCannon(table.test.contextAt(SETTLED, hand())))
                .isEmpty();
    }

    @Test
    void ignoresAShipThatWasAlreadyDamagedWhenSpaceCannonOffenseFired() {
        table.ships(table.sol, tile, UnitType.Flagship, 1);
        damageSol(UnitType.Flagship, 1);
        holdDirectHit();
        CombatCardRules.watchSpaceCannon(table.test.context(), position);

        assertThat(CombatCardRules.afterSpaceCannon(table.test.contextAt(SETTLED, hand())))
                .isEmpty();
    }
}
