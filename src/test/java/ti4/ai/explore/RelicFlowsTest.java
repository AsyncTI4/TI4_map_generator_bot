package ti4.ai.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// Relics whose ACTION the AI takes through the component action menu: it opens the menu instead of a weak tactical
// action or before passing, presses the relic's own button and answers whatever the bot asks next.
class RelicFlowsTest extends BaseTi4Test {

    private static final String MENU = "FFCC_nekro_componentAction";
    private static final String RELIC_BUTTON = "FFCC_nekro_componentActionRes_relic_";

    private AiTestGame test;
    private AiPrompt turn;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.aiIsActive("action");
        turn = prompt("turn", PromptSource.PUBLIC, NOW, MENU, "FFCC_nekro_passForRound");
    }

    private Optional<AiDecision> openBeforePassing() {
        return RelicActionRules.beforePassing(test.context(turn), List.of(turn));
    }

    private AiPrompt componentMenu(String relic) {
        return prompt("menu", PromptSource.PUBLIC, NOW + 1, RELIC_BUTTON + relic, "FFCC_nekro_passForRound");
    }

    private Optional<AiDecision> next(AiPrompt... prompts) {
        return RelicActionRules.next(test.context(prompts));
    }

    // Dynamis Core: the trade goods now are worth more than the two commodities a round it adds until the game ends.
    @Test
    void purgesTheDynamisCoreForTradeGoods() {
        test.nekro.addRelic("dynamiscore");

        assertThat(pressedId(openBeforePassing().orElseThrow())).isEqualTo(MENU);
        assertThat(pressedId(next(componentMenu("dynamiscore")).orElseThrow())).isEqualTo(RELIC_BUTTON + "dynamiscore");
    }

    // A relic that is already exhausted is not offered, and a seat without relics opens nothing.
    @Test
    void usesNothingWithoutAReadyRelic() {
        assertThat(openBeforePassing()).isEmpty();

        test.nekro.addRelic("titanprototype");
        test.nekro.addExhaustedRelic("titanprototype");
        assertThat(openBeforePassing()).isEmpty();
    }

    // JR-XS455-O: exhausted for a trade good on itself when the turn would end anyway: it chooses itself, then takes
    // the trade good rather than paying 3 resources for a structure.
    @Test
    void exhaustsJrForATradeGoodBeforePassing() {
        test.nekro.addRelic("titanprototype");

        assertThat(pressedId(openBeforePassing().orElseThrow())).isEqualTo(MENU);
        assertThat(pressedId(next(componentMenu("titanprototype")).orElseThrow()))
                .isEqualTo(RELIC_BUTTON + "titanprototype");
        AiPrompt choice = prompt("choice", PromptSource.PUBLIC, NOW + 2, "jrResolution_sol", "jrResolution_nekro");
        assertThat(pressedId(next(choice).orElseThrow())).isEqualTo("jrResolution_nekro");
        AiPrompt structure = AiTestGame.withContent(
                prompt(
                        "structure",
                        PromptSource.PUBLIC,
                        NOW + 3,
                        "jrStructure_sd",
                        "jrStructure_pds",
                        "jrStructure_tg"),
                test.nekro.getRepresentationUnfogged() + ", please choose which structure to build.");
        assertThat(pressedId(next(structure).orElseThrow())).isEqualTo("jrStructure_tg");
    }

    // The structure choice of another player is not the AI's to answer.
    @Test
    void leavesAnotherPlayersStructureChoiceAlone() {
        test.nekro.addRelic("titanprototype");
        openBeforePassing();
        next(componentMenu("titanprototype"));
        next(prompt("choice", PromptSource.PUBLIC, NOW + 2, "jrResolution_nekro"));
        AiPrompt theirs = AiTestGame.withContent(
                prompt("structure", PromptSource.PUBLIC, NOW + 3, "jrStructure_tg"),
                test.sol.getRepresentationUnfogged() + ", please choose which structure to build.");

        assertThat(next(theirs)).isEmpty();
    }

    // Circlet of the Void needs a frontier token in a system with no other player's ships.
    @Test
    void exploresAFrontierTokenWithTheCirclet() {
        test.nekro.addRelic("circletofthevoid");
        assertThat(openBeforePassing()).isEmpty();

        Tile frontier = test.place("41", AiTestGame.neighbourOf(AiTestGame.HOME));
        frontier.addToken(Mapper.getTokenID(Constants.FRONTIER), "space");

        assertThat(pressedId(openBeforePassing().orElseThrow())).isEqualTo(MENU);
        assertThat(pressedId(next(componentMenu("circletofthevoid")).orElseThrow()))
                .isEqualTo(RELIC_BUTTON + "circletofthevoid");
        AiPrompt targets = prompt("targets", PromptSource.PUBLIC, NOW + 2, "probeStep2_" + frontier.getPosition());
        assertThat(pressedId(next(targets).orElseThrow())).isEqualTo("probeStep2_" + frontier.getPosition());
    }

    // Nano-Forge goes on a planet that is neither legendary nor a home planet: +2 resources, +2 influence and a
    // legendary planet is worth more than most tactical actions.
    @Test
    void attachesTheNanoForgeToANonHomePlanet() {
        test.nekro.addRelic("nanoforge");
        assertThat(openBeforePassing()).isEmpty();

        test.place("22", "302");
        test.nekro.addPlanet("tarmann");

        assertThat(pressedId(openBeforePassing().orElseThrow())).isEqualTo(MENU);
        assertThat(pressedId(next(componentMenu("nanoforge")).orElseThrow())).isEqualTo(RELIC_BUTTON + "nanoforge");
        AiPrompt planets = prompt("planets", PromptSource.PUBLIC, NOW + 2, "nanoforgePlanet_tarmann");
        assertThat(pressedId(next(planets).orElseThrow())).isEqualTo("nanoforgePlanet_tarmann");
    }

    // The Codex retrieves the best of the discarded action cards, one pick at a time, and stops when the rest are
    // worth less than the cards in hand.
    @Test
    void retrievesTheBestDiscardedActionCards() {
        test.game.getDiscardActionCards().put("sabo1", 901);
        test.game.getDiscardActionCards().put("sabo2", 902);
        test.nekro.addRelic("codex");

        assertThat(pressedId(openBeforePassing().orElseThrow())).isEqualTo(MENU);
        assertThat(pressedId(next(componentMenu("codex")).orElseThrow())).isEqualTo(RELIC_BUTTON + "codex");
        AiPrompt buttons =
                prompt("codex", PromptSource.PUBLIC, NOW + 2, "codexCardPick_1", "codexCardPick_2", "codexCardPick_3");
        assertThat(pressedId(next(buttons).orElseThrow())).isEqualTo("codexCardPick_1");
        AiPrompt picker =
                prompt("picker", PromptSource.AI_THREAD, NOW + 3, "pickFromDiscard_sabo1", "pickFromDiscard_sabo2");
        assertThat(pressedId(next(buttons, picker).orElseThrow())).startsWith("pickFromDiscard_sabo");
    }

    // Book of Latvinia: the point is only there when the seat holds all four technology specialties; otherwise
    // purging it only moves the speaker token.
    @Test
    void keepsTheBookOfLatviniaUntilItScores() {
        test.nekro.addRelic("bookoflatvinia");

        assertThat(openBeforePassing()).isEmpty();
        assertThat(LatviniaFlow.holdsAllSpecialties(test.game, test.nekro)).isFalse();
    }

    // The two free technologies the Book gives when it is drawn: the best of the offered ones, twice.
    @Test
    void researchesTwoFreeTechnologiesWhenTheBookIsDrawn() {
        test.nekro.addRelic("bookoflatvinia");
        AiPrompt offer = prompt(
                "techs",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_getTech_amd__noPay__comp",
                "FFCC_nekro_getTech_st__noPay__comp");

        Optional<AiDecision> first = RelicActionRules.next(test.context(offer));
        assertThat(first).isPresent();
        assertThat(pressedId(first.orElseThrow())).startsWith("FFCC_nekro_getTech_");

        AiPrompt again = prompt(
                "techs2",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_getTech_amd__noPay__comp",
                "FFCC_nekro_getTech_st__noPay__comp");
        Set<String> pressed = Set.of(
                ti4.ai.brain.AiTurnContext.pressKey(offer, offer.buttons().getFirst()),
                ti4.ai.brain.AiTurnContext.pressKey(offer, offer.buttons().getLast()));
        assertThat(RelicActionRules.next(test.context(pressed, again))).isPresent();
        assertThat(RelicActionRules.next(test.context(pressed, again))).isEmpty();
    }

    // The Crown of Emphidia is purged for its point as soon as the bot offers the button.
    @Test
    void purgesTheCrownOfEmphidiaForItsPoint() {
        AiPrompt offer = prompt("crown", PromptSource.AI_THREAD, NOW, "resolveCrownOfE", "deleteButtons");
        assertThat(CrownOfEmphidiaRules.scorePoint(test.context(offer))).isEmpty();

        test.nekro.addRelic("emphidia");

        assertThat(pressedId(
                        CrownOfEmphidiaRules.scorePoint(test.context(offer)).orElseThrow()))
                .isEqualTo("resolveCrownOfE");
    }

    // Maw of Worlds exhausts every planet, so it is only purged for a technology worth more than the votes given up.
    @Test
    void purgesTheMawOfWorldsForAWorthwhileTechnology() {
        AiPrompt offer = prompt("maw", PromptSource.AI_THREAD, NOW, "resolveMaw", "deleteButtons");
        assertThat(MawOfWorldsRules.purge(test.context(offer))).isEmpty();

        test.nekro.addRelic("mawofworlds");
        double net = MawOfWorldsRules.net(test.game, test.nekro);

        assertThat(MawOfWorldsRules.purge(test.context(offer)).isPresent()).isEqualTo(net >= MawOfWorldsRules.MIN_NET);
    }

    // The Stellar Converter destroys a rival-held planet next to the seat's bombardment: only one that holds enough
    // (a plain planet is not worth the relic), and never one with the seat's own units on it.
    @Test
    void destroysARichRivalPlanetNextToItsBombardment() {
        test.nekro.addRelic("stellarconverter");
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        Tile target = test.place("22", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.sol.addPlanet("tarmann");
        test.units(target, "tarmann", test.sol, UnitType.Infantry, 2);

        assertThat(openBeforePassing()).isEmpty();

        test.units(target, "tarmann", test.sol, UnitType.Pds, 2);
        test.units(target, "tarmann", test.sol, UnitType.Spacedock, 1);

        assertThat(pressedId(openBeforePassing().orElseThrow())).isEqualTo(MENU);
        assertThat(pressedId(next(componentMenu("stellarconverter")).orElseThrow()))
                .isEqualTo(RELIC_BUTTON + "stellarconverter");
        AiPrompt targets = prompt("targets", PromptSource.PUBLIC, NOW + 2, "FFCC_nekro_stellarConvert_tarmann");
        assertThat(pressedId(next(targets).orElseThrow())).isEqualTo("FFCC_nekro_stellarConvert_tarmann");
    }

    // The Triad is a planet card the bot adds to the play area, so the seat's payments count it: 3 resources and 3
    // influence, plus one for each kind of fragment held.
    @Test
    void spendsTheTriadLikeAPlanet() {
        test.nekro.addRelic("thetriad");
        test.nekro.addPlanet("triad");
        test.game.getPlanetsInfo().get("triad").updateTriadStats(test.nekro);

        ti4.ai.scoring.Wallet wallet = ti4.ai.scoring.Wallet.of(test.game, test.nekro);

        assertThat(wallet.planets()).anyMatch(planet -> "triad".equals(planet.name()) && planet.resources() >= 3);
    }
}
