package ti4.ai.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.testUtils.BaseTi4Test;

// The instant exploration cards that ask the explorer for a choice. The AI weighs each option in the same currency
// (trade good 1, command token 2, action card 1, an own commodity about a third) and presses the better one.
class CardRulesTest extends BaseTi4Test {

    private static final String DECLINE = "decline_explore";

    private AiTestGame test;
    private Tile site;
    private String position;

    // Nekro has just taken Tequran (a hazardous 2/0 planet) in the system next to its home.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        site = test.place("28", position);
        test.nekro.addPlanet("tequran");
        test.aiIsActive("action");
    }

    // Nothing can reach Tequran and nothing is left to claim, so the infantry costs 0.7 and a command token is
    // worth 2: it is removed.
    @Test
    void volatileFuelSourceTakesTheTokenOnASafePlanet() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 1);

        assertThat(pressedId(next(volatile_("resolveVolatileInf_tequran")).orElseThrow()))
                .isEqualTo("resolveVolatileInf_tequran");
    }

    // A Sol destroyer next door could take the planet anyway, and would pay a token and risk retaliation for it: the
    // infantry is its only ground force, yet 0.7 plus a small share of the planet's stake is far below a token.
    @Test
    void volatileFuelSourceTakesTheTokenEvenWithAnEnemyNextDoor() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 1);
        solDestroyerNextDoor();

        assertThat(pressedId(next(volatile_("resolveVolatileInf_tequran")).orElseThrow()))
                .isEqualTo("resolveVolatileInf_tequran");
    }

    // The infantry is kept when it is needed to claim a planet worth more: Lodor, a carrier away, and no other
    // ground force to take it.
    @Test
    void volatileFuelSourceKeepsTheInfantryNeededForExpansion() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 1);
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", test.nekro, UnitType.Carrier, 1);
        test.place("26", otherNeighbourOfHome());
        test.nekro.addPlanet("torkan");

        assertThat(pressedId(next(volatile_("resolveVolatileInf_tequran")).orElseThrow()))
                .isEqualTo(DECLINE);
    }

    // Another infantry stays to hold the planet, so removing one is fine even with an enemy next door.
    @Test
    void volatileFuelSourceRemovesAnInfantryThatIsNotTheLastForce() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 2);
        solDestroyerNextDoor();

        assertThat(pressedId(next(volatile_("resolveVolatileInf_tequran")).orElseThrow()))
                .isEqualTo("resolveVolatileInf_tequran");
    }

    // A mech on the planet gives the token for free, whatever the danger.
    @Test
    void volatileFuelSourceUsesAMechWhenThereIsOne() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 1);
        test.units(site, "tequran", test.nekro, UnitType.Mech, 1);
        solDestroyerNextDoor();

        assertThat(pressedId(next(volatile_("resolveVolatileMech_tequran", "resolveVolatileInf_tequran"))
                        .orElseThrow()))
                .isEqualTo("resolveVolatileMech_tequran");
    }

    // With every command token on the board or in a pool, there is no token to gain.
    @Test
    void volatileFuelSourceDeclinesWithoutATokenInReinforcements() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 2);
        test.nekro.setTacticalCC(10);
        test.nekro.setFleetCC(3);
        test.nekro.setStrategicCC(3);

        assertThat(pressedId(next(volatile_("resolveVolatileInf_tequran")).orElseThrow()))
                .isEqualTo(DECLINE);
    }

    // A planet left to claim makes the infantry worth keeping: a trade good (1) does not beat 0.7 plus half the
    // value of the planet (Lodor) it could still take.
    @Test
    void coreMineDeclinesWhileTheInfantryCanStillClaimAPlanet() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 2);
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.place("26", otherNeighbourOfHome());

        assertThat(pressedId(next(coreMine("resolveCoreMineInf_tequran")).orElseThrow()))
                .isEqualTo(DECLINE);
    }

    // With nothing left to claim, a spare infantry is cheap enough for a trade good.
    @Test
    void coreMineTakesTheTradeGoodWhenNothingIsLeftToClaim() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 2);

        assertThat(pressedId(next(coreMine("resolveCoreMineInf_tequran")).orElseThrow()))
                .isEqualTo("resolveCoreMineInf_tequran");
    }

    // A mech makes the trade good free, even while there are planets to claim.
    @Test
    void coreMineUsesAMech() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 1);
        test.units(site, "tequran", test.nekro, UnitType.Mech, 1);
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.place("26", otherNeighbourOfHome());

        assertThat(pressedId(next(coreMine("resolveCoreMineMech_tequran", "resolveCoreMineInf_tequran"))
                        .orElseThrow()))
                .isEqualTo("resolveCoreMineMech_tequran");
    }

    // Tequran starts exhausted and the AI still builds this round (a dock with something to buy), so readying its 2
    // resources is worth more than the infantry that pays for it.
    @Test
    void expeditionReadiesAPlanetItWillSpend() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 2);
        test.nekro.exhaustPlanet("tequran");
        spendEverythingAtHome();

        assertThat(pressedId(next(expedition("resolveExpeditionInf_tequran")).orElseThrow()))
                .isEqualTo("resolveExpeditionInf_tequran");
    }

    // Pre-Fab Arcologies readies the planet by itself, so the card adds nothing.
    @Test
    void expeditionDeclinesWithPreFabArcologies() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 2);
        test.nekro.exhaustPlanet("tequran");
        test.nekro.addTech("pfa");
        spendEverythingAtHome();

        assertThat(pressedId(next(expedition("resolveExpeditionInf_tequran")).orElseThrow()))
                .isEqualTo(DECLINE);
    }

    // A planet that is not exhausted has nothing to ready.
    @Test
    void expeditionDeclinesForAReadyPlanet() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 2);
        spendEverythingAtHome();

        assertThat(pressedId(next(expedition("resolveExpeditionInf_tequran")).orElseThrow()))
                .isEqualTo(DECLINE);
    }

    // Zohbat (3/1) with its only infantry. With Technology still to be played and a strategy token to follow it,
    // 3 resources readied now will be spent, which beats the infantry.
    @Test
    void expeditionReadiesAPlanetItCanSpendOnAStrategyCard() {
        exhaustedZohbatWithOneInfantry();
        test.sol.addSC(7);
        test.nekro.setStrategicCC(1);

        assertThat(pressedId(next(expedition("resolveExpeditionInf_zohbat")).orElseThrow()))
                .isEqualTo("resolveExpeditionInf_zohbat");
    }

    // The same Zohbat with nothing left to spend it on this round (no tactic token to produce with, no card to follow,
    // no spend objective): the readied resources would sit unused, so the infantry stays.
    @Test
    void expeditionKeepsTheInfantryWhenTheResourcesWouldGoUnused() {
        exhaustedZohbatWithOneInfantry();
        test.nekro.setTacticalCC(0);

        assertThat(pressedId(next(expedition("resolveExpeditionInf_zohbat")).orElseThrow()))
                .isEqualTo(DECLINE);
    }

    // Even after passing, a readied Zohbat counts when it completes a spend objective in the status phase: Erect a
    // Monument needs 8 resources, and Mordai II (4) and a trade good fall 3 short.
    @Test
    void expeditionReadiesAPlanetThatCompletesASpendObjective() {
        exhaustedZohbatWithOneInfantry();
        test.nekro.getPlanets().forEach(test.nekro::exhaustPlanet);
        test.nekro.refreshPlanet("mordaiii");
        test.nekro.setTg(1);
        test.nekro.setTacticalCC(0);
        test.nekro.setPassed(true);
        test.game.getRevealedPublicObjectives().put("monument", 1);

        assertThat(pressedId(next(expedition("resolveExpeditionInf_zohbat")).orElseThrow()))
                .isEqualTo("resolveExpeditionInf_zohbat");
    }

    private void exhaustedZohbatWithOneInfantry() {
        Tile zohbat = test.place("30", otherNeighbourOfHome());
        test.nekro.addPlanet("zohbat");
        test.units(zohbat, "zohbat", test.nekro, UnitType.Infantry, 1);
        test.nekro.exhaustPlanet("zohbat");
    }

    // Once the AI has passed, a ready planet is worth nothing until the status phase readies it anyway, so even a mech
    // does not bother.
    @Test
    void expeditionIsWorthNothingAfterPassing() {
        Tile lazar = test.place("31", otherNeighbourOfHome());
        test.nekro.addPlanet("lazar");
        test.units(lazar, "lazar", test.nekro, UnitType.Infantry, 2);
        test.units(lazar, "lazar", test.nekro, UnitType.Mech, 1);
        test.nekro.exhaustPlanet("lazar");
        test.nekro.setPassed(true);

        assertThat(pressedId(next(expedition("resolveExpeditionMech_lazar", "resolveExpeditionInf_lazar"))
                        .orElseThrow()))
                .isEqualTo(DECLINE);
    }

    // Local Fabricators: a mech (2) for a commodity (a third) beats a commodity.
    @Test
    void localFabricatorsPlacesAMech() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 1);
        test.nekro.setCommodities(2);

        assertThat(pressedId(next(localFabricators()).orElseThrow())).isEqualTo("resolveLocalFab_tequran");
    }

    // With four mechs already on the board there is none left in reinforcements, and the bot would not stop it.
    @Test
    void localFabricatorsGainsACommodityWithoutMechsInReinforcements() {
        test.units(site, "tequran", test.nekro, UnitType.Mech, 4);
        test.nekro.setCommodities(1);

        assertThat(pressedId(next(localFabricators()).orElseThrow())).isEqualTo("gain_1_comms");
    }

    // Units cannot be placed on a planet with the Demilitarized Zone.
    @Test
    void localFabricatorsGainsACommodityOnADemilitarizedPlanet() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 1);
        test.nekro.setCommodities(1);
        site.getUnitHolderFromPlanet("tequran").addToken("attachment_dmz.png");

        assertThat(pressedId(next(localFabricators()).orElseThrow())).isEqualTo("gain_1_comms");
    }

    // Functioning Base: a commodity (a third) buys an action card (1), unless the hand is full.
    @Test
    void functioningBaseDrawsAnActionCardUnlessTheHandIsFull() {
        test.nekro.setCommodities(2);
        assertThat(pressedId(next(functioningBase()).orElseThrow())).isEqualTo("comm_for_AC");

        for (int card = 0; card < 7; card++) test.nekro.setActionCard("card" + card);
        assertThat(pressedId(next(functioningBase()).orElseThrow())).isEqualTo("gain_1_comms");
    }

    // With nothing to pay with there is no action card to buy.
    @Test
    void functioningBaseGainsACommodityWhenItCannotPay() {
        assertThat(pressedId(next(functioningBase()).orElseThrow())).isEqualTo("gain_1_comms");
    }

    // With the hand full and no room for another commodity, neither option is worth anything, but the card has no
    // decline button, so it still answers it rather than leave the prompt hanging.
    @Test
    void functioningBaseIsAnsweredEvenWhenNothingIsGained() {
        test.nekro.setCommodities(3);
        for (int card = 0; card < 7; card++) test.nekro.setActionCard("card" + card);

        assertThat(pressedId(next(functioningBase()).orElseThrow())).isEqualTo("gain_1_comms");
    }

    // With neither a mech nor an infantry on the planet the card offers only Decline, and the AI presses it.
    @Test
    void declinesACardItHasNoUnitsFor() {
        assertThat(pressedId(next(expedition()).orElseThrow())).isEqualTo(DECLINE);
    }

    // Abandoned Warehouses: with all 3 commodities in hand, gaining 2 is worth nothing and converting 2 is worth
    // two thirds of a trade good each.
    @Test
    void abandonedWarehousesConvertsFullCommodities() {
        test.nekro.setCommodities(3);

        assertThat(pressedId(next(card("Abandoned Warehouses", "convert_2_comms", "gain_2_comms"))
                        .orElseThrow()))
                .isEqualTo("convert_2_comms");
    }

    // The Merchant Station's own buttons: convert everything it holds when it is full.
    @Test
    void merchantStationConvertsFullCommodities() {
        test.nekro.setCommodities(3);

        assertThat(pressedId(next(merchantStation()).orElseThrow())).isEqualTo("FFCC_nekro_mallice_convert_comm");
    }

    // Replenishing does not close the Merchant Station's message, so it is never pressed twice.
    @Test
    void merchantStationIsAnsweredOnlyOnce() {
        test.nekro.setCommodities(3);
        AiPrompt station = merchantStation();
        String pressed = AiTurnContext.pressKey(station, station.buttons().get(1));

        assertThat(ExplorationRules.next(test.context(Set.of(pressed), station)))
                .isEmpty();
    }

    // Both sides of the Ion Storm link systems with the same wormhole. Alpha leads to Lodor, which nobody holds; beta
    // to Quann, which is worth less. Once Lodor is Nekro's, the free planet is on the beta side.
    @Test
    void ionStormLinksTowardTheFreePlanets() {
        test.place("46", "305");
        test.place("26", "302");
        test.place("25", "304");
        AiPrompt storm = AiTestGame.withContent(
                prompt(
                        "storm",
                        PromptSource.PUBLIC,
                        NOW,
                        List.of("addIonStorm_alpha_305", "addIonStorm_beta_305"),
                        List.of("Place an Alpha", "Place a Beta")),
                test.nekro.getRepresentation()
                        + ", please choose if the _Ion Storm_ is placed on its alpha or beta side.");
        assertThat(pressedId(next(storm).orElseThrow())).isEqualTo("addIonStorm_alpha_305");

        test.nekro.addPlanet("lodor");
        assertThat(pressedId(next(storm).orElseThrow())).isEqualTo("addIonStorm_beta_305");
    }

    // A card that mentions another player belongs to that player's exploration, even on the AI's own turn.
    @Test
    void leavesCardsOfAnotherPlayerAlone() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 2);
        AiPrompt solsCard = AiTestGame.withContent(
                prompt("card", PromptSource.PUBLIC, NOW, List.of("resolveVolatileInf_tequran", DECLINE), List.of()),
                test.sol.getRepresentation() + ", please resolve _Volatile Fuel Source_.");

        assertThat(next(solsCard)).isEmpty();
    }

    // Each card is answered once.
    @Test
    void answersEachCardOnce() {
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 2);
        AiPrompt card = volatile_("resolveVolatileInf_tequran");
        String pressed = AiTurnContext.pressKey(card, card.buttons().getFirst());

        assertThat(ExplorationRules.next(test.context(Set.of(pressed), card))).isEmpty();
    }

    private AiPrompt localFabricators() {
        return AiTestGame.withContent(
                prompt(
                        "fabricators",
                        PromptSource.PUBLIC,
                        NOW,
                        List.of("resolveLocalFab_tequran", "gain_1_comms"),
                        List.of("Spend 1 Commodity or Trade Good for a Mech", "Gain 1 Commodity")),
                test.nekro.getRepresentation() + " please resolve _Local Fabricators_.");
    }

    private AiPrompt functioningBase() {
        return AiTestGame.withContent(
                prompt(
                        "base",
                        PromptSource.PUBLIC,
                        NOW,
                        List.of("comm_for_AC", "gain_1_comms"),
                        List.of("Spend 1 Trade Good or 1 Commodity For 1 Action Card", "Gain 1 Commodity")),
                "Resolve _Functioning Base_.");
    }

    private AiPrompt merchantStation() {
        return prompt(
                "station",
                PromptSource.PUBLIC,
                NOW,
                List.of("FFCC_nekro_mallice_convert_comm", "FFCC_nekro_resolveHarness"),
                List.of("Convert Commodities Into Trade Goods", "Replenish Commodities"));
    }

    private void spendEverythingAtHome() {
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
    }

    private AiPrompt volatile_(String... handlers) {
        return card("Volatile Fuel Source", handlers);
    }

    private AiPrompt coreMine(String... handlers) {
        return card("Core Mine", handlers);
    }

    private AiPrompt expedition(String... handlers) {
        return card("Expedition", handlers);
    }

    private AiPrompt card(String name, String... handlers) {
        ArrayList<String> ids = new ArrayList<>(List.of(handlers));
        ids.add(DECLINE);
        return AiTestGame.withContent(
                prompt("card", PromptSource.PUBLIC, NOW, ids, List.of()),
                test.nekro.getRepresentation() + ", please resolve _" + name + "_.");
    }

    private Optional<AiDecision> next(AiPrompt prompt) {
        return ExplorationRules.next(test.context(prompt));
    }

    private void solDestroyerNextDoor() {
        String next = PositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate))
                .findFirst()
                .orElseThrow();
        test.units(test.place("25", next), "space", test.sol, UnitType.Destroyer, 1);
    }

    private String otherNeighbourOfHome() {
        return PositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(candidate -> !"x".equals(candidate) && !position.equals(candidate))
                .findFirst()
                .orElseThrow();
    }
}
