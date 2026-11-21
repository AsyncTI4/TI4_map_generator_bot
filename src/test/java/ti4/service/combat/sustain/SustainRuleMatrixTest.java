package ti4.service.combat.sustain;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.combat.sustain.SustainScenario.SPACE;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.service.combat.SustainCatalogs;
import ti4.service.combat.sustain.SustainRuleMatrix.Path;
import ti4.service.combat.sustain.SustainRuleMatrix.Row;
import ti4.testUtils.BaseTi4Test;

class SustainRuleMatrixTest extends BaseTi4Test {

    static Stream<Arguments> cells() {
        return SustainRuleMatrix.rows().stream()
                .flatMap(row -> row.expected().entrySet().stream()
                        .map(cell -> Arguments.of(row, cell.getKey(), cell.getValue())));
    }

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @MethodSource("cells")
    void eachPathAgreesWithTheRule(Row row, Path path, boolean sustains) {
        SustainScenario scenario = new SustainScenario();
        Player owner = row.setup().apply(scenario);

        Set<UnitType> sustained = path.sustained(scenario, owner, row.holder());

        assertThat(sustained.contains(row.unit()))
                .as("%s sustained on %s: %s", row.unit(), path, sustained)
                .isEqualTo(sustains);
    }

    @Test
    void everyCatalogRuleHasAMatrixRow() {
        Set<String> covered = new TreeSet<>();
        SustainRuleMatrix.rows().forEach(row -> covered.add(row.rule()));
        covered.add("nes");
        covered.add("kryxos_flagship3");

        Set<String> missing = new TreeSet<>();
        SustainCatalogs.rules().forEach(rule -> missing.add(rule.id()));
        SustainCatalogs.cancelsTwoHits().forEach(rule -> missing.add(rule.id()));
        missing.removeAll(covered);

        assertThat(missing).isEmpty();
    }

    // LRR 87.6: Non-Euclidean Shielding cancels 2 hits per SUSTAIN DAMAGE, so 2 hits leave the destroyer alive.
    @Test
    void nonEuclideanShieldingCancelsTwoHitsInAutoAssign() {
        SustainScenario scenario = new SustainScenario();
        Player owner = scenario.addPlayer("red", "letnev", "dreadnought", "destroyer");
        owner.addTech("nes");
        scenario.place(owner, SPACE, UnitType.Dreadnought, 1);
        scenario.place(owner, SPACE, UnitType.Destroyer, 1);
        scenario.addOpponent("black", "hacan");

        ButtonHelperModifyUnits.autoAssignSpaceCombatHits(owner, scenario.game, scenario.tile, 2, null, false);

        UnitHolder space = scenario.tile.getSpaceUnitHolder();
        assertThat(space.getDamagedUnitCount(scenario.key(owner, UnitType.Dreadnought)))
                .isEqualTo(1);
        assertThat(space.getUnitCount(scenario.key(owner, UnitType.Destroyer))).isEqualTo(1);
    }

    // Ultimate Evolution III: "When this unit uses SUSTAIN DAMAGE: Cancel an additional hit."
    @Test
    void ultimateEvolutionThreeCancelsTwoHitsOnlyForTheFlagship() {
        SustainScenario scenario = new SustainScenario();
        Player owner = scenario.addPlayer("red", "kryxos", "kryxos_flagship3", "dreadnought", "destroyer");
        scenario.place(owner, SPACE, UnitType.Flagship, 1);
        scenario.place(owner, SPACE, UnitType.Destroyer, 1);
        scenario.addOpponent("black", "hacan");
        SustainContext context = SustainContext.of(scenario.game, owner, scenario.tile, null, HitSource.SPACE_COMBAT);

        assertThat(SustainEligibility.hitsCancelled(context, owner.getUnitFromUnitKey(flagship(scenario, owner))))
                .isEqualTo(2);
        assertThat(SustainEligibility.hitsCancelled(
                        context, owner.getUnitFromUnitKey(scenario.key(owner, UnitType.Dreadnought))))
                .isEqualTo(1);

        ButtonHelperModifyUnits.autoAssignSpaceCombatHits(owner, scenario.game, scenario.tile, 2, null, false);

        UnitHolder space = scenario.tile.getSpaceUnitHolder();
        assertThat(space.getDamagedUnitCount(flagship(scenario, owner))).isEqualTo(1);
        assertThat(space.getUnitCount(scenario.key(owner, UnitType.Destroyer))).isEqualTo(1);
    }

    // D4: manual hit buttons outside the combat builder (Magen Defense Grid, Ruthless, ...) also refuse Sustain
    // when a suppressor applies, but still offer Destroy.
    @Test
    void abilityHitsRespectSuppressorsButKeepDestroy() {
        SustainScenario scenario = new SustainScenario();
        Player owner = SustainRuleMatrix.owner(scenario, "mech");
        Player mentak = scenario.addPlayer("blue", "mentak", "mentak_mech");
        scenario.place(mentak, SustainScenario.PLANET, UnitType.Mech, 1);

        assertThat(scenario.canSustain(owner, SustainScenario.PLANET, HitSource.ABILITY, UnitType.Mech))
                .isFalse();
        assertThat(scenario.manual(owner, "groundcombat", SustainScenario.PLANET))
                .doesNotContain(UnitType.Mech);
        assertThat(
                        ButtonHelper.getButtonsForRemovingAllUnitsInSystem(
                                        owner, scenario.game, scenario.tile, "groundcombat")
                                .stream()
                                .map(Button::getCustomId)
                                .anyMatch(id -> id.contains("assignHits_" + SustainScenario.POSITION + "_1_mf")))
                .isTrue();
    }

    private static UnitKey flagship(SustainScenario scenario, Player owner) {
        return scenario.key(owner, UnitType.Flagship);
    }
}
