package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.PaymentRules;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class StrategyCardRulesTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int CONSTRUCTION = 4;
    private static final long MINUTE = 60_000L;
    private static final int IMPERIAL = 8;
    private static final int TECHNOLOGY = 7;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        test.aiIsActive("action");
    }

    private static AiPrompt imperialCard() {
        return prompt(
                "imperial",
                PromptSource.PUBLIC,
                NOW,
                "sc_follow_8",
                "sc_no_follow_8",
                "sc_draw_so",
                "score_imperial",
                "scoreAnObjective");
    }

    private void playedImperial(AiPrompt card) {
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), IMPERIAL);
    }

    // Without an objective to score and without Mecatol Rex, the Imperial primary still draws a secret objective
    // instead of being thrown away.
    @Test
    void drawsASecretWithImperialWhenNothingIsScorable() {
        AiPrompt card = imperialCard();
        playedImperial(card);

        Optional<AiDecision> decision = StrategyCardRules.resolvePrimary(test.context(card));

        assertThat(decision.map(AiTestGame::pressedId)).contains("sc_draw_so");
    }

    // With a public objective it qualifies for, it opens the scoring prompt first and then scores from it.
    @Test
    void scoresAPublicObjectiveWithImperial() {
        test.nekroHome();
        test.game
                .getRevealedPublicObjectives()
                .put("lead", test.game.getRevealedPublicObjectives().size() + 1);
        test.nekro.setTacticalCC(3);
        AiPrompt card = imperialCard();
        playedImperial(card);

        assertThat(StrategyCardRules.resolvePrimary(test.context(card)).map(AiTestGame::pressedId))
                .contains("scoreAnObjective");

        int lead = test.game.getRevealedPublicObjectives().get("lead");
        AiPrompt scoring =
                prompt("score", PromptSource.PUBLIC, NOW, "FFCC_nekro_po_scoring_" + lead, "FFCC_nekro_po_scoring_0");
        assertThat(StrategyCardRules.resolvePrimary(test.context(card, scoring)).map(AiTestGame::pressedId))
                .contains("FFCC_nekro_po_scoring_" + lead);
    }

    // Following Imperial costs a strategy token and draws a secret, which is worth it while the seat has room for
    // more secrets.
    @Test
    void followsImperialToDrawASecret() {
        test.sol.addSC(IMPERIAL);
        test.game.setSCPlayed(IMPERIAL, true);
        test.nekro.setStrategicCC(2);
        test.game.setActivePlayerID(test.sol.getUserID());

        Optional<AiDecision> decision = StrategyCardRules.follow(test.context(imperialCard()));

        assertThat(decision.map(AiTestGame::pressedId)).contains("sc_draw_so");
    }

    // A full hand would only trade one secret for another, which costs a strategy token without bringing a point
    // closer.
    @Test
    void doesNotFollowImperialToSwapSecretsWithAFullHand() {
        test.sol.addSC(IMPERIAL);
        test.game.setSCPlayed(IMPERIAL, true);
        test.nekro.setStrategicCC(2);
        test.nekro.setSecret("survey");
        test.nekro.setSecret("prove_endurance");
        test.nekro.setSecret("dhw");

        assertThat(StrategyCardRules.follow(test.context(imperialCard()))).isEmpty();
    }

    @Test
    void doesNotFollowImperialWithoutAStrategyToken() {
        test.sol.addSC(IMPERIAL);
        test.game.setSCPlayed(IMPERIAL, true);
        test.nekro.setStrategicCC(0);

        assertThat(StrategyCardRules.follow(test.context(imperialCard()))).isEmpty();
    }

    private static AiPrompt leadershipCard() {
        return prompt("leadership", PromptSource.PUBLIC, NOW, "sc_no_follow_1", "leadershipGenerateCCButtons");
    }

    private void holdInfluencePlanets() {
        test.place("29", "201");
        test.place("24", "202");
        test.nekro.addPlanet("qucenn");
        test.nekro.addPlanet("rarron");
        test.nekro.addPlanet("meharxull");
    }

    private void someoneElsePlayed(int initiative) {
        test.sol.addSC(initiative);
        test.game.setSCPlayed(initiative, true);
        test.game.setActivePlayerID(test.sol.getUserID());
    }

    // Leadership's secondary costs no strategy token: every 3 influence spent buys a command token. Qucenn (1/2),
    // Rarron (0/3) and Mehar Xull (1/3) hold 8 influence, enough for 2 tokens, and none of them is worth more for
    // production than for influence.
    @Test
    void followsLeadershipToBuyTokensWithSpareInfluence() {
        holdInfluencePlanets();
        someoneElsePlayed(LEADERSHIP);
        AiTurnContext context = test.context(leadershipCard());

        Optional<AiDecision> decision = StrategyCardRules.follow(context);

        assertThat(decision.map(AiTestGame::pressedId)).contains("leadershipGenerateCCButtons");
        assertThat(PaymentRules.isPending(context)).isTrue();
        assertThat(test.memory.get("tokenGain|" + test.game.getRound()))
                .hasValueSatisfying(value -> assertThat(value).endsWith("|0|2"));
    }

    // The primary's three free tokens come with the same purchase, so the 8 spare influence adds two more.
    @Test
    void buysExtraTokensWithTheLeadershipPrimary() {
        holdInfluencePlanets();
        AiPrompt card = leadershipCard();
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), LEADERSHIP);
        AiTurnContext context = test.context(card);

        Optional<AiDecision> decision = StrategyCardRules.resolvePrimary(context);

        assertThat(decision.map(AiTestGame::pressedId)).contains("leadershipGenerateCCButtons");
        assertThat(PaymentRules.isPending(context)).isTrue();
        assertThat(test.memory.get("tokenGain|" + test.game.getRound()))
                .hasValueSatisfying(value -> assertThat(value).endsWith("|3|2"));
    }

    // Another payment is still open, so buying tokens now would overwrite it: the seat waits for it to close and is
    // free to follow once it has.
    @Test
    void waitsToFollowLeadershipWhileAnotherPaymentIsOpen() {
        holdInfluencePlanets();
        someoneElsePlayed(LEADERSHIP);
        AiTurnContext context = test.context(leadershipCard());
        PaymentRules.expectNothing(context, "a technology", PaymentRules.TECHNOLOGY_DONE);

        assertThat(StrategyCardRules.follow(context)).isEmpty();
        assertThat(StrategyCardRules.waitsToFollow(context, LEADERSHIP)).isTrue();

        PaymentRules.forget(context);
        assertThat(StrategyCardRules.waitsToFollow(context, LEADERSHIP)).isFalse();
        assertThat(StrategyCardRules.follow(context)).isPresent();
    }

    // A token gain whose prompt never arrived must not hold the seat back for the rest of the round.
    @Test
    void stopsWaitingOnATokenGainThatNeverArrived() {
        holdInfluencePlanets();
        someoneElsePlayed(LEADERSHIP);
        AiPrompt card = leadershipCard();
        assertThat(StrategyCardRules.follow(test.context(card))).isPresent();

        assertThat(StrategyCardRules.waitsToFollow(test.contextAt(NOW + MINUTE, card), LEADERSHIP))
                .isTrue();
        assertThat(StrategyCardRules.waitsToFollow(test.contextAt(NOW + 16 * MINUTE, card), LEADERSHIP))
                .isFalse();
    }

    private static AiPrompt leadershipGainPrompt(long created) {
        return prompt(
                "gain",
                PromptSource.AI_THREAD,
                created,
                java.util.List.of(
                        "increase_tactic_cc",
                        "increase_fleet_cc",
                        "increase_strategy_cc",
                        "FFCC_nekro_deleteButtons_leadership"),
                java.util.List.of(
                        "Gain 1 Tactic Token",
                        "Gain 1 Fleet Token",
                        "Gain 1 Strategy Token",
                        "Done Gaining Command Tokens"));
    }

    private static AiPrompt leadershipPaymentPrompt(long created) {
        return prompt(
                "pay",
                PromptSource.AI_THREAD,
                created,
                java.util.List.of("deleteButtons_leadership"),
                java.util.List.of("Done Exhausting Planets"));
    }

    // The primary's three tokens are free; the two bought with influence are only gained once that influence is paid.
    // Here the payment never went through, so after three tokens the seat closes the prompt.
    @Test
    void gainsOnlyTheFreeLeadershipTokensWhenThePurchaseWasNotPaid() {
        holdInfluencePlanets();
        AiPrompt card = leadershipCard();
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), LEADERSHIP);
        assertThat(StrategyCardRules.resolvePrimary(test.context(card))).isPresent();
        test.game.setStoredValue("originalCCsFornekro", test.nekro.getCCRepresentation());
        test.nekro.setTacticalCC(test.nekro.getTacticalCC() + 3);

        AiTurnContext afterThePaymentLapsed = test.contextAt(NOW + 11 * MINUTE, card, leadershipGainPrompt(NOW));

        assertThat(StrategyCardRules.gainTokens(afterThePaymentLapsed).map(AiTestGame::pressedId))
                .contains("FFCC_nekro_deleteButtons_leadership");
    }

    @Test
    void gainsTheBoughtLeadershipTokensOnceTheInfluenceIsPaid() {
        holdInfluencePlanets();
        AiPrompt card = leadershipCard();
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), LEADERSHIP);
        assertThat(StrategyCardRules.resolvePrimary(test.context(card))).isPresent();
        test.game.setStoredValue("originalCCsFornekro", test.nekro.getCCRepresentation());
        payThroughThePrompt(card);
        test.nekro.setTacticalCC(test.nekro.getTacticalCC() + 3);

        AiTurnContext gaining = test.context(card, leadershipGainPrompt(NOW));

        assertThat(StrategyCardRules.gainTokens(gaining).map(AiTestGame::pressedId))
                .hasValueSatisfying(id -> assertThat(id).startsWith("increase_"));
    }

    // Presses the payment prompt's spend buttons as the bot would see them, exhausting each planet it presses, until
    // the
    // seat closes the prompt.
    private void payThroughThePrompt(AiPrompt card) {
        AiPrompt payment = prompt(
                "pay",
                PromptSource.AI_THREAD,
                NOW,
                java.util.List.of(
                        "spend_qucenn_inf", "spend_rarron_inf", "spend_meharxull_inf", "deleteButtons_leadership"),
                java.util.List.of("Qucenn", "Rarron", "Mehar Xull", "Done Exhausting Planets"));
        for (int press = 0; press < 5; press++) {
            String id = PaymentRules.pay(test.context(card, payment))
                    .map(AiTestGame::pressedId)
                    .orElseThrow();
            if (id.startsWith("deleteButtons")) return;
            test.nekro.exhaustPlanet(org.apache.commons.lang3.StringUtils.substringBetween(id, "spend_", "_"));
        }
        throw new AssertionError("the payment never finished");
    }

    // A planet the seat meant to pay with was exhausted by something else before the payment prompt came: the payment
    // is short, so only the free tokens are gained.
    @Test
    void doesNotCountAPlanetExhaustedElsewhereAsPaid() {
        holdInfluencePlanets();
        AiPrompt card = leadershipCard();
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), LEADERSHIP);
        assertThat(StrategyCardRules.resolvePrimary(test.context(card))).isPresent();
        test.game.setStoredValue("originalCCsFornekro", test.nekro.getCCRepresentation());
        for (String planet : java.util.List.of("qucenn", "rarron", "meharxull")) test.nekro.exhaustPlanet(planet);
        assertThat(PaymentRules.pay(test.context(card, leadershipPaymentPrompt(NOW)))
                        .map(AiTestGame::pressedId))
                .contains("deleteButtons_leadership");
        test.nekro.setTacticalCC(test.nekro.getTacticalCC() + 3);

        assertThat(StrategyCardRules.gainTokens(test.context(card, leadershipGainPrompt(NOW)))
                        .map(AiTestGame::pressedId))
                .contains("FFCC_nekro_deleteButtons_leadership");
    }

    private static AiPrompt technologyCardForNekro() {
        return prompt(
                "tech",
                PromptSource.PUBLIC,
                NOW,
                "sc_no_follow_7",
                "acquireATechWithSC_first",
                "FFCC_nekro_nekroFollowTech");
    }

    // Nekro cannot research, so following Technology gains 3 command tokens instead, for the usual strategy token and
    // 4 resources (Mordai II pays). The resources are paid through the bot's own "Exhaust Planets" prompt, which the
    // seat opens before gaining the tokens.
    @Test
    void followsTechnologyForPropagationTokensAndPaysForThem() {
        test.nekroHome();
        someoneElsePlayed(TECHNOLOGY);
        AiPrompt card = technologyCardForNekro();
        AiTurnContext following = test.context(card);

        assertThat(StrategyCardRules.follow(following).map(AiTestGame::pressedId))
                .contains("FFCC_nekro_nekroFollowTech");
        assertThat(PaymentRules.isPending(following)).isTrue();

        AiPrompt openPayment = prompt("exhaust", PromptSource.AI_THREAD, NOW, "nekroTechExhaust");
        assertThat(StrategyCardRules.gainTokens(test.context(card, openPayment)).map(AiTestGame::pressedId))
                .contains("nekroTechExhaust");
    }

    @Test
    void doesNotFollowTechnologyForPropagationWithoutTheResources() {
        someoneElsePlayed(TECHNOLOGY);

        assertThat(StrategyCardRules.follow(test.context(technologyCardForNekro())))
                .isEmpty();
    }

    @Test
    void doesNotFollowLeadershipWithoutInfluenceToSpare() {
        test.nekroHome();
        someoneElsePlayed(LEADERSHIP);

        assertThat(StrategyCardRules.follow(test.context(leadershipCard()))).isEmpty();
    }

    // Sway the Council takes 8 influence, all the seat has, so buying tokens would cost the objective. Scoring needs
    // the home system, which adds only resources.
    @Test
    void keepsInfluenceForSwayTheCouncilInsteadOfFollowingLeadership() {
        test.nekroHome();
        holdInfluencePlanets();
        someoneElsePlayed(LEADERSHIP);
        test.game
                .getRevealedPublicObjectives()
                .put("sway_council", test.game.getRevealedPublicObjectives().size() + 1);

        assertThat(StrategyCardRules.follow(test.context(leadershipCard()))).isEmpty();
    }

    private static AiPrompt constructionCard() {
        return prompt(
                "construction",
                PromptSource.PUBLIC,
                NOW,
                "sc_no_follow_4",
                "construction_pds",
                "construction_spacedock");
    }

    // Build Defenses needs 4 structures. A follow places one, so it is only worth a strategy token once the seat is
    // close enough for the objective to come within reach.
    @Test
    void followsConstructionOnlyWhenAStructureObjectiveIsWithinReach() {
        Tile home = test.nekroHome();
        test.nekro.setStrategicCC(2);
        test.nekro.setTacticalCC(3);
        someoneElsePlayed(CONSTRUCTION);
        test.game
                .getRevealedPublicObjectives()
                .put("build_defenses", test.game.getRevealedPublicObjectives().size() + 1);
        assertThat(StrategyCardRules.follow(test.context(constructionCard()))).isEmpty();

        test.units(home, "mordaiii", test.nekro, UnitType.Pds, 1);
        assertThat(StrategyCardRules.follow(test.context(constructionCard()))).isPresent();
    }

    // Once the research list arrives in its thread, it picks the most valuable technology and expects the payment
    // prompt that follows, even for a free tech, so that prompt gets closed.
    @Test
    void picksTheMostValuableTechnologyFromTheList() {
        Player sol = test.sol;
        AiPrompt card = prompt("tech", PromptSource.PUBLIC, NOW, "sc_no_follow_7", "acquireATechWithSC_first");
        test.game.setActivePlayerID(sol.getUserID());
        test.memoryOf(sol).put("scPlayed|" + test.contextFor(sol, Set.of(), NOW).turnKey(), String.valueOf(TECHNOLOGY));
        AiTurnContext beforeList = test.contextFor(sol, Set.of(), NOW, card);
        assertThat(StrategyCardRules.resolvePrimary(beforeList).map(AiTestGame::pressedId))
                .contains("acquireATechWithSC_first");

        AiPrompt list = prompt("list", PromptSource.AI_THREAD, NOW, "FFCC_sol_getTech_det", "FFCC_sol_getTech_gd");
        AiTurnContext withList = test.contextFor(sol, Set.of(), NOW, card, list);

        assertThat(StrategyCardRules.chooseTechnology(withList).map(AiTestGame::pressedId))
                .contains("FFCC_sol_getTech_gd");
        assertThat(PaymentRules.isPending(withList)).isTrue();
    }

    @Test
    void neverResearchesAsNekro() {
        AiPrompt card = prompt("tech", PromptSource.PUBLIC, NOW, "sc_no_follow_7", "acquireATechWithSC_first");
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), TECHNOLOGY);

        assertThat(StrategyCardRules.resolvePrimary(test.context(card))).isEmpty();
    }

    // Warfare's primary is a tactical action that places no command token, so the AI can activate its home system a
    // second time to produce again. It plans first, then presses the card's button and the begin button, then picks
    // the system like any tactical action.
    @Test
    void usesWarfareToProduceAgainAtHome() {
        ti4.game.Tile home = test.nekroHome();
        home.addCC(ti4.image.Mapper.getCCID(test.nekro.getColor()));
        test.nekro.setTg(10);
        AiPrompt card = prompt("warfare", PromptSource.PUBLIC, NOW, "sc_no_follow_6", "primaryOfTeWarfare");
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), 6);

        assertThat(StrategyCardRules.resolvePrimary(test.context(card)).map(AiTestGame::pressedId))
                .contains("primaryOfTeWarfare");
        assertThat(StrategyCardRules.resolvePrimary(test.context(card))).containsInstanceOf(AiDecision.Wait.class);

        AiPrompt begin = prompt("begin", PromptSource.PUBLIC, NOW, "FFCC_nekro_beginTacticalTeWarfare");
        assertThat(StrategyCardRules.resolvePrimary(test.context(card, begin)).map(AiTestGame::pressedId))
                .contains("FFCC_nekro_beginTacticalTeWarfare");

        AiPrompt picker =
                prompt("picker", PromptSource.PUBLIC, NOW, "ringTile_" + AiTestGame.HOME, "getTilesThisFarAway_1");
        assertThat(StrategyCardRules.resolvePrimary(test.context(card, picker)).map(AiTestGame::pressedId))
                .contains("ringTile_" + AiTestGame.HOME);
    }

    private static final int DIPLOMACY = 2;
    private static final int TRADE = 5;
    private static final int WARFARE = 6;

    private static AiPrompt diplomacyCard() {
        return prompt("diplomacy", PromptSource.PUBLIC, NOW, "sc_follow_2", "sc_no_follow_2", "diploRefresh2");
    }

    private static AiPrompt warfareCard() {
        return prompt("warfare", PromptSource.PUBLIC, NOW, "sc_follow_6", "warfareBuild", "sc_no_follow_6");
    }

    private static AiPrompt tradeCard() {
        return prompt("trade", PromptSource.PUBLIC, NOW, "sc_trade_follow", "sc_no_follow_5", "sc_refresh");
    }

    // Rarron (0/3) and Mehar Xull (1/3) are exhausted: readying both gives back 6 influence.
    private void exhaustedInfluencePlanets() {
        holdInfluencePlanets();
        test.nekro.exhaustPlanet("rarron");
        test.nekro.exhaustPlanet("meharxull");
        test.nekro.setStrategicCC(2);
        test.nekro.setTacticalCC(3);
    }

    private String readyPlan() {
        return test.memory.get("readyPlan|" + test.game.getRound()).orElse("");
    }

    // Diplomacy's secondary readies two exhausted planets. With Leadership still to come, Rarron and Mehar Xull's 6
    // influence (plus Qucenn's 2) buy 2 command tokens, so the strategy token is worth it and those two are planned.
    @Test
    void followsDiplomacyToReadyPlanetsItWillSpend() {
        exhaustedInfluencePlanets();
        test.sol.addSC(LEADERSHIP);
        someoneElsePlayed(DIPLOMACY);

        assertThat(StrategyCardRules.follow(test.context(diplomacyCard())).map(AiTestGame::pressedId))
                .contains("diploRefresh2");
        assertThat(test.memory.get("readyPlanets|" + test.game.getRound())).isPresent();
        assertThat(readyPlan().split(",")).containsExactlyInAnyOrder("rarron", "meharxull");
    }

    // The same planets are worth 6 influence, but nothing this round would spend it: no strategy token for that.
    @Test
    void doesNotFollowDiplomacyWithoutAUseForThePlanets() {
        exhaustedInfluencePlanets();
        someoneElsePlayed(DIPLOMACY);

        assertThat(StrategyCardRules.follow(test.context(diplomacyCard()))).isEmpty();
    }

    @Test
    void doesNotFollowDiplomacyWithNothingWorthReadying() {
        holdInfluencePlanets();
        test.nekro.setStrategicCC(2);
        test.nekro.setTacticalCC(3);
        someoneElsePlayed(DIPLOMACY);

        assertThat(StrategyCardRules.follow(test.context(diplomacyCard()))).isEmpty();
    }

    // Readying planets in the middle of a payment would upset it: the seat neither follows nor declines Diplomacy
    // until the payment closes.
    @Test
    void waitsToFollowDiplomacyWhileAPaymentIsOpen() {
        exhaustedInfluencePlanets();
        test.sol.addSC(LEADERSHIP);
        someoneElsePlayed(DIPLOMACY);
        AiTurnContext context = test.context(diplomacyCard());
        PaymentRules.expectNothing(context, "a technology", PaymentRules.TECHNOLOGY_DONE);

        assertThat(StrategyCardRules.follow(context)).isEmpty();
        assertThat(StrategyCardRules.waitsToFollow(context, DIPLOMACY)).isTrue();

        PaymentRules.forget(context);
        assertThat(StrategyCardRules.waitsToFollow(context, DIPLOMACY)).isFalse();
        assertThat(StrategyCardRules.follow(context).map(AiTestGame::pressedId)).contains("diploRefresh2");
    }

    // With more than 24 exhausted planets the bot splits its ready prompt over several messages. The planned planets
    // are pressed in whichever part they are, before Lirtaiv (2/3), which is worth more but not planned, and the Done
    // button comes last.
    @Test
    void readiesThePlannedPlanetsFromEveryPartOfASplitPrompt() {
        exhaustedInfluencePlanets();
        test.place("35", "203");
        test.nekro.addPlanet("lirtaiv");
        test.nekro.exhaustPlanet("lirtaiv");
        int round = test.game.getRound();
        test.memory.put("readyPlanets|" + round, NOW + "|2");
        test.memory.put("readyPlan|" + round, "rarron,meharxull");
        AiPrompt firstPart = prompt(
                "ready-1", PromptSource.AI_THREAD, NOW, "FFCC_nekro_refresh_lirtaiv", "FFCC_nekro_refresh_rarron");
        AiPrompt lastPart = prompt(
                "ready-2", PromptSource.AI_THREAD, NOW, "FFCC_nekro_refresh_meharxull", "deleteButtons_diplomacy");

        assertThat(StrategyCardRules.readyPlanets(test.context(firstPart, lastPart))
                        .map(AiTestGame::pressedId))
                .contains("FFCC_nekro_refresh_rarron");

        test.nekro.refreshPlanet("rarron");
        firstPart = prompt("ready-1", PromptSource.AI_THREAD, NOW, "FFCC_nekro_refresh_lirtaiv");
        assertThat(StrategyCardRules.readyPlanets(test.context(firstPart, lastPart))
                        .map(AiTestGame::pressedId))
                .contains("FFCC_nekro_refresh_meharxull");

        test.nekro.refreshPlanet("meharxull");
        lastPart = prompt("ready-2", PromptSource.AI_THREAD, NOW, "deleteButtons_diplomacy");
        assertThat(StrategyCardRules.readyPlanets(test.context(firstPart, lastPart))
                        .map(AiTestGame::pressedId))
                .contains("deleteButtons_diplomacy");
        assertThat(test.memory.get("readyPlanets|" + round)).isEmpty();
    }

    // After a memory wipe the plan is gone; the seat chooses again from the planets the prompt offers, with the same
    // test as the follow: Leadership is still to come, so an influence planet goes first.
    @Test
    void choosesThePlanetsAgainWhenThePlanIsLost() {
        exhaustedInfluencePlanets();
        test.sol.addSC(LEADERSHIP);
        test.place("38", "203");
        test.nekro.addPlanet("abyz");
        test.nekro.exhaustPlanet("abyz");
        test.memory.put("readyPlanets|" + test.game.getRound(), NOW + "|2");
        AiPrompt ready = prompt(
                "ready",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_nekro_refresh_abyz",
                "FFCC_nekro_refresh_rarron",
                "FFCC_nekro_refresh_meharxull",
                "deleteButtons_diplomacy");

        assertThat(StrategyCardRules.readyPlanets(test.context(ready)).map(AiTestGame::pressedId))
                .hasValueSatisfying(
                        id -> assertThat(id).isIn("FFCC_nekro_refresh_rarron", "FFCC_nekro_refresh_meharxull"));
        assertThat(readyPlan().split(",")).containsExactlyInAnyOrder("rarron", "meharxull");
    }

    // Diplomacy's primary costs no strategy token, so it readies planets even when nothing will spend them, and plans
    // the 2 most valuable: Lirtaiv (2/3) and Mehar Xull (1/3).
    @Test
    void primaryReadiesTheMostValuablePairEvenWithoutAUse() {
        exhaustedInfluencePlanets();
        test.place("35", "203");
        test.nekro.addPlanet("lirtaiv");
        test.nekro.exhaustPlanet("lirtaiv");
        AiPrompt card = diplomacyCard();
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), DIPLOMACY);

        assertThat(StrategyCardRules.resolvePrimary(test.context(card)).map(AiTestGame::pressedId))
                .contains("diploRefresh2");
        assertThat(readyPlan().split(",")).containsExactlyInAnyOrder("lirtaiv", "meharxull");
    }

    // Imperial still to come is worth more than Diplomacy now: with one strategy token the seat keeps it for
    // Imperial, with two it can afford both.
    @Test
    void savesAStrategyTokenForImperialStillToBePlayed() {
        exhaustedInfluencePlanets();
        test.sol.addSC(IMPERIAL);
        test.sol.addSC(LEADERSHIP);
        someoneElsePlayed(DIPLOMACY);
        test.nekro.setStrategicCC(1);
        assertThat(StrategyCardRules.follow(test.context(diplomacyCard()))).isEmpty();

        test.nekro.setStrategicCC(2);
        assertThat(StrategyCardRules.follow(test.context(diplomacyCard())).map(AiTestGame::pressedId))
                .contains("diploRefresh2");
    }

    // When several cards wait for an answer, the most valuable follow goes first.
    @Test
    void followsImperialBeforeDiplomacy() {
        exhaustedInfluencePlanets();
        someoneElsePlayed(DIPLOMACY);
        someoneElsePlayed(IMPERIAL);

        assertThat(StrategyCardRules.follow(test.context(diplomacyCard(), imperialCard()))
                        .map(AiTestGame::pressedId))
                .contains("sc_draw_so");
    }

    // Warfare's secondary produces at home without placing a command token there. It presses the card's build
    // button, then places the planned units and closes the production prompt.
    @Test
    void followsWarfareToProduceAtHome() {
        Tile home = test.nekroHome();
        test.nekro.setTg(10);
        test.nekro.setStrategicCC(2);
        test.nekro.setTacticalCC(3);
        someoneElsePlayed(WARFARE);
        ti4.ai.tactical.ProductionPlanner.BuildPlan plan =
                ti4.ai.tactical.ProductionPlanner.plan(test.game, test.nekro, home);
        assertThat(plan.isEmpty()).isFalse();

        assertThat(StrategyCardRules.follow(test.context(warfareCard())).map(AiTestGame::pressedId))
                .contains("warfareBuild");

        String place = "FFCC_nekro_" + plan.orders().getFirst().handlerId();
        String done = "FFCC_nekro_deleteButtons_warfare_" + home.getPosition();
        AiPrompt production = prompt(
                "produce",
                PromptSource.AI_THREAD,
                NOW,
                java.util.List.of(place, done),
                java.util.List.of("Produce", "Done Producing Units"));
        assertThat(StrategyCardRules.buildWithWarfare(test.context(production)).map(AiTestGame::pressedId))
                .contains(place);
    }

    @Test
    void doesNotFollowWarfareWithoutAHomeSpaceDock() {
        test.place("08", AiTestGame.HOME);
        test.nekro.setTg(10);
        test.nekro.setStrategicCC(2);
        test.nekro.setTacticalCC(3);
        someoneElsePlayed(WARFARE);

        assertThat(StrategyCardRules.follow(test.context(warfareCard()))).isEmpty();
    }

    // An AI holding Trade lets everyone else replenish for free, and says so once.
    @Test
    void announcesFreeTradeFollowsForEveryoneElse() {
        AiPrompt card = prompt("trade", PromptSource.PUBLIC, NOW, "sc_trade_follow", "sc_no_follow_5");
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), TRADE);

        assertThat(StrategyCardRules.resolvePrimary(test.context(card))).containsInstanceOf(AiDecision.Announce.class);
        assertThat(StrategyCardRules.resolvePrimary(test.context(card))).isEmpty();
    }

    // Another AI's Trade is free to follow when its terms are worth it: Nekro (3 commodities, its own Trade Agreement
    // in hand) is not Sol's neighbour, so it owes 1 debt for 3 new commodities (0.3 x 3 - 0.8 = 0.1). It replenishes
    // without spending a strategy token. Without its Trade Agreement the replenish would only feed whoever holds it.
    @Test
    void replenishesForFreeWhenAnAiHoldsTrade() {
        test.nekro.setStrategicCC(0);
        test.nekro.setCommoditiesTotal(3);
        test.nekro.setCommodities(0);
        someoneElsePlayed(TRADE);
        assertThat(StrategyCardRules.follow(test.context(tradeCard()))).isEmpty();

        test.nekro.setPromissoryNote("black_ta", 1);
        assertThat(StrategyCardRules.follow(test.context(tradeCard())).map(AiTestGame::pressedId))
                .contains("sc_refresh");
    }

    // A human Trade holder has not offered a free follow, and 3 new commodities are not worth a strategy token.
    @Test
    void doesNotFollowAHumansTrade() {
        AiTestGame human = new AiTestGame();
        human.aiIsActive("action");
        human.nekro.setStrategicCC(3);
        human.nekro.setTacticalCC(3);
        human.nekro.setCommoditiesTotal(3);
        human.nekro.setCommodities(0);
        human.sol.addSC(TRADE);
        human.game.setSCPlayed(TRADE, true);

        assertThat(StrategyCardRules.follow(human.context(tradeCard()))).isEmpty();
    }

    private static final int POLITICS = 3;

    // "Draw 2 Action Cards" follows Politics, takes the strategy token and draws in one press.
    private static AiPrompt politicsCard() {
        return prompt("politics", PromptSource.PUBLIC, NOW, "sc_follow_3", "sc_no_follow_3", "sc_ac_draw");
    }

    private void holdActionCards(int cards) {
        for (int card = 1; card <= cards; card++) test.nekro.setActionCard("sabo" + card, card);
    }

    private void politicsPlayed(int strategyTokens) {
        someoneElsePlayed(POLITICS);
        test.nekro.setStrategicCC(strategyTokens);
        test.nekro.setTacticalCC(3);
    }

    // With 3 cards in hand, Politics' 2 complete Form a Spy Network's 5. A point is at stake, so the seat follows with
    // its only strategy token even though Imperial, a better card, is still to come.
    @Test
    void followsPoliticsWithItsLastTokenWhenItCompletesFormASpyNetwork() {
        test.nekro.setSecret("fsn");
        holdActionCards(3);
        test.sol.addSC(IMPERIAL);
        politicsPlayed(1);

        assertThat(StrategyCardRules.follow(test.context(politicsCard())).map(AiTestGame::pressedId))
                .contains("sc_ac_draw");
    }

    // A revealed Build Defenses is one structure short, so Construction is worth following too. Politics normally
    // ranks below it, but moves up to just after Imperial while its 2 cards complete Form a Spy Network.
    @Test
    void ranksPoliticsRightAfterImperialWhileItCompletesFormASpyNetwork() {
        Tile home = test.nekroHome();
        test.units(home, "mordaiii", test.nekro, UnitType.Pds, 1);
        test.game
                .getRevealedPublicObjectives()
                .put("build_defenses", test.game.getRevealedPublicObjectives().size() + 1);
        someoneElsePlayed(CONSTRUCTION);
        test.nekro.setSecret("fsn");
        holdActionCards(3);
        politicsPlayed(2);

        assertThat(StrategyCardRules.follow(test.context(constructionCard(), politicsCard()))
                        .map(AiTestGame::pressedId))
                .contains("sc_ac_draw");

        test.nekro.getActionCards().remove("sabo3");
        assertThat(StrategyCardRules.follow(test.context(constructionCard(), politicsCard()))
                        .map(AiTestGame::pressedId))
                .hasValueSatisfying(id -> assertThat(id).startsWith("construction_"));
    }

    // Without Form a Spy Network, Politics is worth a strategy token only as a spare: 3 tokens leave 2 after it.
    @Test
    void followsPoliticsWithSpareTokensAndRoomForTwoCards() {
        politicsPlayed(3);

        assertThat(StrategyCardRules.follow(test.context(politicsCard())).map(AiTestGame::pressedId))
                .contains("sc_ac_draw");
    }

    // One spare token is not enough for extra cards: alone, or as the second of two when the other is kept for
    // Imperial still to come.
    @Test
    void keepsItsOnlySpareTokenInsteadOfFollowingPolitics() {
        politicsPlayed(1);
        assertThat(StrategyCardRules.follow(test.context(politicsCard()))).isEmpty();

        test.sol.addSC(IMPERIAL);
        test.nekro.setStrategicCC(2);
        assertThat(StrategyCardRules.follow(test.context(politicsCard()))).isEmpty();
    }

    // The bot draws the cards even for a player with no strategy token left, so the seat must not press it.
    @Test
    void neverFollowsPoliticsWithoutAStrategyToken() {
        test.nekro.setSecret("fsn");
        holdActionCards(3);
        politicsPlayed(0);

        assertThat(StrategyCardRules.follow(test.context(politicsCard()))).isEmpty();
    }

    // 6 cards of 7: one more would already be one too many.
    @Test
    void doesNotFollowPoliticsWithRoomForOneCard() {
        holdActionCards(6);
        politicsPlayed(3);

        assertThat(StrategyCardRules.follow(test.context(politicsCard()))).isEmpty();
    }

    // Holding Form a Spy Network with 1 card, a single spare token is worth drawing towards it. Under Sanctions the
    // hand limit is 3, so the secret can never be completed and the usual 2-token rule applies.
    @Test
    void ignoresFormASpyNetworkUnderSanctions() {
        test.nekro.setSecret("fsn");
        holdActionCards(1);
        politicsPlayed(1);
        assertThat(StrategyCardRules.follow(test.context(politicsCard())).map(AiTestGame::pressedId))
                .contains("sc_ac_draw");

        test.game.addLaw("sanctions", null);
        assertThat(StrategyCardRules.follow(test.context(politicsCard()))).isEmpty();
    }

    // Hold No Action Cards wants an empty hand.
    @Test
    void neverFollowsPoliticsWhileItWantsAnEmptyHand() {
        test.nekro.setSecret("dont_fsn");
        politicsPlayed(3);

        assertThat(StrategyCardRules.follow(test.context(politicsCard()))).isEmpty();
    }

    // Hold No Action Cards means Politics is never followed, even while its 2 cards would complete Form a Spy Network.
    // So no strategy token is kept for Sol's Politics still to come, and Technology gets the only one.
    @Test
    void keepsNoTokenForPoliticsWhileItWantsAnEmptyHand() {
        test.nekroHome();
        test.nekro.setSecret("fsn");
        test.nekro.setSecret("dont_fsn");
        holdActionCards(3);
        test.sol.addSC(POLITICS);
        someoneElsePlayed(TECHNOLOGY);
        test.nekro.setStrategicCC(1);
        test.nekro.setTacticalCC(3);

        assertThat(StrategyCardRules.follow(test.context(technologyCardForNekro()))
                        .map(AiTestGame::pressedId))
                .contains("FFCC_nekro_nekroFollowTech");
    }

    // Technology waits while a payment is open, but it is still the better card. Politics, played meanwhile, would
    // draw towards Form a Spy Network with the only strategy token; that token stays with Technology, which the seat
    // follows once the payment closes.
    @Test
    void keepsItsTokenForABetterCardWaitingOnAPayment() {
        test.nekroHome();
        someoneElsePlayed(TECHNOLOGY);
        test.nekro.setSecret("fsn");
        holdActionCards(1);
        politicsPlayed(1);
        AiTurnContext context = test.context(technologyCardForNekro(), politicsCard());
        PaymentRules.expectNothing(context, "a technology", PaymentRules.TECHNOLOGY_DONE);

        assertThat(StrategyCardRules.waitsToFollow(context, TECHNOLOGY)).isTrue();
        assertThat(StrategyCardRules.follow(context)).isEmpty();

        PaymentRules.forget(context);
        assertThat(StrategyCardRules.follow(context).map(AiTestGame::pressedId)).contains("FFCC_nekro_nekroFollowTech");
    }

    // Sol still holds Politics. While its 2 cards would complete Form a Spy Network the seat keeps its only token for
    // it instead of following Construction; with a card fewer the secret is out of reach and Construction gets the
    // token.
    @Test
    void savesATokenForPoliticsOnlyWhenItCompletesFormASpyNetwork() {
        Tile home = test.nekroHome();
        test.units(home, "mordaiii", test.nekro, UnitType.Pds, 1);
        test.game
                .getRevealedPublicObjectives()
                .put("build_defenses", test.game.getRevealedPublicObjectives().size() + 1);
        test.sol.addSC(POLITICS);
        someoneElsePlayed(CONSTRUCTION);
        test.nekro.setStrategicCC(1);
        test.nekro.setTacticalCC(3);
        test.nekro.setSecret("fsn");
        holdActionCards(3);

        assertThat(StrategyCardRules.follow(test.context(constructionCard()))).isEmpty();

        test.nekro.getActionCards().remove("sabo3");
        assertThat(StrategyCardRules.follow(test.context(constructionCard()))).isPresent();
    }
}
