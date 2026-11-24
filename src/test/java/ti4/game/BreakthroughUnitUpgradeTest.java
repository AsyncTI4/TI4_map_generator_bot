package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ti4.service.franken.FrankenUnitService;
import ti4.testUtils.BaseTi4Test;

class BreakthroughUnitUpgradeTest extends BaseTi4Test {

    // breakthrough, unit owned before the upgrade, upgrade tech, unit that tech gives, breakthrough's flip side.
    // The first six were hard-coded swaps before; Arachnera and Industries had no swap at all.
    @ParameterizedTest
    @CsvSource({
        "mentakbt, cruiser, cr2, cruiser2, mentak_cruiser3",
        "rohdhnabt, rohdhna_warsun, dsrohdws, rohdhna_warsun2, rohdhna_warsun3",
        "mirvedabt, fighter, ff2, fighter2, mirveda_fighter3",
        "crystellumbt, fighter, ff2, fighter2, crystellum_fighter3",
        "kortalibt, dreadnought, dn2, dreadnought2, tribune3",
        "uydaibt, infantry, inf2, infantry2, death_commandos3",
        "arachnerabt, cruiser, cr2, cruiser2, arachnera_cruiser3",
        "industriesbt, spacedock, sd2, spacedock2, industries_spacedock3",
    })
    void researchingTheUpgradeAfterUnlockingFlipsTheBreakthrough(
            String breakthrough, String baseUnit, String tech, String upgradedUnit, String flipSide) {
        Player player = playerOwning(baseUnit);
        unlock(player, breakthrough);

        player.addTech(tech);

        assertThat(player.getUnitsOwned()).contains(flipSide).doesNotContain(baseUnit, upgradedUnit);
    }

    // Same table, other order: the upgrade is already researched when the breakthrough unlocks.
    // Kortali never flipped this way before, because the unlock code checked "kortalbt".
    @ParameterizedTest
    @CsvSource({
        "mentakbt, cruiser, cr2, cruiser2, mentak_cruiser3",
        "rohdhnabt, rohdhna_warsun, dsrohdws, rohdhna_warsun2, rohdhna_warsun3",
        "mirvedabt, fighter, ff2, fighter2, mirveda_fighter3",
        "crystellumbt, fighter, ff2, fighter2, crystellum_fighter3",
        "kortalibt, dreadnought, dn2, dreadnought2, tribune3",
        "uydaibt, infantry, inf2, infantry2, death_commandos3",
        "arachnerabt, cruiser, cr2, cruiser2, arachnera_cruiser3",
        "industriesbt, spacedock, sd2, spacedock2, industries_spacedock3",
    })
    void unlockingAfterResearchingTheUpgradeFlipsTheBreakthrough(
            String breakthrough, String baseUnit, String tech, String upgradedUnit, String flipSide) {
        Player player = playerOwning(baseUnit);
        player.addTech(tech);
        unlock(player, breakthrough);

        player.applyBreakthroughUnitUpgrades();

        assertThat(player.getUnitsOwned()).contains(flipSide).doesNotContain(baseUnit, upgradedUnit);
    }

    @Test
    void lockedBreakthroughDoesNotFlip() {
        Player player = playerOwning("cruiser");
        player.addBreakthrough("mentakbt");

        player.addTech("cr2");

        assertThat(player.getUnitsOwned()).contains("cruiser2").doesNotContain("mentak_cruiser3");
    }

    @Test
    void unlockingWithoutTheUpgradeDoesNotFlip() {
        Player player = playerOwning("cruiser");
        unlock(player, "mentakbt");

        player.applyBreakthroughUnitUpgrades();

        assertThat(player.getUnitsOwned()).contains("cruiser").doesNotContain("mentak_cruiser3");
    }

    @Test
    void anotherUnitTypesUpgradeDoesNotFlip() {
        Player player = playerOwning("fighter");
        unlock(player, "mirvedabt");

        player.addTech("cr2");

        assertThat(player.getUnitsOwned()).contains("fighter").doesNotContain("mirveda_fighter3");
    }

    // Franken: a faction-specific upgrade of the named unit counts too. The Corsair replaces Saturn Engine II
    // (baseUpgrade cr2), in either order.
    @Test
    void factionSpecificVersionOfTheNamedUpgradeFlips() {
        Player researchedAfterUnlocking = playerOwning("titans_cruiser");
        unlock(researchedAfterUnlocking, "mentakbt");
        researchedAfterUnlocking.addTech("se2");

        Player unlockedAfterResearching = playerOwning("titans_cruiser");
        unlockedAfterResearching.addTech("se2");
        unlock(unlockedAfterResearching, "mentakbt");
        unlockedAfterResearching.applyBreakthroughUnitUpgrades();

        assertThat(researchedAfterUnlocking.getUnitsOwned())
                .contains("mentak_cruiser3")
                .doesNotContain("titans_cruiser", "titans_cruiser2");
        assertThat(unlockedAfterResearching.getUnitsOwned())
                .contains("mentak_cruiser3")
                .doesNotContain("titans_cruiser", "titans_cruiser2");
    }

    // Franken with "combine duplicate unit types" merges two faction-specific cruisers into one card. Researching
    // Saturn Engine II also upgrades Shattered Sky, and the Corsair replaces the whole merged cruiser.
    @Test
    void corsairReplacesTheWholeMergedCruiser() {
        Game game = new Game();
        Player player = game.addPlayer("user", "user");
        player.setFaction(game, "franken12");
        player.setColor("red");
        player.setUnitsOwned(new LinkedHashSet<>(List.of("titans_cruiser", "khrask_cruiser")));
        game.setStoredValue(FrankenUnitService.COMBINE_DUPLICATE_UNIT_TYPES, "true");
        unlock(player, "mentakbt");

        player.addTech("se2");

        assertThat(player.hasTech("dskhracr")).isTrue();
        assertThat(player.getUnitsOwned())
                .contains("mentak_cruiser3")
                .doesNotContain("titans_cruiser", "titans_cruiser2", "khrask_cruiser", "khrask_cruiser2");
        assertThat(player.getUnitsByAsyncID("ca"))
                .singleElement()
                .satisfies(cruiser -> assertThat(cruiser.getName()).isEqualTo("Corsair"));
    }

    @Test
    void factionSpecificFighterFlipsMirveda() {
        Player player = playerOwning("naalu_fighter");
        unlock(player, "mirvedabt");

        player.addTech("hcf2");

        assertThat(player.getUnitsOwned()).contains("mirveda_fighter3").doesNotContain("naalu_fighter2");
    }

    // Reworked versions of the named tech keep its name, so they still count.
    @Test
    void reworkOfTheNamedUpgradeFlips() {
        Player player = playerOwning("fighter");
        unlock(player, "mirvedabt");

        player.addTech("pi_ff2");

        assertThat(player.getUnitsOwned()).contains("mirveda_fighter3");
    }

    // miltymod_ffac2 lists ff2 as the tech it replaces, but it is Floating Factory II, a space dock upgrade.
    @Test
    void techThatOnlyClaimsToReplaceTheNamedUpgradeDoesNotFlip() {
        Player player = playerOwning("fighter");
        unlock(player, "mirvedabt");

        player.addTech("miltymod_ffac2");

        assertThat(player.getUnitsOwned()).doesNotContain("mirveda_fighter3");
    }

    // These flip for other reasons (on gain, or while active), so researching an upgrade must not add them.
    @ParameterizedTest
    @ValueSource(strings = {"celdauribt", "thronesbt", "naazbt"})
    void breakthroughsThatDoNotFlipOnATechAreLeftAlone(String breakthrough) {
        Player player = playerOwning("cruiser");
        unlock(player, breakthrough);

        player.addTech("cr2");

        assertThat(player.getUnitsOwned()).doesNotContain("celdauri_celagrom", "thrones_aurelion", "naaz_voltron");
    }

    private static Player playerOwning(String unit) {
        Player player = new Player("player", "", new Game());
        player.addOwnedUnitByID(unit);
        return player;
    }

    private static void unlock(Player player, String breakthrough) {
        player.addBreakthrough(breakthrough);
        player.setBreakthroughUnlocked(breakthrough, true);
    }
}
