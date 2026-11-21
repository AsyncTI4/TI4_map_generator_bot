package ti4.service.combat.sustain;

import static ti4.service.combat.sustain.SustainScenario.PLANET;
import static ti4.service.combat.sustain.SustainScenario.SPACE;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;

/**
 * The condition x path table behind SustainRuleMatrixTest and SustainParityTest. Each row builds a board, names the
 * unit under test, and says which paths must offer (true) or refuse (false) SUSTAIN DAMAGE for it.
 */
final class SustainRuleMatrix {

    enum Path {
        MANUAL_SPACE,
        AUTO_SPACE,
        MANUAL_GROUND,
        AUTO_GROUND,
        MANUAL_AFB,
        AUTO_AFB;

        Set<UnitType> sustained(SustainScenario scenario, Player owner, String holder) {
            return switch (this) {
                case MANUAL_SPACE -> scenario.manual(owner, "spacecombat", holder);
                case AUTO_SPACE -> {
                    scenario.addOpponent("black", "hacan");
                    yield scenario.autoSpace(owner);
                }
                case MANUAL_GROUND -> scenario.manual(owner, "groundcombat", holder);
                case AUTO_GROUND -> scenario.autoGround(owner);
                case MANUAL_AFB -> scenario.manual(owner, "afb", holder);
                case AUTO_AFB -> scenario.autoAntiFighterBarrage(owner);
            };
        }
    }

    record Row(
            String rule,
            String condition,
            Function<SustainScenario, Player> setup,
            UnitType unit,
            String holder,
            Map<Path, Boolean> expected) {

        @Override
        public String toString() {
            return rule + ": " + condition;
        }
    }

    static final String PRINTED = "printed";
    private static final String OLD_GLORY_KEY = "ponthousOldGlorySustain_";
    private static final String BULWARK_KEY = "vanguardBulwarkSustain_";
    private static final String SCAR_TOKEN = "token_entropicscar_async.png";
    private static final String FAR_POSITION = "401";

    static List<Row> rows() {
        List<Row> rows = new ArrayList<>();

        rows.add(space(
                PRINTED,
                "a dreadnought sustains on every space path",
                s -> owner(s, "dreadnought"),
                UnitType.Dreadnought,
                true,
                true,
                true,
                true));
        rows.add(ground(
                PRINTED,
                "a mech on a planet sustains in ground combat",
                s -> owner(s, "mech"),
                UnitType.Mech,
                true,
                true));
        // LRR 87.4a: a unit only sustains when it can be hit; a transported mech is not in the space combat.
        rows.add(spaceOnly(
                PRINTED,
                "a transported mech is not in the space combat",
                s -> {
                    Player owner = owner(s);
                    s.place(owner, SPACE, UnitType.Mech, 1);
                    s.place(owner, SPACE, UnitType.Carrier, 1);
                    return owner;
                },
                UnitType.Mech,
                false,
                false,
                false));

        rows.add(space(
                "schematics",
                "war suns lose SUSTAIN DAMAGE",
                s -> {
                    Player owner = owner(s, "warsun");
                    s.game.addLaw("schematics", null);
                    return owner;
                },
                UnitType.Warsun,
                false,
                false,
                false,
                false));

        rows.add(space(
                "mentak_flagship",
                "other players' ships next to the Fourth Moon cannot sustain",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    Player mentak = s.addPlayer("blue", "mentak", "mentak_flagship");
                    s.place(mentak, SPACE, UnitType.Flagship, 1);
                    return owner;
                },
                UnitType.Dreadnought,
                false,
                false,
                false,
                false));

        rows.add(ground(
                "mentak_mech",
                "other players' ground forces on its planet cannot sustain",
                s -> {
                    Player owner = owner(s, "mech");
                    Player mentak = s.addPlayer("blue", "mentak", "mentak_mech", "infantry");
                    s.place(mentak, PLANET, UnitType.Mech, 1);
                    return owner;
                },
                UnitType.Mech,
                false,
                false));
        rows.add(ground(
                "mentak_mech",
                "Articles of War switches the Moll Terminus off",
                s -> {
                    Player owner = owner(s, "mech");
                    Player mentak = s.addPlayer("blue", "mentak", "mentak_mech");
                    s.place(mentak, PLANET, UnitType.Mech, 1);
                    s.game.addLaw("articles_war", null);
                    return owner;
                },
                UnitType.Mech,
                true,
                true));

        rows.add(space(
                "crimson_flagship",
                "the Quietus on an active breach strips unit abilities",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    quietus(s);
                    return owner;
                },
                UnitType.Dreadnought,
                false,
                false,
                false,
                false));
        rows.add(ground(
                "crimson_flagship",
                "the Quietus also reaches ground forces in the system",
                s -> {
                    Player owner = owner(s, "mech");
                    quietus(s);
                    return owner;
                },
                UnitType.Mech,
                false,
                false));

        // Quantum Manipulator: may cancel hits against the Nomad's ships, including anti-fighter barrage hits
        // (Nomad ruling II). Raid Formation picks ships, so auto anti-fighter barrage never uses it.
        rows.add(space(
                "nomad_mech",
                "Quantum Manipulator covers ships from the space area",
                s -> {
                    Player owner = owner(s, "nomad_mech", "carrier");
                    s.place(owner, SPACE, UnitType.Mech, 1);
                    s.place(owner, SPACE, UnitType.Carrier, 1);
                    return owner;
                },
                UnitType.Mech,
                true,
                true,
                true,
                false));
        // Articles of War: "All mechs lose their printed abilities except for SUSTAIN DAMAGE." The Quantum
        // Manipulator's space-area use is its printed ability, so the law suppresses that grant, AFB included.
        rows.add(spaceOnly(
                "articles_war",
                "Articles of War switches Quantum Manipulator off in the space area",
                s -> {
                    Player owner = owner(s, "nomad_mech", "carrier");
                    s.place(owner, SPACE, UnitType.Mech, 1);
                    s.place(owner, SPACE, UnitType.Carrier, 1);
                    s.game.addLaw("articles_war", null);
                    return owner;
                },
                UnitType.Mech,
                false,
                false,
                false));
        // SUSTAIN DAMAGE itself survives Articles of War, so the mech still sustains on a planet.
        rows.add(ground(
                "articles_war",
                "Quantum Manipulator keeps SUSTAIN DAMAGE on a planet under Articles of War",
                s -> {
                    Player owner = owner(s, "nomad_mech", "infantry");
                    s.place(owner, PLANET, UnitType.Mech, 1);
                    s.place(owner, PLANET, UnitType.Infantry, 1);
                    s.game.addLaw("articles_war", null);
                    return owner;
                },
                UnitType.Mech,
                true,
                true));
        rows.add(ground(
                "nomad_mech",
                "Quantum Manipulator sustains on a planet",
                s -> {
                    Player owner = owner(s, "nomad_mech", "infantry");
                    s.place(owner, PLANET, UnitType.Mech, 1);
                    s.place(owner, PLANET, UnitType.Infantry, 1);
                    return owner;
                },
                UnitType.Mech,
                true,
                true));

        rows.add(spaceOnly(
                "nekro_flagship",
                "The Alastor brings ground forces into the space combat",
                s -> {
                    Player owner = owner(s, "nekro_flagship", "mech");
                    s.place(owner, SPACE, UnitType.Flagship, 1);
                    s.place(owner, SPACE, UnitType.Mech, 1);
                    return owner;
                },
                UnitType.Mech,
                true,
                true,
                true));
        rows.add(manualSpaceOnPlanet(
                "nekro_flagship",
                "The Alastor also brings ground forces from planets",
                s -> {
                    Player owner = owner(s, "nekro_flagship", "mech");
                    s.place(owner, SPACE, UnitType.Flagship, 1);
                    s.place(owner, PLANET, UnitType.Mech, 1);
                    return owner;
                },
                true));

        rows.add(manualSpaceOnPlanet(
                "naaz_voltron",
                "Eidolon Maximum fights in space combat",
                s -> {
                    Player owner = owner(s, "naaz_voltron", "carrier");
                    s.place(owner, SPACE, UnitType.Carrier, 1);
                    s.place(owner, PLANET, UnitType.Mech, 1);
                    return owner;
                },
                true));

        rows.add(space(
                "metalivoidshielding",
                "a non-fighter ship may sustain as if it had the ability",
                s -> {
                    Player owner = owner(s, "carrier");
                    s.place(owner, SPACE, UnitType.Carrier, 1);
                    owner.addRelic("metalivoidshielding");
                    return owner;
                },
                UnitType.Carrier,
                true,
                true,
                true,
                false));

        rows.add(space(
                "superweaponglatison",
                "other players' units next to Glatison lose SUSTAIN DAMAGE",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    glatison(s);
                    return owner;
                },
                UnitType.Dreadnought,
                false,
                false,
                false,
                false));
        rows.add(ground(
                "superweaponglatison",
                "Glatison also reaches ground forces",
                s -> {
                    Player owner = owner(s, "mech");
                    glatison(s);
                    return owner;
                },
                UnitType.Mech,
                false,
                false));
        rows.add(spaceOnly(
                "superweaponglatison",
                "suppressors beat granted sustain (Old Glory)",
                s -> {
                    Player owner = owner(s, "fighter");
                    s.place(owner, SPACE, UnitType.Fighter, 2);
                    s.game.setStoredValue(OLD_GLORY_KEY + owner.getFaction() + "_" + s.tile.getPosition(), "2");
                    glatison(s);
                    return owner;
                },
                UnitType.Fighter,
                false,
                false,
                false));

        rows.add(spaceOnly(
                "belkosea_monument",
                "Armageddon Project's Glatison strips SUSTAIN DAMAGE",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    s.game.setMonumentsMode(true);
                    Player belkosea = s.addPlayer("blue", "belkosea", "belkosea_monument");
                    s.place(belkosea, PLANET, UnitType.Monument, 1);
                    belkosea.addRelic("superweaponglatison");
                    s.game.setStoredValue(
                            "armageddonProjectSuperweapons_" + belkosea.getFaction(), "superweaponglatison");
                    return owner;
                },
                UnitType.Dreadnought,
                false,
                false,
                false));

        rows.add(space(
                "dsxinystar",
                "Veiled Networking (Star) keeps SUSTAIN DAMAGE against Glatison",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    owner.addTech("dsxinystar");
                    glatison(s);
                    return owner;
                },
                UnitType.Dreadnought,
                true,
                true,
                true,
                true));
        rows.add(spaceOnly(
                "dsxinystar",
                "Veiled Networking (Star) does not stop \"cannot use\" effects",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    owner.addTech("dsxinystar");
                    Player mentak = s.addPlayer("blue", "mentak", "mentak_flagship");
                    s.place(mentak, SPACE, UnitType.Flagship, 1);
                    return owner;
                },
                UnitType.Dreadnought,
                false,
                false,
                false));

        rows.add(space(
                "entropic_scar",
                "units in an entropic scar lose their unit abilities",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    s.tile.addToken(SCAR_TOKEN, SPACE);
                    return owner;
                },
                UnitType.Dreadnought,
                false,
                false,
                false,
                false));
        rows.add(spaceOnly(
                "entropic_scar",
                "the combat tile counts, not the active system",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    farActiveSystem(s).addToken(SCAR_TOKEN, SPACE);
                    return owner;
                },
                UnitType.Dreadnought,
                true,
                true,
                true));
        rows.add(spaceOnly(
                "nivynbt",
                "Anomaly Stabilization ignores entropic scars",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    owner.addBreakthrough("nivynbt");
                    owner.setBreakthroughUnlocked("nivynbt", true);
                    s.tile.addToken(SCAR_TOKEN, SPACE);
                    return owner;
                },
                UnitType.Dreadnought,
                true,
                true,
                true));
        rows.add(spaceOnly(
                "tf-singularitypoint",
                "Singularity Point ignores entropic scars",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    owner.addTech("tf-singularitypoint");
                    s.tile.addToken(SCAR_TOKEN, SPACE);
                    return owner;
                },
                UnitType.Dreadnought,
                true,
                true,
                true));

        rows.add(space(
                "mortheuscommander",
                "Komat gives a defending non-fighter ship SUSTAIN DAMAGE",
                s -> {
                    Player owner = owner(s, "carrier");
                    s.place(owner, SPACE, UnitType.Carrier, 1);
                    owner.addLeader("mortheuscommander");
                    owner.unsafeGetLeader("mortheuscommander").setLocked(false);
                    return owner;
                },
                UnitType.Carrier,
                true,
                true,
                true,
                false));

        rows.add(space(
                "khrask_flagship",
                "Splintering Gale gives non-fighter ships SUSTAIN DAMAGE",
                s -> {
                    Player owner = owner(s, "khrask_flagship", "carrier");
                    s.place(owner, SPACE, UnitType.Flagship, 1);
                    s.place(owner, SPACE, UnitType.Carrier, 1);
                    return owner;
                },
                UnitType.Carrier,
                true,
                true,
                true,
                false));

        rows.add(spaceOnly(
                "radiant_grafting_flight",
                "structures in space cannot use unit abilities",
                s -> {
                    Player owner = owner(s, "pds", "carrier");
                    owner.addAbility("radiant_grafting_flight");
                    owner.addAbility("radiant_grafting_scales");
                    s.place(owner, SPACE, UnitType.Pds, 1);
                    s.place(owner, SPACE, UnitType.Carrier, 1);
                    return owner;
                },
                UnitType.Pds,
                false,
                false,
                false));
        rows.add(ground(
                "radiant_grafting_flight",
                "structures on planets keep their abilities",
                s -> {
                    Player owner = owner(s, "pds", "infantry");
                    owner.addAbility("radiant_grafting_flight");
                    owner.addAbility("radiant_grafting_scales");
                    s.place(owner, PLANET, UnitType.Pds, 1);
                    s.place(owner, PLANET, UnitType.Infantry, 1);
                    return owner;
                },
                UnitType.Pds,
                true,
                true));

        rows.add(space(
                "ponthous_flagship",
                "Old Glory gives fighters SUSTAIN DAMAGE",
                s -> {
                    Player owner = owner(s, "fighter", "carrier");
                    s.place(owner, SPACE, UnitType.Fighter, 2);
                    s.place(owner, SPACE, UnitType.Carrier, 1);
                    s.game.setStoredValue(OLD_GLORY_KEY + owner.getFaction() + "_" + s.tile.getPosition(), "2");
                    return owner;
                },
                UnitType.Fighter,
                true,
                true,
                true,
                false));

        rows.add(space(
                "vanguard_mech",
                "Bulwark gives a ship SUSTAIN DAMAGE",
                s -> {
                    Player owner = owner(s, "carrier");
                    s.place(owner, SPACE, UnitType.Carrier, 1);
                    s.game.setStoredValue(
                            BULWARK_KEY + owner.getFaction(), s.tile.getPosition() + "|" + UnitType.Carrier + ";");
                    return owner;
                },
                UnitType.Carrier,
                true,
                true,
                true,
                false));

        rows.add(space(
                "sigma_mentak_flagship_2",
                "ships in or next to Fourth Moon II cannot sustain",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    Player mentak = s.addPlayer("blue", "mentak", "sigma_mentak_flagship_2");
                    s.place(mentak, SPACE, UnitType.Flagship, 1);
                    return owner;
                },
                UnitType.Dreadnought,
                false,
                false,
                false,
                false));

        rows.add(spaceOnly(
                "sigma_nekro_flagship_1",
                "Alastor I brings ground forces from the space area",
                s -> {
                    Player owner = owner(s, "sigma_nekro_flagship_1", "mech");
                    s.place(owner, SPACE, UnitType.Flagship, 1);
                    s.place(owner, SPACE, UnitType.Mech, 1);
                    return owner;
                },
                UnitType.Mech,
                true,
                true,
                true));
        rows.add(manualSpaceOnPlanet(
                "sigma_nekro_flagship_1",
                "Alastor I leaves planets out",
                s -> {
                    Player owner = owner(s, "sigma_nekro_flagship_1", "mech");
                    s.place(owner, SPACE, UnitType.Flagship, 1);
                    s.place(owner, PLANET, UnitType.Mech, 1);
                    return owner;
                },
                false));
        rows.add(spaceOnly(
                "sigma_nekro_flagship_2",
                "Alastor II brings ground forces into space combat",
                s -> {
                    Player owner = owner(s, "sigma_nekro_flagship_2", "mech");
                    s.place(owner, SPACE, UnitType.Flagship, 1);
                    s.place(owner, SPACE, UnitType.Mech, 1);
                    return owner;
                },
                UnitType.Mech,
                true,
                true,
                true));
        rows.add(manualSpaceOnPlanet(
                "sigma_nekro_flagship_2",
                "Alastor II brings ground forces from planets",
                s -> {
                    Player owner = owner(s, "sigma_nekro_flagship_2", "mech");
                    s.place(owner, SPACE, UnitType.Flagship, 1);
                    s.place(owner, PLANET, UnitType.Mech, 1);
                    return owner;
                },
                true));

        rows.add(space(
                "tf-smotheringpresence",
                "units next to Smothering Presence structures lose abilities",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    smotheringPresence(s, s.tile.getPosition());
                    return owner;
                },
                UnitType.Dreadnought,
                false,
                false,
                false,
                false));
        rows.add(spaceOnly(
                "tf-smotheringpresence",
                "the combat tile counts, not the active system",
                s -> {
                    Player owner = owner(s, "dreadnought");
                    smotheringPresence(s, farActiveSystem(s).getPosition());
                    return owner;
                },
                UnitType.Dreadnought,
                true,
                true,
                true));

        rows.add(spaceOnly(
                "purpletf_mech",
                "Starlancer XI fights in space combat as a ship",
                s -> {
                    Player owner = owner(s, "purpletf_mech", "carrier");
                    s.place(owner, SPACE, UnitType.Mech, 1);
                    s.place(owner, SPACE, UnitType.Carrier, 1);
                    return owner;
                },
                UnitType.Mech,
                true,
                true,
                true));

        return rows;
    }

    static Player owner(SustainScenario scenario, String... ownedUnits) {
        Player owner = scenario.addPlayer("red", "sol", ownedUnits);
        if (List.of(ownedUnits).contains("dreadnought")) {
            scenario.place(owner, SPACE, UnitType.Dreadnought, 1);
        }
        if (List.of(ownedUnits).equals(List.of("mech"))) {
            owner.addOwnedUnitByID("infantry");
            scenario.place(owner, PLANET, UnitType.Mech, 1);
            scenario.place(owner, PLANET, UnitType.Infantry, 1);
        }
        if (List.of(ownedUnits).contains("warsun")) {
            scenario.place(owner, SPACE, UnitType.Warsun, 1);
        }
        return owner;
    }

    private static void quietus(SustainScenario scenario) {
        Player crimson = scenario.addPlayer("blue", "crimson", "crimson_flagship");
        scenario.place(crimson, SPACE, UnitType.Flagship, 1);
        scenario.tile.addToken(Constants.TOKEN_BREACH_ACTIVE, SPACE);
    }

    private static void glatison(SustainScenario scenario) {
        Player xin = scenario.addPlayer("blue", "xin", "destroyer");
        scenario.place(xin, SPACE, UnitType.Destroyer, 1);
        xin.addRelic("superweaponglatison");
        scenario.tile.addToken("token_superweapon_glatison.png", PLANET);
    }

    private static void smotheringPresence(SustainScenario scenario, String structurePosition) {
        Player crimson = scenario.addPlayer("blue", "crimson", "pds");
        crimson.addTech("tf-smotheringpresence");
        scenario.game
                .getTileByPosition(structurePosition)
                .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Pds, crimson.getColorID()), 1);
    }

    private static Tile farActiveSystem(SustainScenario scenario) {
        Tile far = new Tile("20", FAR_POSITION);
        scenario.game.setTile(far);
        scenario.game.setActiveSystem(FAR_POSITION);
        return far;
    }

    private static Row space(
            String rule,
            String condition,
            Function<SustainScenario, Player> setup,
            UnitType unit,
            boolean manualSpace,
            boolean autoSpace,
            boolean manualAfb,
            boolean autoAfb) {
        Map<Path, Boolean> expected = new EnumMap<>(Path.class);
        expected.put(Path.MANUAL_SPACE, manualSpace);
        expected.put(Path.AUTO_SPACE, autoSpace);
        expected.put(Path.MANUAL_AFB, manualAfb);
        expected.put(Path.AUTO_AFB, autoAfb);
        return new Row(rule, condition, setup, unit, SPACE, expected);
    }

    private static Row spaceOnly(
            String rule,
            String condition,
            Function<SustainScenario, Player> setup,
            UnitType unit,
            boolean manualSpace,
            boolean autoSpace,
            boolean manualAfb) {
        Map<Path, Boolean> expected = new EnumMap<>(Path.class);
        expected.put(Path.MANUAL_SPACE, manualSpace);
        expected.put(Path.AUTO_SPACE, autoSpace);
        expected.put(Path.MANUAL_AFB, manualAfb);
        return new Row(rule, condition, setup, unit, SPACE, expected);
    }

    private static Row ground(
            String rule,
            String condition,
            Function<SustainScenario, Player> setup,
            UnitType unit,
            boolean manualGround,
            boolean autoGround) {
        Map<Path, Boolean> expected = new EnumMap<>(Path.class);
        expected.put(Path.MANUAL_GROUND, manualGround);
        expected.put(Path.AUTO_GROUND, autoGround);
        return new Row(rule, condition, setup, unit, PLANET, expected);
    }

    private static Row manualSpaceOnPlanet(
            String rule, String condition, Function<SustainScenario, Player> setup, boolean manualSpace) {
        return new Row(rule, condition, setup, UnitType.Mech, PLANET, Map.of(Path.MANUAL_SPACE, manualSpace));
    }
}
