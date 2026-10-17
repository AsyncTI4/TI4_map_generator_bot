package ti4.ai.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The offers the bot posts when a planet is explored: after taking a planet nobody held, with Scanlink Drone Network
// when it activates a system, and with the Crown of Emphidia. The decks are public, so the AI averages what is left.
class ExplorationRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile site;

    // Nekro holds Tequran (hazardous 2/0) and Torkan (cultural 0/3), the two planets of one system, with an infantry
    // on each.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        site = test.place("28", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("tequran");
        test.nekro.addPlanet("torkan");
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 1);
        test.units(site, "torkan", test.nekro, UnitType.Infantry, 1);
        test.aiIsActive("action");
    }

    // A planet taken for the first time is explored through the offer the bot posts: no choice but the trait.
    @Test
    void exploresANewlyTakenPlanet() {
        assertThat(pressedId(ExplorationRules.next(test.context(offer("filler", "tequran", "hazardous")))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_movedNExplored_filler_tequran_hazardous");
    }

    // With several planets to explore it goes hazardous first, then industrial, then cultural, as players usually do.
    @Test
    void exploresHazardousBeforeCultural() {
        AiPrompt torkan = offer("filler", "torkan", "cultural");
        AiPrompt tequran = offer("filler", "tequran", "hazardous");

        assertThat(pressedId(
                        ExplorationRules.next(test.context(torkan, tequran)).orElseThrow()))
                .isEqualTo("FFCC_nekro_movedNExplored_filler_tequran_hazardous");
    }

    // One planet with two traits offers both decks. The AI looks at what is left in each: a Mining World (+2 resources)
    // beats a Demilitarized Zone, and the other way round when the decks are swapped.
    @Test
    void choosesTheTraitWhoseDeckIsWorthMore() {
        AiPrompt both = offer("filler", "tequran", "hazardous", "cultural");
        test.game.setExploreDeck(new java.util.ArrayList<>(List.of("mw", "dmz")));
        assertThat(pressedId(ExplorationRules.next(test.context(both)).orElseThrow()))
                .isEqualTo("FFCC_nekro_movedNExplored_filler_tequran_hazardous");

        test.game.setExploreDeck(new java.util.ArrayList<>(List.of("ds", "rw")));
        assertThat(pressedId(ExplorationRules.next(test.context(both)).orElseThrow()))
                .isEqualTo("FFCC_nekro_movedNExplored_filler_tequran_cultural");
    }

    // Taking the planet starts the offer, and there is nothing to decline: even a deck of bad cards is explored.
    @Test
    void exploresEvenWhenTheDeckIsBad() {
        test.game.setExploreDeck(new java.util.ArrayList<>(List.of("dmz")));

        assertThat(ExplorationRules.next(test.context(offer("filler", "torkan", "cultural"))))
                .isPresent();
    }

    // An offer from an earlier turn is not the AI's to answer any more.
    @Test
    void ignoresAnOfferFromAnEarlierTurn() {
        AiPrompt old =
                prompt("old", PromptSource.PUBLIC, NOW - 60_000, "FFCC_nekro_movedNExplored_filler_tequran_hazardous");

        assertThat(ExplorationRules.next(test.context(old))).isEmpty();
    }

    // Scanlink explores a planet it already holds, so the card must be worth having: a Dyson Sphere is, a Demilitarized
    // Zone is not (it sends the garrison into space), and then the offer is left alone.
    @Test
    void scanlinkExploresOnlyWhenTheDeckIsWorthIt() {
        AiPrompt scanlink = offer("scanlink", "torkan", "cultural");
        test.game.setExploreDeck(new java.util.ArrayList<>(List.of("dmz")));
        assertThat(ExplorationRules.next(test.context(scanlink))).isEmpty();

        test.game.setExploreDeck(new java.util.ArrayList<>(List.of("ds")));
        assertThat(pressedId(ExplorationRules.next(test.context(scanlink)).orElseThrow()))
                .isEqualTo("FFCC_nekro_movedNExplored_scanlink_torkan_cultural");
    }

    // With two planets to choose from, Scanlink picks the one whose deck is worth more.
    @Test
    void scanlinkPicksTheBetterPlanet() {
        test.game.setExploreDeck(new java.util.ArrayList<>(List.of("mw", "ds")));
        AiPrompt scanlink = offer("scanlink", List.of("tequran", "torkan"), List.of("hazardous", "cultural"));

        assertThat(pressedId(ExplorationRules.next(test.context(scanlink)).orElseThrow()))
                .isEqualTo("FFCC_nekro_movedNExplored_scanlink_torkan_cultural");
    }

    // The Crown of Emphidia is exhausted for an exploration only when some planet's deck is worth it.
    @Test
    void crownExploresOnlyWhenWorthIt() {
        AiPrompt crown = AiTestGame.withContent(
                prompt(
                        "crown",
                        PromptSource.PUBLIC,
                        NOW,
                        List.of("crownofemphidiaexplore", "deleteButtons"),
                        List.of("Use Crown of Emphidia To Explore", "Decline")),
                test.nekro.getRepresentation() + ", you may use the button to explore a planet.");
        test.game.setExploreDeck(new java.util.ArrayList<>(List.of("dmz")));
        assertThat(pressedId(ExplorationRules.next(test.context(crown)).orElseThrow()))
                .isEqualTo("deleteButtons");

        test.game.setExploreDeck(new java.util.ArrayList<>(List.of("ds")));
        assertThat(pressedId(ExplorationRules.next(test.context(crown)).orElseThrow()))
                .isEqualTo("crownofemphidiaexplore");
    }

    // After Volatile Fuel Source the bot asks for one command token. It goes to the pool the policy grows first, then
    // the AI closes the window; a second token is never taken.
    @Test
    void placesTheCommandTokenAndFinishes() {
        test.game.setStoredValue("originalCCsFornekro", test.nekro.getCCRepresentation());
        AiPrompt tokens = tokenPrompt("Please gain 1 command token. Your current command tokens are 3/3/2.");
        String grow = "FFCC_nekro_" + CommandTokenPolicy.poolToGrow(test.game, test.nekro);

        assertThat(pressedId(ExplorationRules.next(test.context(tokens)).orElseThrow()))
                .isEqualTo(grow);

        test.nekro.setTacticalCC(test.nekro.getTacticalCC() + 1);
        assertThat(pressedId(ExplorationRules.next(test.context(tokens)).orElseThrow()))
                .isEqualTo("FFCC_nekro_deleteButtons");
    }

    // The Keleres ship asks for two tokens.
    @Test
    void placesAsManyTokensAsTheCardGives() {
        test.game.setStoredValue("originalCCsFornekro", test.nekro.getCCRepresentation());
        AiPrompt tokens = tokenPrompt("Use buttons to gain 2 command tokens.");

        test.nekro.setTacticalCC(test.nekro.getTacticalCC() + 1);
        assertThat(pressedId(ExplorationRules.next(test.context(tokens)).orElseThrow()))
                .startsWith("FFCC_nekro_increase_");

        test.nekro.setTacticalCC(test.nekro.getTacticalCC() + 1);
        assertThat(pressedId(ExplorationRules.next(test.context(tokens)).orElseThrow()))
                .isEqualTo("FFCC_nekro_deleteButtons");
    }

    // Inside the tactical action the same rules apply: a newly taken planet is explored, but a Scanlink offer whose
    // deck is bad is left alone and the action carries on.
    @Test
    void exploresInsideATacticalActionAndSkipsABadScanlink() {
        test.game.setStoredValue("currentActionSummarynekro", " Activated 28.");
        test.game.setActiveSystem(site.getPosition());
        test.game.setExploreDeck(new java.util.ArrayList<>(List.of("dmz")));

        assertThat(TacticalRules.continueAction(test.context(offer("scanlink", "torkan", "cultural"))))
                .isEmpty();
        assertThat(pressedId(TacticalRules.continueAction(test.context(offer("filler", "torkan", "cultural")))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_movedNExplored_filler_torkan_cultural");
    }

    // The whole brain answers the offer, and sees the unowned buttons the cards use.
    @Test
    void theBrainAnswersExplorationPromptsAndSeesTheirButtons() {
        NekroBrain brain = new NekroBrain();
        AiDecision decision = brain.decide(test.context(offer("filler", "tequran", "hazardous")));
        assertThat(pressedId(decision)).isEqualTo("FFCC_nekro_movedNExplored_filler_tequran_hazardous");

        for (String handler : List.of(
                "resolveVolatileMech_x",
                "resolveVolatileInf_x",
                "resolveExpeditionMech_x",
                "resolveExpeditionInf_x",
                "resolveCoreMineMech_x",
                "resolveCoreMineInf_x",
                "resolveLocalFab_x",
                "freelancersBuild_x",
                "decline_explore",
                "comm_for_AC",
                "gain_1_comms",
                "gain_2_comms",
                "convert_2_comms",
                "addIonStorm_alpha_301",
                "crownofemphidiaexplore",
                "acquireATech")) {
            assertThat(brain.publicWindowHandlerPrefixes().stream().anyMatch(handler::startsWith))
                    .as(handler)
                    .isTrue();
        }
    }

    private AiPrompt tokenPrompt(String content) {
        return AiTestGame.withContent(
                prompt(
                        "tokens",
                        PromptSource.PUBLIC,
                        NOW,
                        List.of(
                                "FFCC_nekro_increase_tactic_cc",
                                "FFCC_nekro_increase_fleet_cc",
                                "FFCC_nekro_increase_strategy_cc",
                                "FFCC_nekro_deleteButtons"),
                        List.of(
                                "Gain 1 Tactic Token",
                                "Gain 1 Fleet Token",
                                "Gain 1 Strategy Token",
                                "Done Gaining Command Tokens")),
                content);
    }

    private AiPrompt offer(String source, String planet, String... traits) {
        return offer(source, java.util.Collections.nCopies(traits.length, planet), List.of(traits));
    }

    private AiPrompt offer(String source, List<String> planets, List<String> traits) {
        List<String> ids = new java.util.ArrayList<>();
        for (int index = 0; index < planets.size(); index++) {
            ids.add("FFCC_nekro_movedNExplored_" + source + "_" + planets.get(index) + "_" + traits.get(index));
        }
        return prompt("offer", PromptSource.PUBLIC, NOW, ids, List.of());
    }
}
