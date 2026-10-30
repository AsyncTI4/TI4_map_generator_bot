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
import ti4.ai.AiSettings;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.CombatCardHook;
import ti4.ai.tactical.CombatForecast;
import ti4.ai.tactical.CombatMoment;
import ti4.ai.tactical.CombatRules;
import ti4.game.Tile;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class CombatCardFlowTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final long AFTER_GRACE = NOW + 120_000L;
    private static final int HAND_INDEX = 412;
    private static final String SPACE = "space";

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
        System.clearProperty(AiSettings.COMBAT_CARDS_PROPERTY);
    }

    private CombatMoment moment() {
        return new CombatMoment(position, SPACE, false, 0);
    }

    private String key() {
        return PlayMemory.combatKey(position, SPACE, 0);
    }

    private AiPrompt hand() {
        return prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX, "cardsInfo");
    }

    private AiPrompt rolls() {
        return prompt("roll", PromptSource.COMBAT_THREAD, NOW, "combatRoll_" + position + "_" + SPACE);
    }

    private AiPrompt announcement(String title) {
        return prompt(
                "announce",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_" + title,
                "no_sabotage",
                "sabotage_ac_" + title + "_nekro");
    }

    private Optional<Stage> stage() {
        return table.test.memory.get(key()).flatMap(CardPlay::decode).map(CardPlay::stage);
    }

    private void damagedCloseFight() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 2);
        table.ships(table.sol, tile, UnitType.Dreadnought, 2);
        tile.addUnitDamage(SPACE, Units.getUnitKey(UnitType.Dreadnought, table.nekro.getColor()), 2);
    }

    private Optional<AiDecision> atRoundStart() {
        return CombatCardRules.atRoundStart(table.test.context(hand()), moment());
    }

    private void playsThroughItsWindow(String title, String resolveId) {
        assertThat(pressedId(atRoundStart().orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        table.nekro.getActionCards().clear();
        table.game.getGameStats().recordAcPlay(title, table.nekro);
        AiPrompt announce = announcement(title);
        ActionCardRules.continuePlay(table.test.context(announce));
        ActionCardRules.continuePlay(
                table.test.context(announce, prompt("resolve", PromptSource.PUBLIC, NOW, resolveId)));
    }

    @Test
    void emergencyRepairsIsPlayedWhenDamagedShipsDecideACloseFight() {
        damagedCloseFight();
        table.nekro.setActionCard("emergency", HAND_INDEX);

        assertThat(pressedId(atRoundStart().orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        assertThat(stage()).contains(Stage.PLAYED);
    }

    @Test
    void emergencyRepairsIsNotPlayedWithoutDamagedUnits() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 2);
        table.ships(table.sol, tile, UnitType.Dreadnought, 2);
        table.nekro.setActionCard("emergency", HAND_INDEX);

        assertThat(atRoundStart()).isEmpty();
    }

    // Repairing one dreadnought changes nothing when eight others are about to destroy it.
    @Test
    void emergencyRepairsIsKeptWhenTheFightIsLostAnyway() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 1);
        table.ships(table.sol, tile, UnitType.Dreadnought, 8);
        tile.addUnitDamage(SPACE, Units.getUnitKey(UnitType.Dreadnought, table.nekro.getColor()), 1);
        table.nekro.setActionCard("emergency", HAND_INDEX);

        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void emergencyRepairsIsKeptWhenTheFightIsWonAnyway() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 6);
        table.ships(table.sol, tile, UnitType.Fighter, 1);
        tile.addUnitDamage(SPACE, Units.getUnitKey(UnitType.Dreadnought, table.nekro.getColor()), 1);
        table.nekro.setActionCard("emergency", HAND_INDEX);

        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void emergencyRepairsIsOnlyResolvedAfterTheSabotageWindowCloses() {
        damagedCloseFight();
        table.nekro.setActionCard("emergency", HAND_INDEX);
        String resolveId = "FFCC_nekro_resolveEmergencyRepairs_" + position;
        playsThroughItsWindow("Emergency Repairs", resolveId);
        AiPrompt announce = announcement("Emergency Repairs");
        AiPrompt resolve = prompt("resolve", PromptSource.PUBLIC, NOW, resolveId);

        assertThat(ActionCardRules.continuePlay(table.test.contextAt(NOW + 1_000L, announce, resolve)))
                .isEmpty();
        assertThat(CombatCardRules.hold(table.test.contextAt(NOW + 1_000L, announce, resolve)))
                .isPresent();
        assertThat(pressedId(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announce, resolve))
                        .orElseThrow()))
                .isEqualTo(resolveId);
        assertThat(stage()).contains(Stage.DONE);
        assertThat(CombatCardRules.hold(table.test.contextAt(SETTLED, rolls()))).isEmpty();
    }

    @Test
    void aCanceledPlayReleasesTheRoll() {
        damagedCloseFight();
        table.nekro.setActionCard("emergency", HAND_INDEX);
        playsThroughItsWindow("Emergency Repairs", "FFCC_nekro_resolveEmergencyRepairs_" + position);
        table.game.getGameStats().markLatestPlayCanceled("Emergency Repairs");

        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announcement("Emergency Repairs")));

        assertThat(stage()).contains(Stage.CANCELED);
        assertThat(CombatCardRules.hold(table.test.contextAt(SETTLED))).isEmpty();
    }

    @Test
    void theCardIsPlayedInsteadOfTheSeatsRollAtTheMomentItWouldRoll() {
        damagedCloseFight();
        table.nekro.setActionCard("emergency", HAND_INDEX);
        CombatCardHook hook = CombatCardRules::atRoundStart;

        Optional<AiDecision> decision = CombatRules.next(table.test.contextAt(AFTER_GRACE, rolls(), hand()), hook);

        assertThat(pressedId(decision.orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    // The opponent has rolled, so the round has started and its dice are known.
    @Test
    void noStartOfRoundCardIsPlayedOnceTheOpponentHasRolled() {
        damagedCloseFight();
        table.nekro.setActionCard("emergency", HAND_INDEX);
        table.game.setStoredValue("combatRoundTrackersol" + position + SPACE, "1");

        Optional<AiDecision> decision =
                CombatRules.next(table.test.contextAt(AFTER_GRACE, rolls(), hand()), CombatCardRules::atRoundStart);

        assertThat(pressedId(decision.orElseThrow())).isEqualTo("combatRoll_" + position + "_" + SPACE);
    }

    @Test
    void theCardIsPlayedAtMostOncePerRound() {
        damagedCloseFight();
        table.nekro.setActionCard("emergency", HAND_INDEX);
        atRoundStart();

        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void theCardsCanBeSwitchedOff() {
        damagedCloseFight();
        table.nekro.setActionCard("emergency", HAND_INDEX);
        System.setProperty(AiSettings.COMBAT_CARDS_PROPERTY, "false");

        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void noCardIsPlayedInAFogOfWarGame() {
        damagedCloseFight();
        table.nekro.setActionCard("emergency", HAND_INDEX);
        table.game.setFowMode(true);

        assertThat(atRoundStart()).isEmpty();
    }

    // After a restart the memory is gone, but the resolve button of the seat's uncanceled play is still on the table.
    @Test
    void aPlayIsPickedUpAgainFromItsVisibleResolveButton() {
        damagedCloseFight();
        table.game.getGameStats().recordAcPlay("Emergency Repairs", table.nekro);
        String resolveId = "FFCC_nekro_resolveEmergencyRepairs_" + position;
        AiPrompt announce = announcement("Emergency Repairs");
        AiPrompt resolve = prompt("resolve", PromptSource.PUBLIC, NOW, resolveId);

        ActionCardRules.continuePlay(table.test.contextAt(NOW + 1_000L, announce, resolve));

        assertThat(CombatCardRules.hold(table.test.contextAt(NOW + 1_000L, announce, resolve)))
                .isPresent();
        assertThat(pressedId(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announce, resolve))
                        .orElseThrow()))
                .isEqualTo(resolveId);
    }

    private static final String MORALE_BOOST_RESOLVE = "FFCC_nekro_applytempcombatmod__action_cards__";

    private Optional<AiDecision> atRoundStart(CombatMoment moment) {
        return CombatCardRules.atRoundStart(table.test.context(hand()), moment);
    }

    private void evenSpaceFight() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 4);
        table.ships(table.sol, tile, UnitType.Dreadnought, 4);
    }

    @Test
    void moraleBoostIsPlayedInAnEvenFight() {
        evenSpaceFight();
        table.nekro.setActionCard("mb1", HAND_INDEX);

        assertThat(pressedId(atRoundStart().orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    @Test
    void moraleBoostIsKeptWhenTheFightIsDecidedAnyway() {
        table.ships(table.nekro, tile, UnitType.Dreadnought, 6);
        table.ships(table.sol, tile, UnitType.Fighter, 1);
        table.nekro.setActionCard("mb1", HAND_INDEX);

        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void moraleBoostResolvesOnlyItsOwnModifierButton() {
        evenSpaceFight();
        table.nekro.setActionCard("mb2", HAND_INDEX);
        playsThroughItsWindow("Morale Boost", MORALE_BOOST_RESOLVE + "mb3");
        AiPrompt announce = announcement("Morale Boost");
        AiPrompt other = prompt("other", PromptSource.PUBLIC, NOW, MORALE_BOOST_RESOLVE + "mb3");
        AiPrompt own = prompt("own", PromptSource.PUBLIC, NOW, MORALE_BOOST_RESOLVE + "mb2");

        assertThat(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announce, other)))
                .isEmpty();
        assertThat(pressedId(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announce, other, own))
                        .orElseThrow()))
                .isEqualTo(MORALE_BOOST_RESOLVE + "mb2");
    }

    // The modifier is moved in by the seat's next roll of any kind, so the roll waits until it shows.
    @Test
    void theRollWaitsForTheModifierToAppearAfterTheCardResolves() {
        CardPlay done = new CardPlay(Stage.DONE, NOW, NOW, "mb1", "", true, 0, "");
        table.test.memory.put(key(), done.encode());

        assertThat(CombatCardRules.hold(table.test.contextAt(NOW + 1_000L, rolls()))
                        .orElseThrow())
                .isInstanceOf(AiDecision.Wait.class);

        table.nekro.addNewTempCombatMod(
                CombatTempModHelper.getPossibleTempModifier(Constants.AC, "mb1", table.nekro.getNumberOfTurns()));

        assertThat(CombatCardRules.hold(table.test.contextAt(NOW + 1_000L, rolls())))
                .isEmpty();
    }

    @Test
    void theRollDoesNotWaitForeverForAModifierThatNeverComes() {
        CardPlay done = new CardPlay(Stage.DONE, NOW, NOW, "mb1", "", true, 0, "");
        table.test.memory.put(key(), done.encode());

        assertThat(CombatCardRules.hold(table.test.contextAt(NOW + 60_000L, rolls())))
                .isEmpty();
    }

    @Test
    void noCardIsPlayedWhileAModifierIsAlreadyWaiting() {
        evenSpaceFight();
        table.nekro.setActionCard("mb1", HAND_INDEX);
        table.nekro.addNewTempCombatMod(
                CombatTempModHelper.getPossibleTempModifier(Constants.AC, "mb2", table.nekro.getNumberOfTurns()));

        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void moraleBoostIsPlayedInAGroundCombat() {
        for (var player : List.of(table.nekro, table.sol)) {
            table.test.units(tile, "lodor", player, UnitType.Mech, 4);
            table.test.units(tile, "lodor", player, UnitType.Infantry, 4);
        }
        table.nekro.setActionCard("mb1", HAND_INDEX);

        assertThat(pressedId(atRoundStart(new CombatMoment(position, "lodor", true, 0))
                        .orElseThrow()))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    private void fighterFight(int nekroFighters, int solFighters) {
        table.ships(table.nekro, tile, UnitType.Fighter, nekroFighters);
        table.ships(table.sol, tile, UnitType.Fighter, solFighters);
    }

    private void assertTooCheapForTheValueRule() {
        double value = CombatCards.valueAtRoundStart(table.game, table.nekro, moment(), "mb1");
        assertThat(value).isLessThan(ActionCardPrior.keepPrior("mb1"));
    }

    // Four fighters each is about a 51% chance to lose; the stake is too small for the value rule to spend the card.
    @Test
    void moraleBoostIsPlayedInACheapFightThatIsAboutEven() {
        fighterFight(4, 4);
        table.nekro.setActionCard("mb1", HAND_INDEX);

        assertTooCheapForTheValueRule();
        assertThat(pressedId(atRoundStart().orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    // Three against four is about a 78% chance to lose, inside the band.
    @Test
    void moraleBoostIsPlayedInACheapFightThatIsLikelyLost() {
        fighterFight(3, 4);
        table.nekro.setActionCard("mb1", HAND_INDEX);

        assertTooCheapForTheValueRule();
        assertThat(pressedId(atRoundStart().orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    // Four infantry against three is about a 36% chance to lose.
    @Test
    void moraleBoostIsPlayedInACheapGroundFightInsideTheBand() {
        table.test.units(tile, "lodor", table.nekro, UnitType.Infantry, 4);
        table.test.units(tile, "lodor", table.sol, UnitType.Infantry, 3);
        table.nekro.setActionCard("mb1", HAND_INDEX);
        CombatMoment ground = new CombatMoment(position, "lodor", true, 0);

        assertThat(CombatCards.valueAtRoundStart(table.game, table.nekro, ground, "mb1"))
                .isLessThan(ActionCardPrior.keepPrior("mb1"));
        assertThat(pressedId(atRoundStart(ground).orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    // Five fighters against three is about an 8% chance to lose.
    @Test
    void moraleBoostIsKeptInACheapFightThatIsAlmostWon() {
        fighterFight(5, 3);
        table.nekro.setActionCard("mb1", HAND_INDEX);

        assertThat(atRoundStart()).isEmpty();
    }

    // Two fighters against four is about a 95% chance to lose.
    @Test
    void moraleBoostIsKeptInACheapFightThatIsAlmostLost() {
        fighterFight(2, 4);
        table.nekro.setActionCard("mb1", HAND_INDEX);

        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void theLossChanceBandIncludesItsEdges() {
        assertThat(CombatCards.lossWorthAMoraleBoost(0.2)).isTrue();
        assertThat(CombatCards.lossWorthAMoraleBoost(0.8)).isTrue();
        assertThat(CombatCards.lossWorthAMoraleBoost(0.1999)).isFalse();
        assertThat(CombatCards.lossWorthAMoraleBoost(0.8001)).isFalse();
        assertThat(CombatCards.lossWorthAMoraleBoost(0.5)).isTrue();
    }

    @Test
    void noMoraleBoostIsPlayedInACloseFightWhenNoneIsHeld() {
        fighterFight(4, 4);
        table.nekro.setActionCard("emergency", HAND_INDEX);

        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void moraleBoostIsPlayedAtMostOncePerRoundInACloseFight() {
        fighterFight(4, 4);
        table.nekro.setActionCard("mb1", HAND_INDEX);
        table.nekro.setActionCard("mb2", HAND_INDEX + 1);

        assertThat(atRoundStart()).isPresent();
        assertThat(atRoundStart()).isEmpty();
    }

    @Test
    void moraleBoostIsNotPlayedInACloseFightOnceTheOpponentHasRolled() {
        fighterFight(4, 4);
        table.nekro.setActionCard("mb1", HAND_INDEX);
        table.game.setStoredValue("combatRoundTrackersol" + position + SPACE, "1");

        Optional<AiDecision> decision =
                CombatRules.next(table.test.contextAt(AFTER_GRACE, rolls(), hand()), CombatCardRules::atRoundStart);

        assertThat(pressedId(decision.orElseThrow())).isEqualTo("combatRoll_" + position + "_" + SPACE);
    }

    @Test
    void noMoraleBoostIsPlayedInACloseFightWhileAModifierIsAlreadyWaiting() {
        fighterFight(4, 4);
        table.nekro.setActionCard("mb1", HAND_INDEX);
        table.nekro.addNewTempCombatMod(
                CombatTempModHelper.getPossibleTempModifier(Constants.AC, "mb2", table.nekro.getNumberOfTurns()));

        assertThat(atRoundStart()).isEmpty();
    }

    // Mutual destruction is a loss for both seats, so the two chances to lose add up to more than one.
    @Test
    void theDefenderMeasuresItsOwnChanceToLose() {
        fighterFight(4, 4);
        double attackerLoss = CombatForecast.lossChance(table.game, table.nekro, position, SPACE)
                .orElseThrow();
        double defenderLoss = CombatForecast.lossChance(table.game, table.sol, position, SPACE)
                .orElseThrow();

        assertThat(defenderLoss).isBetween(0.2, 0.8);
        assertThat(attackerLoss + defenderLoss).isGreaterThan(1.0);
    }

    @Test
    void fighterPrototypeIsPlayedOnlyInTheFirstRoundOfASpaceCombat() {
        table.ships(table.nekro, tile, UnitType.Fighter, 8);
        table.ships(table.sol, tile, UnitType.Fighter, 8);
        table.nekro.setActionCard("f_prototype", HAND_INDEX);

        assertThat(atRoundStart(new CombatMoment(position, SPACE, false, 1))).isEmpty();
        assertThat(pressedId(atRoundStart().orElseThrow())).isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    @Test
    void fighterPrototypeIsNotPlayedInAGroundCombat() {
        table.test.units(tile, "lodor", table.nekro, UnitType.Infantry, 3);
        table.test.units(tile, "lodor", table.sol, UnitType.Infantry, 3);
        table.ships(table.nekro, tile, UnitType.Fighter, 8);
        table.nekro.setActionCard("f_prototype", HAND_INDEX);

        assertThat(atRoundStart(new CombatMoment(position, "lodor", true, 0))).isEmpty();
    }

    @Test
    void fighterPrototypeIsNotPlayedWithoutFighters() {
        evenSpaceFight();
        table.nekro.setActionCard("f_prototype", HAND_INDEX);

        assertThat(atRoundStart()).isEmpty();
    }

    // Anti-fighter barrage is rolled first and would use the modifier up, so the card waits for it.
    @Test
    void noModifierCardIsPlayedBeforeTheSeatsOwnAntiFighterBarrage() {
        table.ships(table.nekro, tile, UnitType.Destroyer, 2);
        table.ships(table.sol, tile, UnitType.Fighter, 4);
        table.ships(table.sol, tile, UnitType.Destroyer, 2);
        table.nekro.setActionCard("mb1", HAND_INDEX);
        AiPrompt barrage = prompt(
                "barrage",
                PromptSource.COMBAT_THREAD,
                NOW,
                "combatRoll_" + position + "_space_afb",
                "combatRoll_" + position + "_space");

        Optional<AiDecision> decision =
                CombatRules.next(table.test.contextAt(AFTER_GRACE, barrage, hand()), CombatCardRules::atRoundStart);

        assertThat(pressedId(decision.orElseThrow())).isEqualTo("combatRoll_" + position + "_space_afb");
    }

    @Test
    void aMoraleBoostIsPickedUpAgainFromItsVisibleResolveButton() {
        evenSpaceFight();
        table.game.getGameStats().recordAcPlay("Morale Boost", table.nekro);
        AiPrompt announce = announcement("Morale Boost");
        AiPrompt resolve = prompt("resolve", PromptSource.PUBLIC, NOW, MORALE_BOOST_RESOLVE + "mb2");

        ActionCardRules.continuePlay(table.test.contextAt(NOW + 1_000L, announce, resolve));

        assertThat(CombatCardRules.hold(table.test.contextAt(NOW + 1_000L, announce, resolve)))
                .isPresent();
        assertThat(pressedId(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announce, resolve))
                        .orElseThrow()))
                .isEqualTo(MORALE_BOOST_RESOLVE + "mb2");
    }

    @Test
    void theCombatCardsAreKeptAtTheirPriorInsteadOfBeingDiscardedFirst() {
        for (String alias : List.of("mb1", "f_prototype", "emergency")) {
            assertThat(ActionCardValue.keepValue(table.game, table.nekro, alias))
                    .as(alias)
                    .isEqualTo(ActionCardPrior.keepPrior(alias));
        }
    }
}
