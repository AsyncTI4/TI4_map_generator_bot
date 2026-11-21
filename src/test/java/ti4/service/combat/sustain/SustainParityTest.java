package ti4.service.combat.sustain;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.combat.sustain.SustainScenario.PLANET;
import static ti4.service.combat.sustain.SustainScenario.SPACE;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.testUtils.BaseTi4Test;

/**
 * On the same board, the units the manual hit buttons offer Sustain for must be exactly the units auto-assign
 * sustains. Space and ground combat allow no difference at all. Auto-assigned anti-fighter barrage only damages ships
 * through Raid Formation, which picks "ships that have SUSTAIN DAMAGE"; the named exceptions below are the units the
 * manual buttons rightly offer that Raid Formation cannot pick.
 */
class SustainParityTest extends BaseTi4Test {

    record Fixture(String name, Function<SustainScenario, Player> setup) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Arguments> fixtures() {
        List<Fixture> fixtures = new ArrayList<>();
        SustainRuleMatrix.rows().forEach(row -> fixtures.add(new Fixture(row.toString(), row.setup())));
        fixtures.add(new Fixture("late remaining-sustains pass when the opponent could Direct Hit", s -> {
            Player owner = SustainRuleMatrix.owner(s, "warsun", "sol_flagship", "dreadnought");
            s.place(owner, SPACE, UnitType.Flagship, 1);
            Player opponent = s.addOpponent("blue", "hacan");
            opponent.setActionCard("dh1");
            return owner;
        }));
        fixtures.add(new Fixture("mixed fleet with Metali Void Shielding", s -> {
            Player owner = SustainRuleMatrix.owner(s, "dreadnought", "carrier", "cruiser", "fighter", "warsun");
            s.place(owner, SPACE, UnitType.Carrier, 2);
            s.place(owner, SPACE, UnitType.Cruiser, 1);
            s.place(owner, SPACE, UnitType.Fighter, 2);
            owner.addRelic("metalivoidshielding");
            return owner;
        }));
        fixtures.add(new Fixture("ground forces and a structure on a planet", s -> {
            Player owner = SustainRuleMatrix.owner(s, "mech");
            owner.addOwnedUnitByID("pds");
            s.place(owner, PLANET, UnitType.Pds, 1);
            s.place(owner, PLANET, UnitType.Mech, 1);
            return owner;
        }));
        return fixtures.stream().map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void manualButtonsAndAutoAssignAgreeInSpaceCombat(Fixture fixture) {
        Set<UnitType> manual = manual(fixture, "spacecombat", SPACE);
        Set<UnitType> auto = withOwner(fixture, (scenario, owner) -> {
            scenario.addOpponent("black", "hacan");
            return scenario.autoSpace(owner);
        });

        assertThat(manual).as("manual %s vs auto %s", manual, auto).isEqualTo(auto);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void manualButtonsAndAutoAssignAgreeInGroundCombat(Fixture fixture) {
        Set<UnitType> manual = manual(fixture, "groundcombat", PLANET);
        Set<UnitType> auto = withOwner(fixture, SustainScenario::autoGround);

        assertThat(manual).as("manual %s vs auto %s", manual, auto).isEqualTo(auto);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void manualButtonsAndRaidFormationAgreeOnAntiFighterBarrage(Fixture fixture) {
        SustainScenario scenario = new SustainScenario();
        Player owner = fixture.setup().apply(scenario);
        Set<UnitType> manual = scenario.manual(owner, "afb", SPACE);
        Set<UnitType> pickableByRaidFormation = EnumSet.noneOf(UnitType.class);
        for (UnitType type : manual) {
            if (!raidFormationCannotPick(owner.getUnitFromUnitKey(scenario.key(owner, type)))) {
                pickableByRaidFormation.add(type);
            }
        }
        Set<UnitType> auto = withOwner(fixture, SustainScenario::autoAntiFighterBarrage);

        assertThat(pickableByRaidFormation)
                .as("manual %s vs auto %s", manual, auto)
                .isEqualTo(auto);
    }

    // Named exceptions, kept from the anti-fighter barrage fix in #6233:
    // - Quantum Manipulator may cancel anti-fighter barrage hits against the Nomad's ships (Nomad ruling II), but it
    //   is not a ship, so Raid Formation cannot pick it.
    // - Granted sustain (Metali Void Shielding, Old Glory, Bulwark, Komat, Splintering Gale) lets a unit cancel a hit,
    //   but the unit does not "have" SUSTAIN DAMAGE for Raid Formation (Metali Void Shielding ruling IV).
    private static boolean raidFormationCannotPick(UnitModel unit) {
        return !unit.getIsShip() || !unit.getSustainDamage();
    }

    private static Set<UnitType> manual(Fixture fixture, String assignHitsType, String holder) {
        SustainScenario scenario = new SustainScenario();
        Player owner = fixture.setup().apply(scenario);
        scenario.addOpponent("black", "hacan");
        return scenario.manual(owner, assignHitsType, holder);
    }

    private static Set<UnitType> withOwner(Fixture fixture, BiFunction<SustainScenario, Player, Set<UnitType>> path) {
        SustainScenario scenario = new SustainScenario();
        Player owner = fixture.setup().apply(scenario);
        return path.apply(scenario, owner);
    }
}
