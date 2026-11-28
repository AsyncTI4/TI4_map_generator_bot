package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Faction technologies that act at the start of, or between rounds of, a space combat.
class CombatTechRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private String position;
    private Tile tile;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        test.units(tile, "space", test.nekro, UnitType.Dreadnought, 1);
        test.aiIsActive("action");
    }

    // Dimensional Splicer's hit is free, and it goes on the cruiser rather than a fighter.
    @Test
    void hitsTheBestShipWithDimensionalSplicer() {
        test.nekro.addTech("ds");
        test.units(tile, "space", test.sol, UnitType.Cruiser, 1);
        test.units(tile, "space", test.sol, UnitType.Fighter, 2);
        String sol = test.sol.getColor();
        AiPrompt start = prompt("start", PromptSource.COMBAT_THREAD, NOW, "assCannonNDihmohn_ds_" + position);
        AiPrompt targets = prompt(
                "targets",
                PromptSource.COMBAT_THREAD,
                NOW + 1,
                List.of(
                        "FFCC_nekro_hitOpponent_" + position + "_fighter_" + sol + "_",
                        "FFCC_nekro_hitOpponent_" + position + "_cruiser_" + sol + "_"),
                List.of("Destroy 1 Fighter", "Destroy 1 Cruiser"));

        assertThat(press(start)).isEqualTo("assCannonNDihmohn_ds_" + position);
        assertThat(press(targets)).isEqualTo("FFCC_nekro_hitOpponent_" + position + "_cruiser_" + sol + "_");
    }

    // Impulse Core trades a destroyer for a hit the opponent must put on a non-fighter ship. Against a lone cruiser
    // that is a cruiser for a destroyer; against a dreadnought the hit would only be sustained.
    @Test
    void usesImpulseCoreOnlyWhenTheHitCannotBeSustained() {
        test.nekro.addTech("ic");
        test.units(tile, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(tile, "space", test.sol, UnitType.Dreadnought, 1);
        AiPrompt start = prompt("start", PromptSource.COMBAT_THREAD, NOW, "FFCC_nekro_startImpulseCore_" + position);
        assertThat(CombatTechRules.next(test.context(start), List.of(start))).isEmpty();

        tile.removeUnit("space", Units.getUnitKey(UnitType.Dreadnought, test.sol.getColor()), 1);
        test.units(tile, "space", test.sol, UnitType.Cruiser, 1);
        AiPrompt sacrifice = prompt(
                "sacrifice",
                PromptSource.COMBAT_THREAD,
                NOW + 1,
                "FFCC_nekro_resolveImpulseCore_" + position + "_ca",
                "FFCC_nekro_resolveImpulseCore_" + position + "_dd",
                "deleteButtons");

        assertThat(press(start)).isEqualTo("FFCC_nekro_startImpulseCore_" + position);
        assertThat(press(sacrifice)).isEqualTo("FFCC_nekro_resolveImpulseCore_" + position + "_dd");
    }

    // Hit by an opponent's Impulse Core, it sustains on its dreadnought rather than losing a ship, then closes the
    // prompt.
    @Test
    void sustainsAnImpulseCoreHit() {
        String color = test.nekro.getColor();
        AiPrompt hit = AiTestGame.withContent(
                prompt(
                        "hit",
                        PromptSource.COMBAT_THREAD,
                        NOW,
                        "FFCC_nekro_assignHits_" + position + "_1_ff_" + color,
                        "FFCC_nekro_assignHits_" + position + "_1_dn_" + color,
                        "FFCC_nekro_assignDamage_" + position + "_1_dn_" + color,
                        "deleteButtons"),
                "your opponent used _Impulse Core_ to produce 1 hit against your ships.");

        assertThat(press(hit)).isEqualTo("FFCC_nekro_assignDamage_" + position + "_1_dn_" + color);
        assertThat(press(hit)).isEqualTo("deleteButtons");
    }

    // After a round, Exotrireme II gives up the dreadnought to destroy two ships. Two dreadnoughts are worth it; a
    // carrier and a cruiser are not.
    @Test
    void tradesADreadnoughtForTwoBetterShipsWithExotriremeII() {
        test.nekro.addTech("exo2");
        test.units(tile, "space", test.sol, UnitType.Carrier, 1);
        test.units(tile, "space", test.sol, UnitType.Cruiser, 1);
        tracker("nekro", 1);
        tracker("sol", 1);
        AiPrompt abilities = prompt("abilities", PromptSource.COMBAT_THREAD, NOW, "assCannonNDihmohn_exo_" + position);
        assertThat(CombatTechRules.next(test.context(abilities), List.of(abilities)))
                .isEmpty();

        test.units(tile, "space", test.sol, UnitType.Dreadnought, 2);

        assertThat(press(abilities)).isEqualTo("assCannonNDihmohn_exo_" + position);
    }

    // Supercharge is exhausted once, at the start of the combat.
    @Test
    void exhaustsSuperchargeForTheCombat() {
        test.nekro.addTech("sc");
        AiPrompt start = prompt("start", PromptSource.COMBAT_THREAD, NOW, "FFCC_nekro_applytempcombatmod__tech__sc");

        assertThat(press(start)).isEqualTo("FFCC_nekro_applytempcombatmod__tech__sc");
        assertThat(CombatTechRules.next(test.context(start), List.of(start))).isEmpty();
    }

    private String press(AiPrompt prompt) {
        return CombatTechRules.next(test.context(prompt), List.of(prompt))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    private void tracker(String faction, int rounds) {
        test.game.setStoredValue("combatRoundTracker" + faction + position + "space", String.valueOf(rounds));
    }
}
