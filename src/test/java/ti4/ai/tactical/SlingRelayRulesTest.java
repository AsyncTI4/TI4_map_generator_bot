package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.PaymentRules;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Before passing, Sling Relay adds a ship at a space dock and lets the seat stall for one more action. Planet
// resources left unspent are wasted for the round; trade goods are weighed against the ship.
class SlingRelayRulesTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.nekro.addTech("sr");
        test.aiIsActive("action");
    }

    // With no carrier yet, the ship worth most is a carrier at the home dock. It opens the component actions,
    // exhausts Sling Relay, picks the home system, produces the carrier and expects to pay its 3 resources.
    @Test
    void producesACarrierInsteadOfPassing() {
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_componentAction", "FFCC_nekro_passForRound");
        assertThat(pressedId(
                        SlingRelayRules.start(test.context(turn), List.of(turn)).orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");

        AiPrompt menu = prompt("menu", PromptSource.PUBLIC, NOW + 1, "FFCC_nekro_exhaustTech_sr");
        assertThat(pressedId(SlingRelayRules.next(test.context(menu)).orElseThrow()))
                .isEqualTo("FFCC_nekro_exhaustTech_sr");

        AiPrompt tiles = prompt("tiles", PromptSource.PUBLIC, NOW + 2, "produceOneUnitInTile_301_sling");
        assertThat(pressedId(SlingRelayRules.next(test.context(tiles)).orElseThrow()))
                .isEqualTo("produceOneUnitInTile_301_sling");

        AiPrompt units = prompt(
                "units",
                PromptSource.PUBLIC,
                NOW + 3,
                "FFCC_nekro_placeOneNDone_dontskip_carrier_301",
                "FFCC_nekro_placeOneNDone_dontskip_dreadnought_301");
        assertThat(pressedId(SlingRelayRules.next(test.context(units)).orElseThrow()))
                .isEqualTo("FFCC_nekro_placeOneNDone_dontskip_carrier_301");
        assertThat(PaymentRules.isPending(test.context())).isTrue();
        assertThat(SlingRelayRules.next(test.context(units))).isEmpty();
    }

    // With every planet spent, two trade goods still buy a destroyer: one more ship and one more turn to watch.
    @Test
    void spendsTradeGoodsOnADestroyer() {
        test.nekro.getPlanets().forEach(test.nekro::exhaustPlanet);
        test.nekro.setTg(2);
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_componentAction", "FFCC_nekro_passForRound");

        assertThat(SlingRelayRules.best(test.game, test.nekro)
                        .orElseThrow()
                        .ship()
                        .type())
                .isEqualTo(UnitType.Destroyer);
        assertThat(SlingRelayRules.start(test.context(turn), List.of(turn))).isPresent();
    }

    // With no planets and no trade goods there is nothing to pay with, so it simply passes.
    @Test
    void passesWithNothingToSpend() {
        test.nekro.getPlanets().forEach(test.nekro::exhaustPlanet);
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_componentAction", "FFCC_nekro_passForRound");

        assertThat(SlingRelayRules.start(test.context(turn), List.of(turn))).isEmpty();
    }

    // Mid-round, Sling Relay is an action of its own. With no tactic token left there is no tactical action to take,
    // so it adds a ship and waits to see what the others do.
    @Test
    void stallsWithSlingRelayWhenNoTacticalActionIsWorthIt() {
        test.nekro.setTacticalCC(0);
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_componentAction");

        assertThat(pressedId(SlingRelayRules.insteadOfTacticalAction(test.context(turn), List.of(turn))
                        .orElseThrow()))
                .isEqualTo("FFCC_nekro_componentAction");
    }

    // A free planet next door is worth more than a ship and a stall, so the tactical action goes first.
    @Test
    void takesAWorthwhileTacticalActionFirst() {
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 2);
        test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_componentAction");

        assertThat(SlingRelayRules.insteadOfTacticalAction(test.context(turn), List.of(turn)))
                .isEmpty();
    }
}
