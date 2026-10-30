package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
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
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.StrategyCard;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.secrets.SecretDrawValue;
import ti4.ai.strategy.OverruleChoice;
import ti4.ai.strategy.SecondaryQueue;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.strategy.StrategyCardTurn;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class OverruleCardTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final int HAND_INDEX = 412;

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.nekro.removeAbility("propagation");
        table.nekro.setStrategicCC(2);
        table.nekro.setTacticalCC(3);
        table.nekro.setFleetCC(3);
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

    private Optional<Plan> plan() {
        return AiActionCards.of("overrule").orElseThrow().planFor(table.game, table.nekro);
    }

    private int initiativeOf(StrategyCard card) {
        for (int initiative : table.game.getSCList()) {
            if (StrategyCard.of(table.game, initiative) == card) return initiative;
        }
        throw new AssertionError(card + " is not in this game");
    }

    private void onlyThese(StrategyCard... cards) {
        List<StrategyCard> kept = List.of(cards);
        for (int initiative : table.game.getSCList()) {
            if (!kept.contains(StrategyCard.of(table.game, initiative))) table.game.setSCPlayed(initiative, true);
        }
    }

    // Overrule is held for the late points it can win. In the late game Nekro qualifies for two public objectives,
    // which Imperial scores one more of than the status phase could, so the card is played on Imperial while it is
    // still to be played.
    @Test
    void overruleIsPlayedOnImperialWhileItIsStillToBePlayed() {
        lateGame();
        qualifyForTwoPublics();
        Plan plan = plan().orElseThrow();

        assertThat(plan.target()).isEqualTo(initiativeOf(StrategyCard.IMPERIAL) + "_overrule");
        assertThat(plan.value()).isGreaterThan(ActionCardPrior.keepPrior("overrule"));
    }

    @Test
    void overruleCannotUseACardThatHasBeenPlayed() {
        table.game.setSCPlayed(initiativeOf(StrategyCard.IMPERIAL), true);

        assertThat(plan().map(Plan::target))
                .isNotEqualTo(Optional.of(initiativeOf(StrategyCard.IMPERIAL) + "_overrule"));
    }

    // Only Imperial and Technology are performed: their steps are the ones the bot can run for a card nobody played.
    @Test
    void overruleLeavesTheOtherCardsAlone() {
        onlyThese(StrategyCard.LEADERSHIP, StrategyCard.POLITICS, StrategyCard.WARFARE, StrategyCard.TRADE);

        assertThat(plan()).isEmpty();
    }

    // Sol is within two points of the goal and Nekro is not: the final round, in which Nekro cannot win with the card
    // and so spends it on its best use instead of keeping it for a late point.
    @Test
    void overrulePrefersTechnologyWhenItIsTheOnlyCardWorthDoing() {
        table.points(table.sol, 8);
        onlyThese(StrategyCard.TECHNOLOGY, StrategyCard.LEADERSHIP);

        assertThat(plan().orElseThrow().target()).isEqualTo(initiativeOf(StrategyCard.TECHNOLOGY) + "_overrule");
    }

    // ---- the flow ----------------------------------------------------------------------------

    private AiPrompt hand() {
        return prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX, "cardsInfo");
    }

    private AiPrompt announcement() {
        return prompt(
                "announce",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_moveAlongAfterAllHaveReactedToAC_Overrule",
                "no_sabotage",
                "sabotage_ac_Overrule_nekro");
    }

    private AiPrompt resolve() {
        return prompt("resolve", PromptSource.PUBLIC, NOW, "FFCC_nekro_overruleStart");
    }

    private String turnKey() {
        return "acPlay|turn:" + table.test.context().turnKey();
    }

    private AiPrompt cardChoices(int... initiatives) {
        String[] ids = java.util.Arrays.stream(initiatives)
                .mapToObj(initiative -> "winnuHero_" + initiative + "_overrule")
                .toArray(String[]::new);
        return prompt("choices", PromptSource.PUBLIC, SETTLED, ids);
    }

    // Sol is within two points of the goal and Nekro is not, which is the final round with Nekro unable to win with the
    // card: the reserve is gone and the card is played on its best use.
    private void playsAndResolves() {
        table.points(table.sol, 8);
        table.nekro.setActionCard("overrule", HAND_INDEX);
        assertThat(AiTestGame.pressedId(ActionCardRules.playBeforePassing(table.test.context(hand()))
                        .orElseThrow()))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        table.nekro.getActionCards().remove("overrule");
        table.game.getGameStats().recordAcPlay("Overrule", table.nekro);
        ActionCardRules.continuePlay(table.test.context(announcement()));
        ActionCardRules.continuePlay(table.test.context(announcement(), resolve()));
        assertThat(AiTestGame.pressedId(
                        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, announcement(), resolve()))
                                .orElseThrow()))
                .isEqualTo("FFCC_nekro_overruleStart");
    }

    @Test
    void overrulePicksTheCardItPlannedAndStartsItsPrimary() {
        onlyThese(StrategyCard.TECHNOLOGY, StrategyCard.LEADERSHIP);
        int technology = initiativeOf(StrategyCard.TECHNOLOGY);
        int leadership = initiativeOf(StrategyCard.LEADERSHIP);

        playsAndResolves();
        Optional<AiDecision> pick =
                ActionCardRules.continuePlay(table.test.contextAt(SETTLED, cardChoices(leadership, technology)));

        assertThat(AiTestGame.pressedId(pick.orElseThrow())).isEqualTo("winnuHero_" + technology + "_overrule");
        assertThat(table.test.memory.get(turnKey()).flatMap(CardPlay::decode).map(CardPlay::stage))
                .contains(Stage.DONE);
        assertThat(StrategyCardRules.playedThisTurn(table.test.contextAt(SETTLED)))
                .isTrue();
    }

    @Test
    void overruleNeverPicksACardItDoesNotPerform() {
        onlyThese(StrategyCard.TECHNOLOGY, StrategyCard.LEADERSHIP);
        int leadership = initiativeOf(StrategyCard.LEADERSHIP);

        playsAndResolves();

        assertThat(ActionCardRules.continuePlay(table.test.contextAt(SETTLED, cardChoices(leadership))))
                .isEmpty();
    }

    // Nobody follows an overruled card, so the seat must not wait for the secondaries of a card another player holds.
    @Test
    void anOverruledPrimaryIsNotAwaitedByAnySecondary() {
        onlyThese(StrategyCard.TECHNOLOGY);
        int technology = initiativeOf(StrategyCard.TECHNOLOGY);
        table.sol.addSC(technology);
        playsAndResolves();
        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, cardChoices(technology)));

        assertThat(StrategyCardRules.awaitingSecondaries(table.test.contextAt(SETTLED)))
                .isFalse();
        assertThat(SecondaryQueue.blockerFor(table.game, table.sol, technology, SETTLED))
                .isEmpty();
    }

    // The card's message names the card as overruled and has no "no follow" button; its primary buttons are the
    // seat's own. The seat still runs the primary from it.
    @Test
    void theOverruledCardsPrimaryIsRunFromItsOwnedButtons() {
        onlyThese(StrategyCard.TECHNOLOGY);
        int technology = initiativeOf(StrategyCard.TECHNOLOGY);
        playsAndResolves();
        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, cardChoices(technology)));
        AiPrompt message = AiTestGame.withContent(
                prompt("overruled", PromptSource.PUBLIC, SETTLED, "FFCC_nekro_acquireATechWithSC_first"),
                "**" + Helper.getSCName(technology, table.game) + "** overruled by " + table.nekro.getRepresentation()
                        + ".");

        Optional<AiDecision> primary = StrategyCardRules.resolvePrimary(table.test.contextAt(SETTLED, message));

        assertThat(AiTestGame.pressedId(primary.orElseThrow())).isEqualTo("FFCC_nekro_acquireATechWithSC_first");
    }

    // The bot names the overruled card by its emojis alone (the card's name is not in the text), so the message is
    // found by those.
    @Test
    void theOverruledCardsMessageIsFoundByItsEmojisAlone() {
        onlyThese(StrategyCard.TECHNOLOGY);
        int technology = initiativeOf(StrategyCard.TECHNOLOGY);
        playsAndResolves();
        ActionCardRules.continuePlay(table.test.contextAt(SETTLED, cardChoices(technology)));
        AiPrompt message = AiTestGame.withContent(
                prompt("overruled", PromptSource.PUBLIC, SETTLED, "FFCC_nekro_acquireATechWithSC_first"),
                table.game.getSCEmojiWordRepresentation(technology) + " overruled by " + table.nekro.getRepresentation()
                        + ".\n\n");

        Optional<AiDecision> primary = StrategyCardRules.resolvePrimary(table.test.contextAt(SETTLED, message));

        assertThat(AiTestGame.pressedId(primary.orElseThrow())).isEqualTo("FFCC_nekro_acquireATechWithSC_first");
    }

    @Test
    void overruleIsPickedUpAgainAfterTheMemoryIsLost() {
        onlyThese(StrategyCard.TECHNOLOGY);
        int technology = initiativeOf(StrategyCard.TECHNOLOGY);
        table.game.getGameStats().recordAcPlay("Overrule", table.nekro);

        Optional<AiDecision> pick =
                ActionCardRules.continuePlay(table.test.contextAt(SETTLED, cardChoices(technology)));

        assertThat(AiTestGame.pressedId(pick.orElseThrow())).isEqualTo("winnuHero_" + technology + "_overrule");
    }

    // ---- holding it for points ---------------------------------------------------------------

    private void changed() {
        table.game.setLastModifiedDate(modified++);
    }

    private int imperial() {
        return initiativeOf(StrategyCard.IMPERIAL);
    }

    private String target(StrategyCard card) {
        return initiativeOf(card) + "_overrule";
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    // Sol at 6 of 10: the late game, when points start to decide the game, but not yet the final round.
    private void lateGame() {
        table.points(table.sol, 6);
        changed();
    }

    private void holdsOverrule() {
        table.nekro.setActionCard("overrule", HAND_INDEX);
    }

    private void ownPlanets(String tile, String position, String... planets) {
        table.system(tile, position);
        for (String planet : planets) table.nekro.addPlanet(planet);
    }

    // Expand Borders (6 planets outside the home system) and Corner the Market (4 planets of one type): two public
    // objectives worth a point each that Nekro can score right now.
    private void qualifyForTwoPublics() {
        table.reveal("expand_borders");
        table.reveal("corner");
        ownPlanets("38", "201", "abyz", "fria");
        ownPlanets("35", "202", "bereg", "lirtaiv");
        ownPlanets("36", "203", "arnor", "lor");
        changed();
    }

    // Build Defenses (4 structures) as a third: the dock at home and a PDS on three of those planets.
    private void qualifyForThreePublics() {
        qualifyForTwoPublics();
        table.reveal("build_defenses");
        table.test.units(table.game.getTileByPosition("201"), "abyz", table.nekro, UnitType.Pds, 1);
        table.test.units(table.game.getTileByPosition("201"), "fria", table.nekro, UnitType.Pds, 1);
        table.test.units(table.game.getTileByPosition("202"), "bereg", table.nekro, UnitType.Pds, 1);
        changed();
    }

    private void holdMecatol() {
        table.test.place("18", "000");
        table.nekro.addPlanet("mr");
        changed();
    }

    // A carrier with two infantry next to a Mecatol Rex nobody holds: the best tactical action takes it.
    private void stageForMecatol() {
        table.test.place("18", "000");
        Tile staging = table.system("26", AiTestGame.neighbourOf("000"));
        table.ships(table.nekro, staging, UnitType.Carrier, 1);
        table.ships(table.nekro, staging, UnitType.Infantry, 2);
        changed();
    }

    private void fullOfSecrets() {
        table.nekro.setSecret("dhw");
        table.nekro.setSecret("syc");
        table.nekro.setSecret("sai");
        changed();
    }

    private AiPrompt turnStart() {
        return prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_strategicAction_8");
    }

    // The reserve is the late game point Overrule could still win. A fresh secret is not worth it, and neither is a
    // free technology, so the card stays in hand and is kept above the rest of the hand.
    @Test
    void withNothingToWinItIsHeldAndKept() {
        holdsOverrule();

        assertThat(OverruleChoice.option(table.game, table.nekro, imperial())
                        .orElseThrow()
                        .value())
                .isLessThan(OverruleReserve.VALUE);
        assertThat(plan()).isEmpty();
        assertThat(ActionCardValue.bestNewComponentPlay(table.game, table.nekro))
                .isEmpty();
        assertThat(ActionCardValue.keepValue(table.game, table.nekro, "overrule"))
                .isGreaterThanOrEqualTo(OverruleReserve.VALUE);
    }

    @Test
    void anExtraPublicObjectiveThroughImperialIsWorthPlayingFor() {
        lateGame();
        qualifyForTwoPublics();
        holdsOverrule();
        double point = ScoringStakes.scoreValue(table.game, table.nekro, 1);
        double secret = SecretDrawValue.of(table.game, table.nekro);

        Plan plan = plan().orElseThrow();

        assertThat(plan.target()).isEqualTo(target(StrategyCard.IMPERIAL));
        assertThat(plan.urgent()).isTrue();
        assertThat(plan.value()).isBetween(point + secret + table.stall(), point + secret + table.stall() + 1);
        assertThat(ActionCardValue.bestNewComponentPlay(table.game, table.nekro))
                .hasValueSatisfying(best -> {
                    assertThat(best.alias()).isEqualTo("overrule");
                    assertThat(best.urgent()).isTrue();
                });
    }

    @Test
    void aPointsPlayDoesNotLowerTheKeepFloor() {
        lateGame();
        qualifyForTwoPublics();
        holdsOverrule();

        assertThat(ActionCardValue.keepValue(table.game, table.nekro, "overrule"))
                .isEqualTo(OverruleReserve.VALUE);
    }

    // Players save Overrule for the late game: early on even an extra public objective and a secret are not worth it,
    // so the card is held and kept.
    @Test
    void earlyInTheGameEvenAnExtraPublicIsHeldForTheLateGame() {
        qualifyForTwoPublics();
        holdsOverrule();

        assertThat(OverruleChoice.option(table.game, table.nekro, imperial())
                        .orElseThrow()
                        .pointsPlay())
                .isTrue();
        assertThat(plan()).isEmpty();
        assertThat(ActionCardValue.bestNewComponentPlay(table.game, table.nekro))
                .isEmpty();
        assertThat(ActionCardValue.keepValue(table.game, table.nekro, "overrule"))
                .isEqualTo(OverruleReserve.VALUE);
    }

    // Early on its own Imperial is not held back for an Overrule the seat is saving.
    @Test
    void earlyInTheGameItsOwnImperialIsPlayedAsBefore() {
        table.nekro.addSC(imperial());
        qualifyForThreePublics();
        holdsOverrule();
        AiPrompt turn = turnStart();

        assertThat(OverruleChoice.overrulesOwnImperialFirst(table.game, table.nekro))
                .isFalse();
        assertThat(pressed(StrategyCardTurn.playIfDue(table.test.context(turn), List.of(turn))))
                .isEqualTo("FFCC_nekro_strategicAction_" + imperial());
    }

    // The Imperial holder may play it before the seat gets another turn, and a tactical action that completes an
    // objective routinely scores more than one point: Overrule goes first anyway.
    @Test
    void mecatolRexWithImperialStillToPlayIsPlayedAtOnceOverATacticalAction() {
        lateGame();
        holdMecatol();
        table.sol.addSC(imperial());
        holdsOverrule();
        table.reveal("expand_borders");
        ownPlanets("38", "201", "abyz", "fria");
        ownPlanets("35", "202", "bereg", "lirtaiv");
        Tile home = table.game.getTileByPosition(AiTestGame.HOME);
        table.ships(table.nekro, home, UnitType.Carrier, 1);
        table.test.units(home, "mordaiii", table.nekro, UnitType.Infantry, 4);
        table.system("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        changed();

        Plan plan = plan().orElseThrow();
        double tactical =
                TacticalPlanner.best(table.game, table.nekro).orElseThrow().score();

        assertThat(plan.target()).isEqualTo(target(StrategyCard.IMPERIAL));
        assertThat(plan.urgent()).isTrue();
        assertThat(tactical).isGreaterThanOrEqualTo(plan.value());
        assertThat(pressed(ActionCardRules.insteadOfTacticalAction(table.test.context(hand()))))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    @Test
    void aPlayThatReachesTheGoalIsWorthTheWholeGame() {
        table.points(table.nekro, 8);
        holdMecatol();
        qualifyForTwoPublics();
        holdsOverrule();

        assertThat(ScoringStakes.overrulePlay(table.game, table.nekro).wins()).isTrue();
        assertThat(plan().orElseThrow().value()).isGreaterThanOrEqualTo(ScoringStakes.WIN_VALUE);
    }

    // At 9 of 10 one qualifying public is not an extra score (the status phase would score it) but Overrule scores
    // it now, and that ends the game.
    @Test
    void aSinglePublicIsAWinningPlayOnePointFromTheGoal() {
        table.points(table.nekro, 9);
        table.reveal("expand_borders");
        ownPlanets("38", "201", "abyz", "fria");
        ownPlanets("35", "202", "bereg", "lirtaiv");
        ownPlanets("36", "203", "arnor", "lor");
        changed();
        holdsOverrule();

        ScoringStakes.OverrulePlay play = ScoringStakes.overrulePlay(table.game, table.nekro);

        assertThat(play.wins()).isTrue();
        assertThat(play.points()).isEqualTo(1);
        assertThat(plan().orElseThrow().value()).isGreaterThanOrEqualTo(ScoringStakes.WIN_VALUE);
    }

    // Conquer the Weak (control another player's home planet) and Unify the Colonies (6 planets of one type) are
    // worth two points each.
    @Test
    void twoStageIIPublicsAreWorthTwoPoints() {
        table.reveal("conquer");
        table.reveal("unify_colonies");
        ownPlanets("01", "304", "jord");
        ownPlanets("38", "201", "abyz", "fria");
        ownPlanets("35", "202", "bereg", "lirtaiv");
        ownPlanets("31", "203", "sakulag");
        ownPlanets("28", "204", "tequran");
        changed();

        assertThat(ObjectivePolicy.scorablePublics(table.game, table.nekro))
                .containsExactlyInAnyOrder("conquer", "unify_colonies");
        assertThat(ScoringStakes.overrulePoints(table.game, table.nekro)).isEqualTo(2);
    }

    // Its own unplayed Imperial already scores one of two publics, so Overrule needs a third to add a point.
    @Test
    void anImperialOfItsOwnMakesTwoPublicsNotEnough() {
        lateGame();
        table.nekro.addSC(imperial());
        qualifyForTwoPublics();
        holdsOverrule();

        OverruleChoice.Option option =
                OverruleChoice.option(table.game, table.nekro, imperial()).orElseThrow();

        assertThat(ScoringStakes.overrulePoints(table.game, table.nekro)).isZero();
        assertThat(option.pointsPlay()).isFalse();
        assertThat(OverruleChoice.overrulesOwnImperialFirst(table.game, table.nekro))
                .isFalse();
    }

    @Test
    void aThirdPublicMakesOverruleGoBeforeItsOwnImperial() {
        lateGame();
        table.nekro.addSC(imperial());
        qualifyForThreePublics();
        holdsOverrule();

        assertThat(ScoringStakes.overrulePoints(table.game, table.nekro)).isEqualTo(1);
        assertThat(OverruleChoice.option(table.game, table.nekro, imperial())
                        .orElseThrow()
                        .pointsPlay())
                .isTrue();
        assertThat(OverruleChoice.overrulesOwnImperialFirst(table.game, table.nekro))
                .isTrue();
        AiPrompt turn = turnStart();
        assertThat(StrategyCardTurn.playIfDue(table.test.context(turn), List.of(turn)))
                .isEmpty();
        assertThat(AiTestGame.pressedId(new NekroBrain().decide(table.test.contextAt(SETTLED, turn, hand()))))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    @Test
    void imperialIsPlayedAsBeforeOnceOverruleIsGone() {
        table.nekro.addSC(imperial());
        qualifyForThreePublics();
        AiPrompt turn = turnStart();

        assertThat(pressed(StrategyCardTurn.playIfDue(table.test.context(turn), List.of(turn))))
                .isEqualTo("FFCC_nekro_strategicAction_" + imperial());
    }

    @Test
    void mecatolRexAlsoMakesOverruleGoBeforeItsOwnImperial() {
        lateGame();
        table.nekro.addSC(imperial());
        holdMecatol();
        holdsOverrule();
        AiPrompt turn = turnStart();

        assertThat(ScoringStakes.overrulePoints(table.game, table.nekro)).isEqualTo(1);
        assertThat(StrategyCardTurn.playIfDue(table.test.context(turn), List.of(turn)))
                .isEmpty();
        assertThat(StrategyCardTurn.dueNow(table.test.context(turn), imperial()))
                .isFalse();
    }

    // Over the hand limit the seat may not play action cards, so Imperial is not held back for an Overrule it cannot
    // play.
    @Test
    void overruleDoesNotGoFirstWhenTheSeatMayNotPlayActionCards() {
        lateGame();
        table.nekro.addSC(imperial());
        qualifyForThreePublics();
        table.cards(table.nekro, "overrule", "sabo1", "sabo2", "sabo3", "sabo4", "rout", "mb1", "mjets1");

        assertThat(table.nekro.getAcCount()).isGreaterThan(7);
        assertThat(OverruleChoice.overrulesOwnImperialFirst(table.game, table.nekro))
                .isFalse();
    }

    // The swipe: take Mecatol Rex, then overrule Imperial for its point. The tactical action comes first.
    @Test
    void overruleWaitsForTheTacticalActionThatTakesMecatolRex() {
        lateGame();
        qualifyForTwoPublics();
        stageForMecatol();
        holdsOverrule();

        assertThat(TacticalPlanner.ranked(table.game, table.nekro).getFirst().target())
                .isEqualTo("000");
        assertThat(OverruleChoice.option(table.game, table.nekro, imperial())
                        .orElseThrow()
                        .pointsPlay())
                .isTrue();
        assertThat(plan()).isEmpty();
        assertThat(ActionCardRules.insteadOfTacticalAction(table.test.context(hand())))
                .isEmpty();

        table.nekro.addPlanet("mr");
        changed();

        assertThat(plan().map(Plan::target)).contains(target(StrategyCard.IMPERIAL));
        assertThat(ScoringStakes.overrulePoints(table.game, table.nekro)).isEqualTo(2);
    }

    @Test
    void aWinningOverruleIsNotHeldForTheSwipe() {
        table.points(table.nekro, 9);
        table.reveal("expand_borders");
        ownPlanets("38", "201", "abyz", "fria");
        ownPlanets("35", "202", "bereg", "lirtaiv");
        ownPlanets("36", "203", "arnor", "lor");
        stageForMecatol();
        holdsOverrule();

        assertThat(OverruleChoice.takesMecatolFirst(table.game, table.nekro)).isTrue();
        assertThat(plan().map(Plan::target)).contains(target(StrategyCard.IMPERIAL));
    }

    // About to pass, the seat has no tactical action left to wait for.
    @Test
    void theSwipeDoesNotHoldOverruleOnceTheSeatIsAboutToPass() {
        lateGame();
        qualifyForTwoPublics();
        stageForMecatol();
        holdsOverrule();

        assertThat(AiActionCards.of("overrule")
                        .orElseThrow()
                        .planFor(table.game, table.nekro, PlayMoment.BEFORE_PASSING))
                .isPresent();
    }

    // Imperial's holder scores the publics itself the same way, so it also waits for the swipe, unless the score wins.
    @Test
    void imperialWaitsForTheSwipeUnlessItWinsTheGame() {
        table.nekro.addSC(imperial());
        qualifyForTwoPublics();
        stageForMecatol();
        AiPrompt turn = turnStart();

        assertThat(StrategyCardTurn.playIfDue(table.test.context(turn), List.of(turn)))
                .isEmpty();

        table.points(table.nekro, 9);
        changed();

        assertThat(pressed(StrategyCardTurn.playIfDue(table.test.context(turn), List.of(turn))))
                .isEqualTo("FFCC_nekro_strategicAction_" + imperial());
    }

    // A technology worth more than the Imperial points play would be chosen on value alone; while the reserve holds
    // it is not even a candidate.
    @Test
    void aBetterTechnologyDoesNotBeatAnImperialPointsPlayWhileTheReserveHolds() {
        lateGame();
        qualifyForTwoPublics();
        table.nekro.setTg(4);
        fullOfSecrets();
        double technology = OverruleChoice.option(table.game, table.nekro, initiativeOf(StrategyCard.TECHNOLOGY))
                .orElseThrow()
                .value();
        double imperialPlay = OverruleChoice.option(table.game, table.nekro, imperial())
                .orElseThrow()
                .value();

        assertThat(technology).isGreaterThan(imperialPlay).isLessThan(OverruleReserve.VALUE);
        assertThat(plan().map(Plan::target)).contains(target(StrategyCard.IMPERIAL));
    }

    // The final round: the seat cannot win with it, so it gets its best use instead of waiting. With one round left a
    // fresh secret is worth little, so the better use is the free technology.
    @Test
    void aSeatThatCannotWinInTheFinalRoundSpendsItOnItsBestUse() {
        table.points(table.sol, 8);
        holdsOverrule();
        double secret = OverruleChoice.option(table.game, table.nekro, imperial())
                .orElseThrow()
                .value();
        double research = OverruleChoice.option(table.game, table.nekro, initiativeOf(StrategyCard.TECHNOLOGY))
                .orElseThrow()
                .value();

        assertThat(secret).isLessThan(research);
        assertThat(plan().map(Plan::target)).contains(target(StrategyCard.TECHNOLOGY));
        assertThat(ActionCardValue.bestNewComponentPlay(table.game, table.nekro))
                .isPresent();
        assertThat(ActionCardValue.keepValue(table.game, table.nekro, "overrule"))
                .isLessThan(OverruleReserve.VALUE);
    }

    @Test
    void aContenderInTheFinalRoundHoldsItUntilItIsAboutToPass() {
        table.points(table.nekro, 8);
        onlyThese(StrategyCard.TECHNOLOGY, StrategyCard.LEADERSHIP);
        holdsOverrule();

        assertThat(ActionCardRules.insteadOfTacticalAction(table.test.context(hand())))
                .isEmpty();
        assertThat(pressed(ActionCardRules.playBeforePassing(table.test.context(hand()))))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
    }

    // Sabotage is kept for what threatens a point while a contender is at the table (4), and the reserve for
    // Overrule is above that.
    @Test
    void aContenderKeepsOverruleAheadOfASabotageReservedForAnotherContender() {
        table.points(table.nekro, 8);
        table.points(table.sol, 8);
        table.cards(table.nekro, "sabo1", "overrule", "rout");

        List<String> without = ActionCardValue.discardOrder(table.game, table.nekro);
        assertThat(ActionCardValue.keepValue(table.game, table.nekro, "sabo1"))
                .isEqualTo(SabotagePolicy.CONTENDER_RESERVE);
        assertThat(without.indexOf("sabo1")).isLessThan(without.indexOf("overrule"));

        qualifyForTwoPublics();
        List<String> with = ActionCardValue.discardOrder(table.game, table.nekro);

        assertThat(with.indexOf("sabo1")).isLessThan(with.indexOf("overrule"));
        assertThat(ActionCardValue.keepValue(table.game, table.nekro, "overrule"))
                .isEqualTo(OverruleReserve.VALUE);
    }

    // Sabotage reads the same points as the seat does, win included.
    @Test
    void anOpponentsOverruleIsReadWithTheSamePointsIncludingTheWin() {
        table.solHome();
        table.points(table.sol, 9);
        table.reveal("trade_routes");
        table.sol.setTg(5);
        changed();

        ScoringStakes.OverrulePlay play = ScoringStakes.overrulePlay(table.game, table.sol);

        assertThat(play.wins()).isTrue();
        assertThat(CardScoring.pointsFor("Overrule", table.game, table.sol)).isEqualTo(play.points());
        assertThat(ScoringStakes.rivalStake(table.game, table.sol, play.points()))
                .isEqualTo(ScoringStakes.WIN_VALUE);
    }
}
