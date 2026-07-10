package ti4.service.combat.v2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Space;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.json.JsonMapperManager;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.breakthrough.ValefarZService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.v2.CombatV2DiceData.AdditionalDiceBasis;
import ti4.service.combat.v2.CombatV2DiceData.AdditionalRollRule;
import ti4.service.combat.v2.CombatV2DiceData.HitMatch;
import ti4.service.combat.v2.CombatV2DiceData.HitRule;
import ti4.service.combat.v2.CombatV2DiceData.HitRuleTiming;
import ti4.service.combat.v2.CombatV2DiceData.ModifierDuration;
import ti4.service.combat.v2.CombatV2DiceData.ModifierEffect;
import ti4.service.combat.v2.CombatV2DiceData.RerollRule;
import ti4.service.combat.v2.CombatV2DiceData.RerollSelector;
import ti4.service.combat.v2.CombatV2DiceData.RollModifier;
import ti4.service.combat.v2.CombatV2DiceData.RollPlan;
import ti4.service.combat.v2.CombatV2DiceData.RollSource;
import ti4.service.combat.v2.CombatV2DiceData.StatModifier;
import ti4.service.combat.v2.CombatV2DiceData.StatOperation;
import ti4.service.combat.v2.CombatV2DiceData.UnitRollStat;
import ti4.service.combat.v2.CombatV2DiceData.ValueModifier;
import ti4.service.combat.v2.CombatV2RollData.BombardmentModifiers;
import ti4.service.combat.v2.CombatV2RollData.Context;
import ti4.service.combat.v2.CombatV2RollData.ResolvedModifier;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import tools.jackson.core.type.TypeReference;

/** Contains every modifier rule used by the Combat V2 roll system. */
@UtilityClass
public class CombatV2Modifiers {

    private static final List<Rule> RULES = allRules();

    private static List<Rule> allRules() {
        List<Rule> rules = new ArrayList<>();
        rules.addAll(universalNumericRules());
        rules.addAll(combatRoundRules());
        rules.addAll(antiFighterBarrageRules());
        rules.addAll(bombardmentRules());
        rules.addAll(spaceCannonRules());
        rules.addAll(rerollAndAdditionalDiceRules());
        return List.copyOf(rules);
    }

    // Universal numeric and initial-dice rules
    private static List<Rule> universalNumericRules() {
        return List.of(
                rule(
                        "plus1_for_each_system_with_planets",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "bastion_flagship")),
                        (context, scope) -> (context.hasUnitSource("bastion_flagship")) && (scope.is("fs")),
                        unit -> unit.toHit(unit.controlledNonHomeSystems())),
                rule(
                        "roll_1_for_galvanize_combat",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.hasGalvanizedUnit()),
                        unit -> unit.extraDice(unit.galvanizedCount())),
                rule(
                        "roll_1_for_galvanize_afb",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.hasGalvanizedUnit()),
                        unit -> unit.extraDice(unit.galvanizedCount())),
                rule(
                        "roll_1_for_galvanize_bombard",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) ->
                                (context.custom() && context.hasGalvanizedUnit() && context.hasBombardmentGalvanize()),
                        unit -> unit.extraDice(unit.galvanizedCount())),
                rule(
                        "roll_1_for_galvanize_spacecannon_offense",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.hasGalvanizedUnit()),
                        unit -> unit.extraDice(unit.galvanizedCount())),
                rule(
                        "roll_1_for_galvanized",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.hasGalvanizedUnit()),
                        unit -> unit.extraDice(unit.galvanizedCount())),
                rule(
                        "plus2_1round_all",
                        CombatRollType.combatround,
                        ModifierDuration.ONE_COMBAT_ROUND,
                        false,
                        List.of(source("action_cards", "tk-exhort"), source("action_cards", "tk-exhort2")),
                        (context, scope) ->
                                ((context.hasActionCard("tk-exhort") || context.hasActionCard("tk-exhort2"))),
                        unit -> unit.toHit(2)),
                rule(
                        "erelim_extra_die_when_damaged",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "tk-erelim")),
                        (context, scope) -> (context.hasUnitSource("tk-erelim")) && (scope.is("dn")),
                        unit -> unit.extraDice(unit.damagedUnits())),
                rule(
                        "tekklarelite_extra_hits",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "tk-tekklarelite")),
                        (context, scope) -> (context.hasUnitSource("tk-tekklarelite")) && (scope.is("gf")),
                        unit -> unit.bonusHits(HitMatch.SUCCESS, 0, 1)),
                rule(
                        "minus4_bombard",
                        CombatRollType.bombardment,
                        ModifierDuration.ONE_TACTICAL_ACTION,
                        true,
                        List.of(source("action_cards", "bunker")),
                        (context, scope) -> (context.hasActionCard("bunker")),
                        unit -> unit.toHit(-4)),
                rule(
                        "plus1_1tacticalaction_all",
                        CombatRollType.combatround,
                        ModifierDuration.ONE_TACTICAL_ACTION,
                        false,
                        List.of(source("leader", "vaylerianhero")),
                        (context, scope) -> (context.hasLeader("vaylerianhero")),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_roll_mechs_naaz_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.naazFlagshipPresent()) && (scope.is("mf")),
                        unit -> unit.extraDice((1) * unit.quantity())),
                rule(
                        "plus_1_mechs_naaz_sigma_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_naazrokha_flagship_1")),
                        (context, scope) -> (context.hasUnitSource("sigma_naazrokha_flagship_1")) && (scope.is("mf")),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_1round_all",
                        CombatRollType.combatround,
                        ModifierDuration.ONE_COMBAT_ROUND,
                        false,
                        List.of(
                                source("tech", "sc"),
                                source("action_cards", "morale_boost_ds"),
                                source("action_cards", "mb1"),
                                source("action_cards", "mb2"),
                                source("action_cards", "mb3"),
                                source("action_cards", "mb4")),
                        (context, scope) -> ((context.hasTech("sc")
                                || context.hasActionCard("morale_boost_ds")
                                || context.hasActionCard("mb1")
                                || context.hasActionCard("mb2")
                                || context.hasActionCard("mb3")
                                || context.hasActionCard("mb4"))),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_1round_wild",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(
                                source("action_cards", "morale_boost_ds"),
                                source("action_cards", "mb1"),
                                source("action_cards", "mb2"),
                                source("action_cards", "mb3"),
                                source("action_cards", "mb4")),
                        (context, scope) -> ((context.hasActionCard("morale_boost_ds")
                                        || context.hasActionCard("mb1")
                                        || context.hasActionCard("mb2")
                                        || context.hasActionCard("mb3")
                                        || context.hasActionCard("mb4"))
                                && context.wildMoraleBoostActive()),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_1round_fighter",
                        CombatRollType.combatround,
                        ModifierDuration.ONE_COMBAT_ROUND,
                        false,
                        List.of(source("action_cards", "f_prototype")),
                        (context, scope) -> (context.hasActionCard("f_prototype")) && (scope.is("ff")),
                        unit -> unit.toHit(2)),
                rule(
                        "minus_1_opponent_tekklar_player_owner",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.opponentTeklarPlayerOwner()),
                        unit -> unit.toHit(-1)),
                rule(
                        "plus1_1invasion_combat",
                        CombatRollType.combatround,
                        ModifierDuration.ONE_COMBAT,
                        false,
                        List.of(
                                source("promissory_notes", "tekklar"),
                                source("promissory_notes", "sigma_tekklar_legion")),
                        (context, scope) -> (context.hasPromissoryNote("tekklar")
                                        || context.hasPromissoryNote("sigma_tekklar_legion"))
                                && scope.groundForce(),
                        unit -> unit.toHit(1)),
                rule(
                        "plus2_1invasion_combat",
                        CombatRollType.combatround,
                        ModifierDuration.ONE_COMBAT,
                        false,
                        List.of(source("promissory_notes", "viability_tekklar_legion")),
                        (context, scope) ->
                                (context.hasPromissoryNote("viability_tekklar_legion")) && (scope.groundForce()),
                        unit -> unit.toHit(2)),
                rule(
                        "plus1_1combat_fighter",
                        CombatRollType.combatround,
                        ModifierDuration.ONE_COMBAT,
                        false,
                        List.of(source("promissory_notes", "dspnkjal")),
                        (context, scope) -> (context.hasPromissoryNote("dspnkjal")) && (scope.is("ff")),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_1combat_all",
                        CombatRollType.combatround,
                        ModifierDuration.ONE_COMBAT,
                        false,
                        List.of(source("action_cards", "shock_troops")),
                        (context, scope) -> (context.hasActionCard("shock_troops")),
                        unit -> unit.toHit(1)),
                rule(
                        "minus1_mod_pds_off_opponent_antimatter",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("opponent_tech", "amd"), source("opponent_tech", "absol_amd")),
                        (context, scope) -> ((context.opponentHasTech("amd") || context.opponentHasTech("absol_amd"))),
                        unit -> unit.toHit(-1)),
                rule(
                        "minus2_mod_pds_off_opponent_antimass",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("opponent_tech", "baldrick_amd")),
                        (context, scope) -> (context.opponentHasTech("baldrick_amd")),
                        unit -> unit.toHit(-1)),
                rule(
                        "jolnar_bonus_hits_9_10",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "jolnar_flagship")),
                        (context, scope) -> (context.hasUnitSource("jolnar_flagship")) && (scope.is("fs")),
                        unit -> unit.bonusHits(HitMatch.AT_LEAST_RESULT, 9, 2)),
                rule(
                        "plus1_roll_plasmascoring_spacecannon_off",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("tech", "ps"), source("tech", "absol_ps"), source("tech", "baldrick_ps")),
                        (context, scope) -> ((context.hasTech("ps")
                                        || context.hasTech("absol_ps")
                                        || context.hasTech("baldrick_ps")))
                                && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "minus1_mod_pds_def_opponent_antimatter",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("opponent_tech", "amd"), source("opponent_tech", "absol_amd")),
                        (context, scope) -> ((context.opponentHasTech("amd") || context.opponentHasTech("absol_amd"))),
                        unit -> unit.toHit(-1)),
                rule(
                        "minus2_mod_pds_def_opponent_antimass",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("opponent_tech", "baldrick_amd")),
                        (context, scope) -> (context.opponentHasTech("baldrick_amd")),
                        unit -> unit.toHit(-1)),
                rule(
                        "plus1_roll_plasmascoring_spacecannon_def",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("tech", "ps"), source("tech", "absol_ps"), source("tech", "baldrick_ps")),
                        (context, scope) -> ((context.hasTech("ps")
                                        || context.hasTech("absol_ps")
                                        || context.hasTech("baldrick_ps")))
                                && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus1_roll_in_nebula",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.nebulaDefender()) && (scope.ship()),
                        unit -> unit.toHit(1)),
                rule(
                        "plus2_roll_in_nebula_cosmic",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.cosmicNebulaDefender()) && (scope.ship()),
                        unit -> unit.toHit(2)),
                rule(
                        "plus1_roll_in_arcane_citadel",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.arcaneDefender()) && (scope.groundForce()),
                        unit -> unit.toHit(1)),
                rule(
                        "nivyn_commander",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.hasLeader("nivyncommander")),
                        unit -> unit.extraDice(Math.min(2, unit.damagedUnits()))),
                rule(
                        "lizho_commander",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.lizhoCommanderApplies()) && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "toldar_commander",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.toldarCommanderApplies()) && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus1_vaylerian_hero",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.vaylerianHeroActive()) && (scope.ship()),
                        unit -> unit.toHit(1)),
                rule(
                        "letnev_agent",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.letnevAgentActive()) && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "classified_weapons_mod",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("action_cards", "classified_weapons1")),
                        (context, scope) ->
                                (context.hasActionCard("classified_weapons1") && context.classifiedWeaponsActive())
                                        && (scope.classifiedWeapons()),
                        unit -> unit.extraDice(2)),
                rule(
                        "sol_agent",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.solAgentActive()) && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus1_thalnos",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.thalnosActive()),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_roll_argent_commander_afb",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "argentcommander"), source("tech", "tf-zealous")),
                        (context, scope) -> ((context.hasLeader("argentcommander") || context.hasTech("tf-zealous")))
                                && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus1_roll_asail_afb",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("ability", "marionettes")),
                        (context, scope) -> (context.hasAbility("marionettes") && context.opponentHasBeenAssailed()),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_roll_asail_spacecannon_off",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("ability", "marionettes")),
                        (context, scope) -> (context.hasAbility("marionettes") && context.opponentHasBeenAssailed()),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_roll_asail_spacecannon_def",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("ability", "marionettes")),
                        (context, scope) -> (context.hasAbility("marionettes") && context.opponentHasBeenAssailed()),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_roll_asail_bombard",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("ability", "marionettes")),
                        (context, scope) -> (context.hasAbility("marionettes") && context.opponentHasBeenAssailed()),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_roll_asail_combat",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("ability", "marionettes")),
                        (context, scope) -> (context.hasAbility("marionettes") && context.opponentHasBeenAssailed()),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_per_opponent_sftt",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("breakthrough", "winnubt")),
                        (context, scope) -> (context.hasBreakthrough("winnubt") && context.opponentHasSupport()),
                        unit -> unit.toHit(unit.opponentSupports())),
                rule(
                        "plus1_roll_plasmascoring_afb",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("tech", "baldrick_ps")),
                        (context, scope) -> (context.hasTech("baldrick_ps")) && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "minus1_roll_tnelis_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.tnelisFlagshipOpposing()) && (scope.best()),
                        unit -> unit.extraDice(-1)),
                rule(
                        "plus1_roll_cheiran_fs_afb",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "cheiran_flagship")),
                        (context, scope) -> (context.hasUnitSource("cheiran_flagship") && context.nextToStructure())
                                && (scope.is("fs")),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus1_roll_argent_commander_bombard",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "argentcommander"), source("tech", "tf-zealous")),
                        (context, scope) -> ((context.hasLeader("argentcommander") || context.hasTech("tf-zealous"))
                                        && context.bombardmentSource("argentcommander"))
                                && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus1_roll_argent_commander_spacecannonoffence",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "argentcommander"), source("tech", "tf-zealous")),
                        (context, scope) -> ((context.hasLeader("argentcommander") || context.hasTech("tf-zealous")))
                                && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus1_roll_argent_commander_spacecannondefence",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "argentcommander"), source("tech", "tf-zealous")),
                        (context, scope) -> ((context.hasLeader("argentcommander") || context.hasTech("tf-zealous")))
                                && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus_2_sigma_argent_spacecannonoffence",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_argent_flagship_1")),
                        (context, scope) ->
                                (context.hasUnitSource("sigma_argent_flagship_1") && context.sigmaArgentOnePresent()),
                        unit -> unit.toHit(2)),
                rule(
                        "minus_2_sigma_argent_spacecannonoffence",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        true,
                        List.of(source("unit", "sigma_argent_flagship_1")),
                        (context, scope) ->
                                (context.hasUnitSource("sigma_argent_flagship_1") && context.sigmaArgentOnePresent()),
                        unit -> unit.toHit(-2)),
                rule(
                        "plus_2_sigma_argent_spacecannondefence",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_argent_flagship_1")),
                        (context, scope) ->
                                (context.hasUnitSource("sigma_argent_flagship_1") && context.sigmaArgentOnePresent()),
                        unit -> unit.toHit(2)),
                rule(
                        "minus_2_sigma_argent_spacecannondefence",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        true,
                        List.of(source("unit", "sigma_argent_flagship_1")),
                        (context, scope) ->
                                (context.hasUnitSource("sigma_argent_flagship_1") && context.sigmaArgentOnePresent()),
                        unit -> unit.toHit(-2)),
                rule(
                        "plus_3_sigma_argent_spacecannonoffence",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_argent_flagship_2")),
                        (context, scope) ->
                                (context.hasUnitSource("sigma_argent_flagship_2") && context.sigmaArgentTwoPresent()),
                        unit -> unit.toHit(3)),
                rule(
                        "minus_3_sigma_argent_spacecannonoffence",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        true,
                        List.of(source("unit", "sigma_argent_flagship_2")),
                        (context, scope) ->
                                (context.hasUnitSource("sigma_argent_flagship_2") && context.sigmaArgentTwoPresent()),
                        unit -> unit.toHit(-3)),
                rule(
                        "plus_3_sigma_argent_spacecannondefence",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_argent_flagship_2")),
                        (context, scope) ->
                                (context.hasUnitSource("sigma_argent_flagship_2") && context.sigmaArgentTwoPresent()),
                        unit -> unit.toHit(3)),
                rule(
                        "minus_3_sigma_argent_spacecannondefence",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        true,
                        List.of(source("unit", "sigma_argent_flagship_2")),
                        (context, scope) ->
                                (context.hasUnitSource("sigma_argent_flagship_2") && context.sigmaArgentTwoPresent()),
                        unit -> unit.toHit(-3)),
                rule(
                        "plus1_roll_plasmascoring",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("tech", "ps"), source("tech", "absol_ps"), source("tech", "baldrick_ps")),
                        (context, scope) -> ((context.hasTech("ps")
                                                || context.hasTech("absol_ps")
                                                || context.hasTech("baldrick_ps"))
                                        && context.bombardmentSource("plasmascoring"))
                                && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "minus1_always_all",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("ability", "fragile")),
                        (context, scope) -> (context.hasAbility("fragile")),
                        unit -> unit.toHit(-1)),
                rule(
                        "plus1_always_all",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("ability", "unrelenting"), source("tech", "tf-unrelenting")),
                        (context, scope) -> ((context.hasAbility("unrelenting") || context.hasTech("tf-unrelenting"))),
                        unit -> unit.toHit(1)),
                rule(
                        "plus2_fracture_all",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("tech", "tf-planesplitter")),
                        (context, scope) -> (context.hasTech("tf-planesplitter") && context.fractureCombat()),
                        unit -> unit.toHit(2)),
                rule(
                        "plus1_fracture_all",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "obsidiancommander")),
                        (context, scope) -> (context.hasLeader("obsidiancommander") && context.fractureCombat()),
                        unit -> unit.toHit(1)),
                rule(
                        "plus2_2_matching_non_ff",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("ability", "rule_of_two")),
                        (context, scope) -> (context.hasAbility("rule_of_two") && context.hasTwoMatchingNonFighters())
                                && (scope.shipExceptFighter()),
                        unit -> unit.toHit(2)),
                rule(
                        "plus1_always_fighter",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("agenda", "prophecy")),
                        (context, scope) -> (context.hasAgenda("prophecy")) && (scope.is("ff")),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_always_fighter_lomega",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("agenda", "little_omega_prophecy")),
                        (context, scope) -> (context.hasAgenda("little_omega_prophecy")) && (scope.is("ff")),
                        unit -> unit.toHit(1)),
                rule(
                        "plus_x_frag_always_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "bentor_flagship")),
                        (context, scope) -> (context.hasUnitSource("bentor_flagship")) && (scope.is("fs")),
                        unit -> unit.toHit(unit.fragmentTypes())),
                rule(
                        "plus_x_code_always_mech",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "toldar_mech")),
                        (context, scope) -> (context.hasUnitSource("toldar_mech")) && (scope.is("mf")),
                        unit -> unit.toHit(unit.codeValue())),
                rule(
                        "plus_x_law_always_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "edyn_flagship")),
                        (context, scope) -> (context.hasUnitSource("edyn_flagship")) && (scope.is("fs")),
                        unit -> unit.toHit(unit.lawCount())),
                rule(
                        "plus_half_unit_tech_always_mech",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "mirveda_mech")),
                        (context, scope) -> (context.hasUnitSource("mirveda_mech")) && (scope.is("mf")),
                        unit -> unit.toHit(unit.unitUpgradeCount() / 2)),
                rule(
                        "plus_2x_destroyers_always_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "nokar_flagship")),
                        (context, scope) -> (context.hasUnitSource("nokar_flagship")) && (scope.is("fs")),
                        unit -> unit.toHit(unit.destroyerCount() / 2)),
                rule(
                        "plus_1_always_other_units_with_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sardakk_flagship"), source("unit", "sigma_norr_flagship_1")),
                        (context, scope) -> ((context.hasUnitSource("sardakk_flagship")
                                        || context.hasUnitSource("sigma_norr_flagship_1")))
                                && (!scope.is("fs")),
                        unit -> unit.toHit(1)),
                rule(
                        "plus_1_always_with_flagship_combat",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_norr_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_norr_flagship_2")),
                        unit -> unit.toHit(1)),
                rule(
                        "plus_1_always_with_flagship_bombard",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_norr_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_norr_flagship_2")),
                        unit -> unit.toHit(1)),
                rule(
                        "plus_1_always_with_flagship_afb",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_norr_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_norr_flagship_2")),
                        unit -> unit.toHit(1)),
                rule(
                        "plus_1_always_with_flagship_cannon_off",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_norr_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_norr_flagship_2")),
                        unit -> unit.toHit(1)),
                rule(
                        "plus_1_always_with_flagship_cannon_def",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_norr_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_norr_flagship_2")),
                        unit -> unit.toHit(1)),
                rule(
                        "plus_x_opponent_unit_tech_always_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "zealots_flagship")),
                        (context, scope) -> (context.hasUnitSource("zealots_flagship")) && (scope.is("fs")),
                        unit -> unit.toHit(unit.opponentUnitUpgradeCount())),
                rule(
                        "plus_2_opponent_cc_not_in_fleet_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "mahact_flagship"), source("unit", "sigma_mahact_flagship_2")),
                        (context, scope) -> ((context.hasUnitSource("mahact_flagship")
                                                || context.hasUnitSource("sigma_mahact_flagship_2"))
                                        && context.opponentCommandCounterAbsent())
                                && (scope.is("fs")),
                        unit -> unit.toHit(2)),
                rule(
                        "plus_1_opponent_cc_not_in_fleet_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_mahact_flagship_1")),
                        (context, scope) -> context.hasUnitSource("sigma_mahact_flagship_1")
                                && context.opponentCommandCounterAbsent()
                                && scope.is("fs"),
                        unit -> unit.toHit(2)),
                rule(
                        "plus1_roll_cheiran_fs_combat",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "cheiran_flagship")),
                        (context, scope) -> (context.hasUnitSource("cheiran_flagship") && context.nextToStructure())
                                && (scope.is("fs")),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus_x_opponent_faction_tech_always_mech",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "zealots_mech")),
                        (context, scope) -> (context.hasUnitSource("zealots_mech")) && (scope.is("mf")),
                        unit -> unit.toHit(unit.opponentFactionTechCount())),
                rule(
                        "plus_2_opponent_frag_conditional_mech",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "naalu_mech")),
                        (context, scope) -> (context.hasUnitSource("naalu_mech") && context.opponentHasFragments())
                                && (scope.is("mf")),
                        unit -> unit.toHit(2)),
                rule(
                        "plus_1_inf_with_mech",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "jolnar_mech")),
                        (context, scope) ->
                                (context.hasUnitSource("jolnar_mech") && context.fragileIsActive()) && (scope.is("gf")),
                        unit -> unit.toHit(1)),
                rule(
                        "plus_2_opponent_stolen_faction_tech_conditional_mech",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "nekro_mech")),
                        (context, scope) -> (context.hasUnitSource("nekro_mech") && context.opponentFactionTechStolen())
                                && (scope.is("mf")),
                        unit -> unit.toHit(2)),
                rule(
                        "plus_2_mr_legendary_home_conditional",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "winnucommander")),
                        (context, scope) ->
                                (context.hasLeader("winnucommander") && context.planetIsHomeMecatolOrLegendary()),
                        unit -> unit.toHit(2)),
                rule(
                        "plus_1_always_afb",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "kolumecommander"), source("relic", "starfall_array")),
                        (context, scope) ->
                                ((context.hasLeader("kolumecommander") || context.hasRelic("starfall_array"))),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_roll_starfall_afb",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("relic", "starfall_array")),
                        (context, scope) -> (context.hasRelic("starfall_array")) && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus1_roll_starfall_spacecannondefence",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("relic", "starfall_array")),
                        (context, scope) -> (context.hasRelic("starfall_array")) && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus_1_always_space_cannon_defence",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "kolumecommander"), source("relic", "starfall_array")),
                        (context, scope) ->
                                ((context.hasLeader("kolumecommander") || context.hasRelic("starfall_array"))),
                        unit -> unit.toHit(1)),
                rule(
                        "plus_1_always_space_cannon_offense",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "kolumecommander"), source("relic", "starfall_array")),
                        (context, scope) ->
                                ((context.hasLeader("kolumecommander") || context.hasRelic("starfall_array"))),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_roll_starfall_spacecannonoffence",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("relic", "starfall_array")),
                        (context, scope) -> (context.hasRelic("starfall_array")) && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus_0_always_space_cannon_offense",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "mirvedacommander")),
                        (context, scope) -> (context.hasLeader("mirvedacommander")),
                        unit -> unit.toHit(0)),
                rule(
                        "plus_1_always_bombardment",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "kolumecommander"), source("relic", "starfall_array")),
                        (context, scope) ->
                                ((context.hasLeader("kolumecommander") || context.hasRelic("starfall_array"))),
                        unit -> unit.toHit(1)),
                rule(
                        "plus1_roll_starfall_bombardment",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("relic", "starfall_array")),
                        (context, scope) -> (context.hasRelic("starfall_array")) && (scope.best()),
                        unit -> unit.extraDice(1)),
                rule(
                        "plus1_roll_bluetf_mech",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.blueMechApplies()) && (scope.bestCapacity()),
                        unit -> unit.extraDice(unit.mechsInSpace())),
                rule(
                        "plus_x_frag_always_flagship_afb",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "bentor_flagship")),
                        (context, scope) -> (context.hasUnitSource("bentor_flagship")) && (scope.is("fs")),
                        unit -> unit.toHit(unit.fragmentTypes())),
                rule(
                        "plus_x_frag_always_flagship_spacecannon",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "bentor_flagship")),
                        (context, scope) -> (context.hasUnitSource("bentor_flagship")) && (scope.is("fs")),
                        unit -> unit.toHit(unit.fragmentTypes())),
                rule(
                        "plus_x_frag_always_flagship_bombardment",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "bentor_flagship")),
                        (context, scope) -> (context.hasUnitSource("bentor_flagship")) && (scope.is("fs")),
                        unit -> unit.toHit(unit.fragmentTypes())),
                rule(
                        "miltymod_cruiser2_bonus_hits_9_10",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "miltymod_cruiser2")),
                        (context, scope) -> (context.hasUnitSource("miltymod_cruiser2")) && (scope.is("fs")),
                        unit -> unit.bonusHits(HitMatch.AT_LEAST_RESULT, 9, 1)),
                rule(
                        "roll_1_for_every_enemy_non_fighter",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "winnu_flagship"), source("unit", "sigma_winnu_flagship_1")),
                        (context, scope) -> ((context.hasUnitSource("winnu_flagship")
                                        || context.hasUnitSource("sigma_winnu_flagship_1")))
                                && (scope.is("fs")),
                        unit -> unit.extraDice(unit.opponentNonFighterShips())),
                rule(
                        "roll_1_for_every_enemy_ship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_winnu_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_winnu_flagship_2")) && (scope.is("fs")),
                        unit -> unit.extraDice(unit.opponentShips())),
                rule(
                        "roll_1_for_every_round_of_combat",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "veldyr_flagship")),
                        (context, scope) -> (context.hasUnitSource("veldyr_flagship")) && (scope.is("fs")),
                        unit -> unit.extraDice(unit.combatRound())),
                rule(
                        "plus_1_for_every_round_of_combat",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "uydai_mech")),
                        (context, scope) -> (context.hasUnitSource("uydai_mech")) && (scope.is("mf")),
                        unit -> unit.toHit(unit.combatRound())),
                rule(
                        "plus_1_for_every_nearby_anomaly",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "purpletf_mech")),
                        (context, scope) -> (context.hasUnitSource("purpletf_mech")) && (scope.is("mf")),
                        unit -> unit.toHit(unit.adjacentAnomalies())),
                rule(
                        "roll_1_for_every_adjacent_mech_comb",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "gledge_flagship")),
                        (context, scope) -> (context.hasUnitSource("gledge_flagship")) && (scope.is("fs")),
                        unit -> unit.extraDice(unit.adjacentMechs())),
                rule(
                        "roll_1_for_every_adjacent_asteroid_comb",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "zelian_flagship")),
                        (context, scope) -> (context.hasUnitSource("zelian_flagship")) && (scope.is("fs")),
                        unit -> unit.extraDice(unit.adjacentAsteroids())),
                rule(
                        "roll_1_for_every_adjacent_asteroid_bomb",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "zelian_flagship")),
                        (context, scope) -> (context.hasUnitSource("zelian_flagship")) && (scope.is("fs")),
                        unit -> unit.extraDice(unit.adjacentAsteroids())),
                rule(
                        "roll_1_for_every_adjacent_asteroid_afb",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "zelian_flagship")),
                        (context, scope) -> (context.hasUnitSource("zelian_flagship")) && (scope.is("fs")),
                        unit -> unit.extraDice(unit.adjacentAsteroids())),
                rule(
                        "xan_warsun2_extra_dice_per_space_dock",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "xan_warsun2")),
                        (context, scope) -> (context.hasUnitSource("xan_warsun2")),
                        unit -> unit.extraDice(unit.spaceDocksInTile())),
                rule(
                        "plus_2_xan_mech_with_space_dock",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "xan_mech")),
                        (context, scope) ->
                                (context.hasUnitSource("xan_mech") && context.spaceDockOnHolder()) && (scope.is("mf")),
                        unit -> unit.toHit(2)),
                rule(
                        "arvaxi_mobilization_engine",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.arvaxiEngineActive()),
                        unit -> unit.toHit(unit.arvaxiEngineValue())),
                rule(
                        "plus_1_onyxxa_mech_per_other_mech_on_planet",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "onyxxa_mech")),
                        (context, scope) -> (context.hasUnitSource("onyxxa_mech")),
                        unit -> unit.toHit(unit.otherMechsOnPlanet())),
                rule(
                        "plus_1_opponent_strat_cards_exhausted",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("tech", "baconcg")),
                        (context, scope) -> (context.hasTech("baconcg") && context.opponentStrategyCardsExhausted()),
                        unit -> unit.toHit(1)),
                rule(
                        "plus_2_for_each_mech_space_cannon_defence",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.technotemplarApplies()),
                        unit -> unit.toHit(2 * unit.mechsOnPlanet())),
                rule(
                        "plus_2_for_each_mech_space_cannon_offence",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.technotemplarApplies()),
                        unit -> unit.toHit(2 * unit.mechsOnPlanet())),
                rule(
                        "plus_2_for_each_mech_anti_fighter_barrage",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.custom() && context.technotemplarApplies()),
                        unit -> unit.toHit(2 * unit.mechsOnPlanet())),
                rule(
                        "roll_1_for_every_adjacent_mech_bomb",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "gledge_flagship")),
                        (context, scope) -> (context.hasUnitSource("gledge_flagship")) && (scope.is("fs")),
                        unit -> unit.extraDice(unit.adjacentMechs())),
                rule(
                        "netrunners_control_network",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) ->
                                (context.anyPlayerHasAbility("control_network") && context.controlNetworkPending()),
                        unit -> unit.toHit(-1)),
                rule(
                        "iron_advanced_targeting_systems",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.hasTech("beironats") && context.advancedTargetingSystemsActive())
                                && (scope.is("mf")),
                        unit -> unit.extraDice(unit.quantity())));
    }

    // Combat-round hit and unit-stat rules
    private static List<Rule> combatRoundRules() {
        return List.of(
                rule(
                        "v2_eidolon_landwaster",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("owned_unit", "tf-eidolonlandwaster")),
                        (context, scope) -> (context.ownsUnit("tf-eidolonlandwaster")) && (scope.is("mf")),
                        unit -> unit.stat(UnitRollStat.DICE_PER_UNIT, StatOperation.ADD, 1)),
                rule(
                        "v2_eidolon_terminus",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("owned_unit", "tf-eidolonterminus")),
                        (context, scope) -> (context.ownsUnit("tf-eidolonterminus")) && (scope.is("mf")),
                        unit -> unit.stat(UnitRollStat.HITS_ON, StatOperation.ADD, -1)),
                rule(
                        "v2_echo_of_ascension_die",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("owned_unit", "tf-echoofascension")),
                        (context, scope) -> (context.ownsUnit("tf-echoofascension")) && (scope.is("fs")),
                        unit -> unit.stat(UnitRollStat.DICE_PER_UNIT, StatOperation.ADD, 1)),
                rule(
                        "v2_winnu_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "winnu_flagship")),
                        (context, scope) -> (context.hasUnitSource("winnu_flagship")) && (scope.is("fs")),
                        unit -> unit.effectiveDice() <= 0
                                ? unit.stat(
                                        UnitRollStat.DICE_PER_UNIT, StatOperation.SET, unit.opponentNonFighterShips())
                                : unit.noEffect()),
                rule(
                        "v2_echo_of_ascension_hit",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("owned_unit", "tf-echoofascension")),
                        (context, scope) -> (context.ownsUnit("tf-echoofascension")) && (scope.is("fs")),
                        unit -> unit.stat(UnitRollStat.HITS_ON, StatOperation.ADD, -1)),
                rule(
                        "v2_sigma_arborec_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_arborec_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_arborec_flagship_2")) && (scope.is("fs")),
                        unit -> unit.bonusHits(HitMatch.AT_LEAST_RESULT, 9, 1)),
                rule(
                        "v2_copied_jolnar_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (ValefarZService.hasFlagshipAbility(
                                        context.game(), context.player(), "jolnar_flagship"))
                                && (scope.is("fs")),
                        unit -> unit.bonusHits(HitMatch.AT_LEAST_RESULT, 9, 2)),
                rule(
                        "v2_valor",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("tech", "tf-valortf")),
                        (context, scope) -> (context.hasTech("tf-valortf")),
                        unit -> unit.bonusHits(HitMatch.EXACT_RESULT, 10, 1, HitRuleTiming.BEFORE_REROLLS)),
                rule(
                        "v2_glory_valor",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> context.gloryValorActive(),
                        unit -> unit.bonusHits(
                                HitMatch.EXACT_RESULT, 10, 1, HitRuleTiming.BEFORE_REROLLS_AND_MUNITIONS)));
    }

    // Anti-fighter barrage hit and unit-stat rules
    private static List<Rule> antiFighterBarrageRules() {
        return List.of(
                rule(
                        "v2_sigma_arborec_flagship",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_arborec_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_arborec_flagship_2")) && (scope.is("fs")),
                        unit -> unit.bonusHits(HitMatch.AT_LEAST_RESULT, 9, 1)),
                rule(
                        "v2_copied_jolnar_flagship",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (ValefarZService.hasFlagshipAbility(
                                        context.game(), context.player(), "jolnar_flagship"))
                                && (scope.is("fs")),
                        unit -> unit.bonusHits(HitMatch.AT_LEAST_RESULT, 9, 2)),
                rule(
                        "v2_valor",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("tech", "tf-valortf")),
                        (context, scope) -> (context.hasTech("tf-valortf")),
                        unit -> unit.bonusHits(HitMatch.EXACT_RESULT, 10, 1, HitRuleTiming.BEFORE_REROLLS)));
    }

    // Bombardment hit and unit-stat rules
    private static List<Rule> bombardmentRules() {
        return List.of(
                rule(
                        "v2_sigma_arborec_flagship",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_arborec_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_arborec_flagship_2")) && (scope.is("fs")),
                        unit -> unit.bonusHits(HitMatch.AT_LEAST_RESULT, 9, 1)),
                rule(
                        "v2_copied_jolnar_flagship",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (ValefarZService.hasFlagshipAbility(
                                        context.game(), context.player(), "jolnar_flagship"))
                                && (scope.is("fs")),
                        unit -> unit.bonusHits(HitMatch.AT_LEAST_RESULT, 9, 2)),
                rule(
                        "v2_valor",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("tech", "tf-valortf")),
                        (context, scope) -> (context.hasTech("tf-valortf")),
                        unit -> unit.bonusHits(HitMatch.EXACT_RESULT, 10, 1, HitRuleTiming.BEFORE_REROLLS)));
    }

    // Space-cannon offense and defense hit rules
    private static List<Rule> spaceCannonRules() {
        return List.of(
                rule(
                        "v2_sigma_arborec_flagship",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_arborec_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_arborec_flagship_2")) && (scope.is("fs")),
                        unit -> unit.bonusHits(HitMatch.AT_LEAST_RESULT, 9, 1)),
                rule(
                        "v2_copied_jolnar_flagship",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (ValefarZService.hasFlagshipAbility(
                                        context.game(), context.player(), "jolnar_flagship"))
                                && (scope.is("fs")),
                        unit -> unit.bonusHits(HitMatch.AT_LEAST_RESULT, 9, 2)),
                rule(
                        "v2_valor",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("tech", "tf-valortf")),
                        (context, scope) -> (context.hasTech("tf-valortf")),
                        unit -> unit.bonusHits(HitMatch.EXACT_RESULT, 10, 1, HitRuleTiming.BEFORE_REROLLS)),
                rule(
                        "v2_sigma_arborec_flagship",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_arborec_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_arborec_flagship_2")) && (scope.is("fs")),
                        unit -> unit.bonusHits(HitMatch.AT_LEAST_RESULT, 9, 1)),
                rule(
                        "v2_copied_jolnar_flagship",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (ValefarZService.hasFlagshipAbility(
                                        context.game(), context.player(), "jolnar_flagship"))
                                && (scope.is("fs")),
                        unit -> unit.bonusHits(HitMatch.AT_LEAST_RESULT, 9, 2)),
                rule(
                        "v2_valor",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("tech", "tf-valortf")),
                        (context, scope) -> (context.hasTech("tf-valortf")),
                        unit -> unit.bonusHits(HitMatch.EXACT_RESULT, 10, 1, HitRuleTiming.BEFORE_REROLLS)),
                rule(
                        "v2_zephyrion_commander",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "zephyrioncommander")),
                        (context, scope) -> (context.hasLeader("zephyrioncommander")),
                        unit -> unit.bonusHits(HitMatch.EXACT_RESULT, 10, 1)),
                rule(
                        "v2_zephyrion_commander",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "zephyrioncommander")),
                        (context, scope) -> (context.hasLeader("zephyrioncommander")),
                        unit -> unit.bonusHits(HitMatch.EXACT_RESULT, 10, 1)));
    }

    // Triggered additional rolls and rerolls
    private static List<Rule> rerollAndAdditionalDiceRules() {
        return List.of(
                rule(
                        "v2_sigma_jolnar_flagship",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_jolnar_flagship_1"), source("unit", "sigma_jolnar_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_jolnar_flagship_1")
                                        || context.hasUnitSource("sigma_jolnar_flagship_2"))
                                && (scope.is("fs")),
                        unit -> unit.additionalDice(
                                RollSource.SIGMA_JOL_NAR_FLAGSHIP,
                                AdditionalDiceBasis.HITS,
                                HitMatch.SUCCESS,
                                0,
                                1,
                                true)),
                rule(
                        "v2_kaltrim_commander",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "kaltrimcommander")),
                        (context, scope) -> (context.hasLeader("kaltrimcommander")),
                        unit -> unit.reroll(RollSource.KALTRIM_COMMANDER_ONES, RerollSelector.ONES)),
                rule(
                        "v2_sigma_jolnar_flagship",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_jolnar_flagship_1"), source("unit", "sigma_jolnar_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_jolnar_flagship_1")
                                        || context.hasUnitSource("sigma_jolnar_flagship_2"))
                                && (scope.is("fs")),
                        unit -> unit.additionalDice(
                                RollSource.SIGMA_JOL_NAR_FLAGSHIP,
                                AdditionalDiceBasis.HITS,
                                HitMatch.SUCCESS,
                                0,
                                1,
                                true)),
                rule(
                        "v2_kaltrim_commander",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "kaltrimcommander")),
                        (context, scope) -> (context.hasLeader("kaltrimcommander")),
                        unit -> unit.reroll(RollSource.KALTRIM_COMMANDER_ONES, RerollSelector.ONES)),
                rule(
                        "v2_sigma_jolnar_flagship",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_jolnar_flagship_1"), source("unit", "sigma_jolnar_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_jolnar_flagship_1")
                                        || context.hasUnitSource("sigma_jolnar_flagship_2"))
                                && (scope.is("fs")),
                        unit -> unit.additionalDice(
                                RollSource.SIGMA_JOL_NAR_FLAGSHIP,
                                AdditionalDiceBasis.HITS,
                                HitMatch.SUCCESS,
                                0,
                                1,
                                true)),
                rule(
                        "v2_kaltrim_commander",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "kaltrimcommander")),
                        (context, scope) -> (context.hasLeader("kaltrimcommander")),
                        unit -> unit.reroll(RollSource.KALTRIM_COMMANDER_ONES, RerollSelector.ONES)),
                rule(
                        "v2_sigma_jolnar_flagship",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_jolnar_flagship_1"), source("unit", "sigma_jolnar_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_jolnar_flagship_1")
                                        || context.hasUnitSource("sigma_jolnar_flagship_2"))
                                && (scope.is("fs")),
                        unit -> unit.additionalDice(
                                RollSource.SIGMA_JOL_NAR_FLAGSHIP,
                                AdditionalDiceBasis.HITS,
                                HitMatch.SUCCESS,
                                0,
                                1,
                                true)),
                rule(
                        "v2_kaltrim_commander",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "kaltrimcommander")),
                        (context, scope) -> (context.hasLeader("kaltrimcommander")),
                        unit -> unit.reroll(RollSource.KALTRIM_COMMANDER_ONES, RerollSelector.ONES)),
                rule(
                        "v2_sigma_jolnar_flagship",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("unit", "sigma_jolnar_flagship_1"), source("unit", "sigma_jolnar_flagship_2")),
                        (context, scope) -> (context.hasUnitSource("sigma_jolnar_flagship_1")
                                        || context.hasUnitSource("sigma_jolnar_flagship_2"))
                                && (scope.is("fs")),
                        unit -> unit.additionalDice(
                                RollSource.SIGMA_JOL_NAR_FLAGSHIP,
                                AdditionalDiceBasis.HITS,
                                HitMatch.SUCCESS,
                                0,
                                1,
                                true)),
                rule(
                        "v2_kaltrim_commander",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "kaltrimcommander")),
                        (context, scope) -> (context.hasLeader("kaltrimcommander")),
                        unit -> unit.reroll(RollSource.KALTRIM_COMMANDER_ONES, RerollSelector.ONES)),
                rule(
                        "v2_iron_commander",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("leader", "ironcommander")),
                        (context, scope) -> (context.hasLeader("ironcommander")) && (scope.is("mf")),
                        unit -> unit.reroll(RollSource.IRON_COMMANDER_MISSES, RerollSelector.MISSES)),
                rule(
                        "v2_munitions_reserves",
                        CombatRollType.combatround,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) -> (context.player()
                                        .getFaction()
                                        .equalsIgnoreCase(context.storedValue("munitionsReserves"))
                                && !"true".equalsIgnoreCase(context.storedValue("thalnosPlusOne"))),
                        unit -> unit.reroll(RollSource.MUNITIONS_RESERVES, RerollSelector.MISSES)),
                rule(
                        "v2_jolnar_commander_AFB",
                        CombatRollType.AFB,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) ->
                                (context.hasLeader("jolnarcommander") || context.hasTech("tf-tacticalbrilliance")),
                        unit -> unit.reroll(RollSource.JOL_NAR_COMMANDER_MISSES, RerollSelector.MISSES)),
                rule(
                        "v2_jolnar_commander_SpaceCannonOffence",
                        CombatRollType.SpaceCannonOffence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) ->
                                (context.hasLeader("jolnarcommander") || context.hasTech("tf-tacticalbrilliance")),
                        unit -> unit.reroll(RollSource.JOL_NAR_COMMANDER_MISSES, RerollSelector.MISSES)),
                rule(
                        "v2_jolnar_commander_SpaceCannonDefence",
                        CombatRollType.SpaceCannonDefence,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) ->
                                (context.hasLeader("jolnarcommander") || context.hasTech("tf-tacticalbrilliance")),
                        unit -> unit.reroll(RollSource.JOL_NAR_COMMANDER_MISSES, RerollSelector.MISSES)),
                rule(
                        "v2_jolnar_commander_bombardment",
                        CombatRollType.bombardment,
                        ModifierDuration.PERMANENT,
                        false,
                        List.of(source("custom", "custom")),
                        (context, scope) ->
                                (context.hasLeader("jolnarcommander") || context.hasTech("tf-tacticalbrilliance")),
                        unit -> unit.context().opponent() == unit.context().player()
                                        && unit.context().playerHasTech("proxima")
                                ? unit.reroll(RollSource.JOL_NAR_COMMANDER_HITS, RerollSelector.HITS)
                                : unit.reroll(RollSource.JOL_NAR_COMMANDER_MISSES, RerollSelector.MISSES)));
    }

    // Rule resolution
    static List<ResolvedModifier> resolve(ModifierInputs inputs, BombardmentModifiers bombardment) {
        List<ResolvedModifier> modifiers = new ArrayList<>();
        RuleContext normal = new RuleContext(inputs, bombardment, null);
        for (Rule rule : RULES) {
            if (rule.rollType() != inputs.rollType() || rule.duration() != ModifierDuration.PERMANENT) continue;
            if (appliesToAnyUnit(rule, normal, inputs)) modifiers.add(resolvedModifier(rule, normal));
        }
        addActivatedRules(inputs.player(), false, inputs, bombardment, modifiers);
        if (inputs.opponent() != null && inputs.opponent() != inputs.player()) {
            addActivatedRules(inputs.opponent(), true, inputs, bombardment, modifiers);
        }
        return List.copyOf(modifiers);
    }

    private static void addActivatedRules(
            Player owner,
            boolean applyToOpponent,
            ModifierInputs inputs,
            BombardmentModifiers bombardment,
            List<ResolvedModifier> modifiers) {
        for (CombatModifierActivation activation : owner.getCombatModifierActivations()) {
            for (Rule rule : rulesFor(activation.sourceType(), activation.sourceId())) {
                if (rule.rollType() != inputs.rollType()
                        || rule.applyToOpponent() != applyToOpponent
                        || !valid(
                                activation,
                                rule,
                                owner,
                                inputs.tile().getTileID(),
                                inputs.holder().getName())) continue;
                RuleContext context = new RuleContext(inputs, bombardment, activation);
                if (appliesToAnyUnit(rule, context, inputs)) modifiers.add(resolvedModifier(rule, context));
            }
        }
    }

    // Temporary modifier activation lifecycle
    public static boolean activate(Player player, String sourceType, String sourceId) {
        if (!canActivate(sourceType, sourceId)) return false;
        for (CombatModifierActivation activation : player.getCombatModifierActivations()) {
            if (activation.sourceType().equals(sourceType)
                    && activation.sourceId().equals(sourceId)
                    && activation.turn() == player.getNumberOfTurns()) return true;
        }
        player.getCombatModifierActivations()
                .add(CombatModifierActivation.pending(sourceType, sourceId, player.getNumberOfTurns()));
        return true;
    }

    public static boolean canActivate(String sourceType, String sourceId) {
        return !rulesFor(sourceType, sourceId).isEmpty();
    }

    private static List<Rule> rulesFor(String sourceType, String sourceId) {
        return RULES.stream()
                .filter(rule -> rule.duration() != ModifierDuration.PERMANENT)
                .filter(rule -> rule.sources().stream()
                        .anyMatch(source -> source.type().equals(sourceType)
                                && source.alias().equals(sourceId)))
                .toList();
    }

    static void consumeTemporaryModifiers(Context context, RollPlan plan) {
        consumeActivations(context);
        if (context.rollType() == CombatRollType.combatround
                && context.getFaction().equalsIgnoreCase(context.storedValue("munitionsReserves"))) {
            context.game().removeStoredValue("munitionsReserves");
        }
        if (context.rollType() == CombatRollType.SpaceCannonOffence
                && plan.units().stream()
                        .anyMatch(unit -> unit.dicePerUnit() == 3
                                && "spacedock".equalsIgnoreCase(unit.unit().getBaseType()))) {
            context.game().removeStoredValue("EBSFaction");
        }
        if (context.rollType() == CombatRollType.bombardment
                && plan.units().stream()
                        .anyMatch(unit -> unit.dicePerUnit() > 1
                                && "destroyer".equalsIgnoreCase(unit.unit().getBaseType()))) {
            context.game().removeStoredValue("TnelisAgentFaction");
        }
        if (hasResolvedModifier(context, "plus1_1round_wild")) {
            context.game().removeStoredValue("wildMB" + context.getFaction());
        }
        if (hasResolvedModifier(context, "netrunners_control_network")) clearControlNetwork(context);
        if (hasResolvedModifier(context, "iron_advanced_targeting_systems")) {
            clearAdvancedTargetingSystems(context.game(), context.player());
        }
    }

    static void consumeActivations(Context context) {
        consumeActivations(context.player(), false, context);
        if (context.opponent() != context.player()) {
            consumeActivations(context.opponent(), true, context);
        }
    }

    private static void consumeActivations(Player owner, boolean applyToOpponent, Context context) {
        var activations = owner.getCombatModifierActivations().listIterator();
        while (activations.hasNext()) {
            CombatModifierActivation activation = activations.next();
            if (activation.turn() != owner.getNumberOfTurns()
                    || rulesFor(activation.sourceType(), activation.sourceId()).isEmpty()) {
                activations.remove();
                continue;
            }
            List<Rule> applied = context.resolvedModifiers().stream()
                    .filter(modifier -> activation.equals(modifier.activation()))
                    .map(ResolvedModifier::rule)
                    .filter(rule -> rule.applyToOpponent() == applyToOpponent)
                    .toList();
            if (applied.isEmpty()) continue;
            if (applied.stream()
                    .anyMatch(rule -> rule.duration() == ModifierDuration.ONE_ROLL
                            || rule.duration() == ModifierDuration.ONE_COMBAT_ROUND)) {
                activations.remove();
            } else if (activation.pending()) {
                activations.set(activation.bind(
                        context.getTileId(), context.combatHolder().getName()));
            }
        }
    }

    private static boolean hasResolvedModifier(Context context, String id) {
        return context.resolvedModifiers().stream().anyMatch(modifier -> id.equals(modifier.ruleId()));
    }

    private static void clearControlNetwork(Context context) {
        String suffix = context.getFaction();
        context.game().removeStoredValue("controlNetworkSpaceCannonTile" + suffix);
        context.game().removeStoredValue("controlNetworkSpaceCannonHolder" + suffix);
        context.game().removeStoredValue("controlNetworkSpaceCannonRoll" + suffix);
    }

    private static void clearAdvancedTargetingSystems(Game game, Player player) {
        String suffix = player.getFaction();
        game.removeStoredValue("ironATSActiveTile_" + suffix);
        game.removeStoredValue("ironATSActiveOpponent_" + suffix);
        game.removeStoredValue("ironATSBoundHolder_" + suffix);
    }

    private static boolean valid(
            CombatModifierActivation activation, Rule rule, Player owner, String systemId, String holderName) {
        if (activation.turn() != owner.getNumberOfTurns()) return false;
        if (activation.pending()) return true;
        return switch (rule.duration()) {
            case ONE_COMBAT -> holderName.equals(activation.holderName()) && systemId.equals(activation.systemId());
            case ONE_TACTICAL_ACTION -> systemId.equals(activation.systemId());
            default -> true;
        };
    }

    // Roll preparation and per-unit application
    static BombardmentModifiers bombardmentModifiers(ModifierInputs inputs) {
        if (inputs.rollType() != CombatRollType.bombardment) return BombardmentModifiers.empty();
        String suffix = inputs.player().getFaction();
        String target = inputs.game().getStoredValue("bombardmentTarget" + suffix);
        String serialized = inputs.game().getStoredValue("assignedBombardment" + suffix);
        if (target.isBlank() || serialized.isBlank()) return BombardmentModifiers.empty();

        List<BombardmentAssignment> assignments =
                JsonMapperManager.basic().readValue(serialized, new TypeReference<List<BombardmentAssignment>>() {});
        Set<String> sourceIds = new HashSet<>();
        Map<String, Integer> galvanizedByUnit = new HashMap<>();
        for (var assignment : assignments) {
            if (!target.equals(assignment.planet()) || assignment.sourceId() == null) continue;
            sourceIds.add(assignment.sourceId());
            if (assignment.galvanized()) galvanizedByUnit.merge(assignment.sourceId(), 1, Integer::sum);
        }
        return new BombardmentModifiers(true, sourceIds, galvanizedByUnit);
    }

    static RollModifier apply(
            ResolvedModifier modifier,
            Context context,
            Map.Entry<Pair<UnitModel, UnitHolder>, Integer> rollingUnit,
            List<RollModifier> resolved) {
        Rule rule = modifier.rule();
        UnitModel unit = rollingUnit.getKey().getLeft();
        if (!rule.eligible().test(ruleContext(modifier, context), UnitScope.from(context, unit))) return null;
        return rule.effect().apply(new UnitContext(context, rollingUnit, resolved, modifier.displayName(), rule));
    }

    static List<RollModifier> forUnit(Context context, Map.Entry<Pair<UnitModel, UnitHolder>, Integer> rollingUnit) {
        List<RollModifier> resolved = new ArrayList<>();
        for (ResolvedModifier modifier : context.resolvedModifiers()) {
            RollModifier rollModifier = apply(modifier, context, rollingUnit, resolved);
            if (rollModifier != null) resolved.add(rollModifier);
        }
        return List.copyOf(resolved);
    }

    static RuleTarget target(ResolvedModifier modifier, Context context) {
        Rule rule = modifier.rule();
        RuleContext rules = ruleContext(modifier, context);
        List<UnitModel> all = List.copyOf(context.rollingUnitModels());
        List<UnitModel> affected = all.stream()
                .filter(unit -> rule.eligible().test(rules, UnitScope.from(context, unit)))
                .toList();
        if (affected.size() == all.size()) return new RuleTarget("all", "all");
        String scope = affected.stream()
                .map(UnitModel::getAsyncId)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
        String display = affected.stream()
                .map(UnitModel::getUnitEmoji)
                .map(Object::toString)
                .distinct()
                .collect(java.util.stream.Collectors.joining(" "));
        return new RuleTarget(scope, display);
    }

    record RuleTarget(String scope, String display) {}

    private static RuleContext ruleContext(ResolvedModifier modifier, Context context) {
        ModifierInputs inputs = new ModifierInputs(
                context.player(),
                context.game(),
                context.tile(),
                context.rollType(),
                context.combatHolder(),
                context.opponent(),
                context.rollingUnitsFlat(),
                context.opponentUnits());
        return new RuleContext(inputs, context.bombardmentModifiers(), modifier.activation());
    }

    private static ResolvedModifier resolvedModifier(Rule rule, RuleContext context) {
        return new ResolvedModifier(rule, context.displayName(rule), context.activation());
    }

    private static boolean appliesToAnyUnit(Rule rule, RuleContext context, ModifierInputs inputs) {
        return inputs.units().keySet().stream()
                .anyMatch(unit -> rule.eligible().test(context, UnitScope.from(inputs, unit)));
    }

    private static Rule rule(
            String id,
            CombatRollType rollType,
            ModifierDuration duration,
            boolean applyToOpponent,
            List<Source> sources,
            BiPredicate<RuleContext, UnitScope> eligible,
            Function<UnitContext, RollModifier> effect) {
        return new Rule(id, rollType, duration, applyToOpponent, sources, eligible, effect);
    }

    private static boolean isBaseType(UnitModel unit, String type) {
        return type.equalsIgnoreCase(unit.getBaseType());
    }

    private static boolean isFlagshipOrLady(UnitModel unit) {
        return isBaseType(unit, "flagship") || isBaseType(unit, "lady");
    }

    private static boolean holderHasGalvanizedUnit(UnitHolder holder, Player player) {
        return holder.getUnitsByStateForPlayer(player.getColorID()).keySet().stream()
                .anyMatch(key -> holder.getGalvanizedUnitCount(key) > 0);
    }

    private static int supportCount(Player player) {
        return (int) player.getPromissoryNotesInPlayArea().stream()
                .map(Mapper::getPromissoryNote)
                .filter(note -> "Support for the Throne".equals(note.getName()))
                .count();
    }

    private static String displayMessage(String id) {
        return switch (id) {
            case "plus1_roll_mechs_naaz_flagship" -> "Naaz Mechs get +1 die due to Flagship";
            case "minus_1_opponent_tekklar_player_owner" ->
                "If your opponent is the N'orr player, apply -1 to the result of each of their unit's combat rolls during this combat.";
            case "plus1_roll_in_nebula" -> ":shield: Defenders get +1 to ship rolls in a nebula.";
            case "plus2_roll_in_nebula_cosmic" ->
                ":shield: Defenders get +2 to ship rolls in a nebula during Cosmic Phenomenae.";
            case "plus1_roll_in_arcane_citadel" -> ":shield: Defenders get +1 to rolls in the arcane citadel.";
            case "nivyn_commander" -> "> Nivyn Commander Grants an Extra Die To Up To 2 Sustained Units\n";
            case "lizho_commander" -> "> Lizho Commander Grants an Extra Die\n";
            case "toldar_commander" -> "> Toldar Commander Grants an Extra Die\n";
            case "plus1_vaylerian_hero" -> "Vaylerian Ships get +1 to combat rolls due to Hero";
            case "letnev_agent" -> "Letnev Agent gives +1 die to best ship";
            case "classified_weapons_mod" -> "_Classified Weapons_ gives +2 dice to the chosen unit";
            case "sol_agent" -> "Sol Agent gives +1 die to best ground force";
            case "plus1_thalnos" -> "Units get +1 to combat rolls due to Thalnos";
            case "minus1_roll_tnelis_flagship" -> "Tnelis Flagship causes -1 to best ships rolls";
            case "plus1_roll_bluetf_mech" -> "Blue mechs in space area grant +1 die per mech";
            case "xan_warsun2_extra_dice_per_space_dock" -> "+1 roll for each space dock in this system";
            case "plus_2_xan_mech_with_space_dock" -> "+2 when on a planet with a space dock";
            case "arvaxi_mobilization_engine" -> "Mobilization Engine";
            case "plus_1_onyxxa_mech_per_other_mech_on_planet" -> "+1 for each other Onyxxa mech on this planet";
            case "plus_2_for_each_mech_space_cannon_defence",
                    "plus_2_for_each_mech_space_cannon_offence",
                    "plus_2_for_each_mech_anti_fighter_barrage" -> "+2 for each Vyserix mech on planet";
            case "roll_1_for_galvanize_combat",
                    "roll_1_for_galvanize_afb",
                    "roll_1_for_galvanize_bombard",
                    "roll_1_for_galvanize_spacecannon_offense",
                    "roll_1_for_galvanized" ->
                "Galvanized units roll 1 additional die for combat and all unit abilities.";
            case "netrunners_control_network" ->
                FactionEmojis.netrunners + " Control Network: -1 to SPACE CANNON rolls";
            case "iron_advanced_targeting_systems" -> "_Advanced Targeting Systems_";
            default -> null;
        };
    }

    private static Source source(String type, String alias) {
        return new Source(type, alias);
    }

    private record Source(String type, String alias) {}

    record Rule(
            String id,
            CombatRollType rollType,
            ModifierDuration duration,
            boolean applyToOpponent,
            List<Source> sources,
            BiPredicate<RuleContext, UnitScope> eligible,
            Function<UnitContext, RollModifier> effect) {}

    // Shared rule inputs and game-state queries
    private record RuleContext(
            ModifierInputs inputs, BombardmentModifiers bombardment, CombatModifierActivation activation) {
        private Player player() {
            return inputs.player();
        }

        private Player opponent() {
            return inputs.opponent();
        }

        private Game game() {
            return inputs.game();
        }

        private Tile tile() {
            return inputs.tile();
        }

        private UnitHolder holder() {
            return inputs.holder();
        }

        private String storedValue(String key) {
            return game().getStoredValue(key);
        }

        private boolean ownsUnit(String id) {
            return sourceActive("owned_unit", id, player().ownsUnit(id));
        }

        private boolean hasAbility(String id) {
            return sourceActive(Constants.ABILITY, id, player().hasAbility(id));
        }

        private boolean hasTech(String id) {
            return sourceActive(Constants.TECH, id, player().hasTech(id));
        }

        private boolean opponentHasTech(String id) {
            boolean activeParticipant = opponent() != null
                    && (player() == game().getActivePlayer() || opponent() == game().getActivePlayer());
            return sourceActive(
                    "opponent_tech", id, activeParticipant && opponent().hasTech(id));
        }

        private boolean hasRelic(String id) {
            return sourceActive(Constants.RELIC, id, player().hasRelic(id));
        }

        private boolean hasAgenda(String id) {
            boolean elected = game().getLawsInfo().entrySet().stream()
                    .anyMatch(law -> law.getKey().equals(id)
                            && (law.getValue().equals(player().getFaction())
                                    || law.getValue().equals(player().getColor())));
            return sourceActive(Constants.AGENDA, id, elected);
        }

        private boolean hasUnitSource(String id) {
            boolean present = inputs.units().keySet().stream().anyMatch(unit -> id.equals(unit.getAlias()));
            if (!present
                    && player().hasUnlockedBreakthrough("nekrobt")
                    && inputs.units().keySet().stream().anyMatch(unit -> unit.getUnitType() == UnitType.Flagship)) {
                present = ValefarZService.getFlagshipAbilitys(game(), player()).stream()
                        .map(Mapper::getUnit)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(unit -> id.equals(unit.getAlias()));
            }
            return sourceActive(Constants.UNIT, id, present);
        }

        private boolean hasLeader(String id) {
            boolean active = game().playerUnlockedLeadersOrAlliance(player()).stream()
                    .anyMatch(leader -> id.equals(leader.getId()) && !leader.isExhausted() && !leader.isLocked());
            return sourceActive(Constants.LEADER, id, active);
        }

        private boolean hasBreakthrough(String id) {
            return sourceActive("breakthrough", id, player().hasBreakthrough(id));
        }

        private boolean hasActionCard(String id) {
            return sourceActive(Constants.ACTION_CARD, id, false);
        }

        private boolean hasPromissoryNote(String id) {
            return sourceActive(Constants.PROMISSORY_NOTES, id, false);
        }

        private boolean custom() {
            return true;
        }

        private boolean anyPlayerHasAbility(String ability) {
            return inputs.game().getRealPlayers().stream().anyMatch(player -> player.hasAbility(ability));
        }

        private boolean controlNetworkPending() {
            String suffix = player().getFaction();
            return inputs.tile().getTileID().equals(storedValue("controlNetworkSpaceCannonTile" + suffix))
                    && holder().getName().equals(storedValue("controlNetworkSpaceCannonHolder" + suffix))
                    && inputs.rollType().toString().equals(storedValue("controlNetworkSpaceCannonRoll" + suffix));
        }

        private boolean advancedTargetingSystemsActive() {
            if (opponent() == null || Constants.SPACE.equalsIgnoreCase(holder().getName())) return false;
            String suffix = player().getFaction();
            if (!inputs.tile().getPosition().equals(storedValue("ironATSActiveTile_" + suffix))
                    || !opponent().getFaction().equals(storedValue("ironATSActiveOpponent_" + suffix))) return false;

            String boundHolder = storedValue("ironATSBoundHolder_" + suffix);
            boolean holderMatches = holder().getName().equals(boundHolder)
                    || (boundHolder.isEmpty()
                            && ButtonHelper.getPlayersWithUnitsOnAPlanet(game(), tile(), holder().getName())
                                    .containsAll(List.of(player(), opponent())));
            return holderMatches && combatRound() == 0;
        }

        private int combatRound() {
            String key = "combatRoundTracker" + player().getFaction() + tile().getPosition() + holder().getName();
            String stored = storedValue(key);
            return stored.isEmpty() ? 0 : Integer.parseInt(stored) - 1;
        }

        private boolean bombardmentSource(String source) {
            return !bombardment.assignmentsPresent() || bombardment.hasSource(source);
        }

        private boolean hasBombardmentGalvanize() {
            return !bombardment.assignmentsPresent()
                    || !bombardment.galvanizedByUnit().isEmpty();
        }

        private boolean spaceCombat() {
            return Constants.SPACE.equalsIgnoreCase(holder().getName());
        }

        private boolean opponentTeklarPlayerOwner() {
            if (opponent() == null) return false;
            boolean ownsNote = player().getPromissoryNotesOwned().stream()
                    .anyMatch(note -> "tekklar".equals(note) || "sigma_tekklar_legion".equals(note));
            if (!ownsNote) return false;
            return opponent().getCombatModifierActivations().stream()
                    .anyMatch(activation -> "tekklar".equals(activation.sourceId())
                            && Constants.PROMISSORY_NOTES.equals(activation.sourceType()));
        }

        private boolean opponentHasFragments() {
            return opponent() != null && !opponent().getFragments().isEmpty();
        }

        private boolean opponentFactionTechStolen() {
            if (opponent() == null || ButtonHelper.isLawInPlay(game(), "articles_war")) return false;
            String faction = opponent().getFaction();
            return player().getTechs().stream()
                    .map(Mapper::getTech)
                    .anyMatch(tech -> "keleres".equals(faction)
                            ? "keleres".equals(tech.getFaction().orElse(""))
                            : faction.equals(tech.getFaction().orElse("")));
        }

        private boolean planetIsHomeMecatolOrLegendary() {
            var model = inputs.tile().getTileModel();
            if (model == null) return false;
            Tile home = player().getHomeSystemTile();
            if (home != null && model.getId().equals(home.getTileID())) return true;
            if (model.getPlanets() != null
                    && model.getPlanets().stream()
                            .anyMatch(planet -> Constants.MR.equals(planet)
                                    || (Mapper.getPlanet(planet) != null
                                            && org.apache.commons.lang3.StringUtils.isNotBlank(
                                                    Mapper.getPlanet(planet).getLegendaryAbilityName())))) return true;
            return tile() != null && ButtonHelper.isTileLegendary(tile());
        }

        private boolean fragileIsActive() {
            return player().hasAbility("fragile") && !ButtonHelper.isLawInPlay(game(), "articles_war");
        }

        private boolean opponentCommandCounterAbsent() {
            return opponent() != null
                    && !player().getMahactCC().contains(opponent().getColor());
        }

        private boolean nextToStructure() {
            return tile() != null
                    && (!ButtonHelperAgents.getAdjacentTilesWithStructuresInThem(player(), game(), tile())
                                    .isEmpty()
                            || ButtonHelperAgents.doesTileHaveAStructureInIt(player(), tile()));
        }

        private boolean fractureCombat() {
            return tile() != null && tile().getPosition().contains("frac");
        }

        private boolean hasTwoMatchingNonFighters() {
            List<Map.Entry<UnitModel, Integer>> entries =
                    List.copyOf(inputs.units().entrySet());
            if (entries.size() == 1) {
                return entries.getFirst().getValue() == 2
                        && !isBaseType(entries.getFirst().getKey(), "fighter");
            }
            if (entries.size() == 2) {
                var first = entries.get(0);
                var second = entries.get(1);
                if (isBaseType(first.getKey(), "fighter") || isBaseType(second.getKey(), "fighter")) {
                    var nonFighter = isBaseType(first.getKey(), "fighter") ? second : first;
                    return nonFighter.getValue() == 2;
                }
                return isFlagshipOrLady(first.getKey()) && isFlagshipOrLady(second.getKey());
            }
            return entries.size() == 3
                    && entries.stream()
                            .allMatch(
                                    entry -> isBaseType(entry.getKey(), "fighter") || isFlagshipOrLady(entry.getKey()));
        }

        private boolean nebulaDefender() {
            Player active = game().getActivePlayer();
            return tile() != null
                    && (inputs.tile().getTileModel().isNebula() || tile().isNebula(game()))
                    && active != null
                    && active != player()
                    && !active.getAllianceMembers().contains(player().getFaction())
                    && !storedValue("mahactHeroTarget").equalsIgnoreCase(player().getFaction());
        }

        private boolean cosmicNebulaDefender() {
            return game().isCosmicPhenomenaeMode() && nebulaDefender();
        }

        private boolean arcaneDefender() {
            if (tile() == null || game().getActivePlayer() == player()) return false;
            return tile().getPlanetUnitHolders().stream()
                    .filter(holder -> holder.getTokenList().contains("attachment_arcane_citadel.png"))
                    .filter(holder -> player().getPlanets().contains(holder.getName()))
                    .anyMatch(holder -> inputs.units().entrySet().stream()
                            .allMatch(entry ->
                                    holder.getUnitCount(entry.getKey().getUnitType(), player()) == entry.getValue()));
        }

        private boolean vaylerianHeroActive() {
            return player() == game().getActivePlayer()
                    && !storedValue("vaylerianHeroActive").isEmpty();
        }

        private boolean tnelisFlagshipOpposing() {
            return opponent() != null
                    && tile() != null
                    && ButtonHelper.doesPlayerHaveFSHere("tnelis_flagship", opponent(), tile())
                    && FoWHelper.otherPlayersHaveShipsInSystem(player(), tile(), game())
                    && FoWHelper.playerHasShipsInSystem(player(), tile());
        }

        private boolean solAgentActive() {
            return storedValue("solagent").contains(player().getFaction());
        }

        private boolean letnevAgentActive() {
            return storedValue("letnevagent").contains(player().getFaction());
        }

        private boolean classifiedWeaponsActive() {
            return storedValue("classifiedWeapons").startsWith(player().getFaction() + ";");
        }

        private boolean thalnosActive() {
            return "true".equalsIgnoreCase(storedValue("thalnosPlusOne"));
        }

        private boolean hasGalvanizedUnit() {
            if (holderHasGalvanizedUnit(holder(), player())) return true;
            if (tile() != null
                    && tile().getUnitHolders().values().stream()
                            .anyMatch(candidate -> holderHasGalvanizedUnit(candidate, player()))) return true;
            if (inputs.units().keySet().stream().noneMatch(unit -> "xxcha_flagship".equalsIgnoreCase(unit.getId())))
                return false;
            return ButtonHelper.getTilesOfPlayersSpecificUnits(game(), player(), UnitType.Flagship).stream()
                    .flatMap(flagshipTile -> flagshipTile.getUnitHolders().values().stream())
                    .anyMatch(candidate -> holderHasGalvanizedUnit(candidate, player()));
        }

        private boolean opponentHasSupport() {
            return player().hasUnlockedBreakthrough("winnubt") && opponent() != null && supportCount(opponent()) > 0;
        }

        private boolean gloryValorActive() {
            Player owner = Helper.getPlayerFromAbility(game(), "valor");
            if (owner == null) {
                owner = game().getRealPlayers().stream()
                        .filter(player -> player.hasTech("tf-glorioushalls"))
                        .findFirst()
                        .orElse(null);
            }
            return owner != null
                    && ButtonHelperAgents.getGloryTokenTiles(game()).contains(tile());
        }

        private boolean opponentHasBeenAssailed() {
            return opponent() != null
                    && player().hasAbility("marionettes")
                    && player().getPuppetedFactionsForPlot("assail")
                            .contains(opponent().getFaction());
        }

        private boolean toldarCommanderApplies() {
            return hasLeader("toldarcommander")
                    && inputs.units().values().stream()
                                    .mapToInt(Integer::intValue)
                                    .sum()
                            < inputs.opponentUnits().values().stream()
                                    .mapToInt(Integer::intValue)
                                    .sum();
        }

        private boolean lizhoCommanderApplies() {
            if (!hasLeader("lizhocommander")) return false;
            int ships = 0;
            int nonFighters = 0;
            int infantry = 0;
            for (var entry : inputs.units().entrySet()) {
                if (entry.getKey().getIsShip()) {
                    ships += entry.getValue();
                    if (!"fighter".equalsIgnoreCase(entry.getKey().getBaseType())) nonFighters += entry.getValue();
                } else if ("infantry".equalsIgnoreCase(entry.getKey().getBaseType())) {
                    infantry += entry.getValue();
                }
            }
            return ships > 0 ? nonFighters < 2 : infantry < 2;
        }

        private boolean naazFlagshipPresent() {
            Tile active = game().getTileByPosition(game().getActiveSystem());
            return ButtonHelper.doesPlayerHaveFSHere("naaz_flagship", player(), active)
                    || ButtonHelper.doesPlayerHaveFSHere("sigma_naazrokha_flagship_2", player(), active);
        }

        private boolean technotemplarApplies() {
            if (tile() == null || !player().hasUnit("vyserix_mech")) return false;
            List<Tile> tiles = new ArrayList<>(List.of(tile()));
            if (inputs.units().keySet().stream().anyMatch(UnitModel::getDeepSpaceCannon)) {
                FoWHelper.getAdjacentTiles(game(), tile().getPosition(), player(), false, true).stream()
                        .map(game()::getTileByPosition)
                        .filter(java.util.Objects::nonNull)
                        .forEach(tiles::add);
            }
            return tiles.stream()
                    .flatMap(candidate -> candidate.getPlanetUnitHolders().stream())
                    .anyMatch(holder -> holder.getUnitCount(UnitType.Mech, player().getColor()) > 0);
        }

        private boolean opponentStrategyCardsExhausted() {
            return opponent() != null
                    && game().getPlayedSCs().containsAll(opponent().getSCs());
        }

        private boolean spaceDockOnHolder() {
            return game().getRealPlayers().stream()
                    .anyMatch(candidate -> holder().getUnitCount(UnitType.Spacedock, candidate.getColor()) > 0);
        }

        private boolean arvaxiEngineActive() {
            String stored = storedValue("arvaxiMobilizationEngine");
            int separator = stored.indexOf('_');
            return separator > 0 && player().getFaction().equals(stored.substring(0, separator));
        }

        private boolean blueMechApplies() {
            return player().hasUnit("bluetf_mech")
                    && inputs.units().keySet().stream().anyMatch(unit -> unit.getCapacityValue() > 0);
        }

        private boolean wildMoraleBoostActive() {
            return game().isWildWildGalaxyMode()
                    && !storedValue("wildMB" + player().getFaction()).isEmpty();
        }

        private boolean sigmaArgentOnePresent() {
            return ButtonHelper.doesPlayerHaveFSHere(
                    "sigma_argent_flagship_1", player(), game().getTileByPosition(game().getActiveSystem()));
        }

        private boolean sigmaArgentTwoPresent() {
            if (tile() == null) return false;
            return ButtonHelper.doesPlayerHaveFSHere("sigma_argent_flagship_2", player(), tile())
                    || FoWHelper.getAdjacentTilesAndNotThisTile(game(), tile().getPosition(), player(), false).stream()
                            .map(game()::getTileByPosition)
                            .anyMatch(adjacent ->
                                    ButtonHelper.doesPlayerHaveFSHere("sigma_argent_flagship_2", player(), adjacent));
        }

        private boolean sourceActive(String type, String alias, boolean normal) {
            return activation == null
                    ? normal
                    : activation.sourceType().equals(type)
                            && activation.sourceId().equals(alias);
        }

        private String displayName(Rule rule) {
            if (activation != null) return Mapper.getRelatedName(activation.sourceId(), activation.sourceType());
            Source active = rule.sources().stream()
                    .filter(this::isSourceActive)
                    .findFirst()
                    .orElse(rule.sources().isEmpty() ? null : rule.sources().getFirst());
            if (active == null) return rule.id();
            String message = displayMessage(rule.id());
            if (Constants.CUSTOM.equals(active.type()) && message != null) return message;
            if (Constants.UNIT.equals(active.type()) && message != null) {
                UnitModel unit = Mapper.getUnit(active.alias());
                if (unit != null) return unit.getUnitEmoji() + " **__" + unit.getName() + "__**: " + message;
            }
            return switch (active.type()) {
                case Constants.ABILITY -> Mapper.getAbility(active.alias()).getRepresentation();
                case Constants.TECH, "opponent_tech" ->
                    Mapper.getTech(active.alias()).getRepresentation(true);
                case Constants.RELIC -> Mapper.getRelic(active.alias()).getSimpleRepresentation();
                case Constants.AGENDA ->
                    CardEmojis.Agenda + " " + Mapper.getAgenda(active.alias()).getName();
                case Constants.UNIT -> unitName(active.alias(), true);
                case "owned_unit" -> unitName(active.alias(), false);
                case Constants.LEADER ->
                    game().playerUnlockedLeadersOrAlliance(player()).stream()
                            .filter(leader -> active.alias().equals(leader.getId()))
                            .findFirst()
                            .map(Helper::getLeaderFullRepresentation)
                            .orElse(active.alias());
                case "breakthrough" -> Mapper.getBreakthrough(active.alias()).getRepresentation(true);
                default -> Mapper.getRelatedName(active.alias(), active.type());
            };
        }

        private String unitName(String id, boolean includeAbility) {
            UnitModel unit = Mapper.getUnit(id);
            if (unit == null) return id;
            String name = unit.getUnitEmoji() + " **__" + unit.getName() + "__**";
            return includeAbility ? name + " " + unit.getAbility() : name;
        }

        private boolean isSourceActive(Source source) {
            return switch (source.type()) {
                case Constants.ABILITY -> hasAbility(source.alias());
                case Constants.TECH -> hasTech(source.alias());
                case "opponent_tech" -> opponentHasTech(source.alias());
                case Constants.RELIC -> hasRelic(source.alias());
                case Constants.AGENDA -> hasAgenda(source.alias());
                case Constants.UNIT -> hasUnitSource(source.alias());
                case Constants.LEADER -> hasLeader(source.alias());
                case "breakthrough" -> hasBreakthrough(source.alias());
                case "owned_unit" -> ownsUnit(source.alias());
                case Constants.ACTION_CARD -> hasActionCard(source.alias());
                case Constants.PROMISSORY_NOTES -> hasPromissoryNote(source.alias());
                case Constants.CUSTOM -> true;
                default -> false;
            };
        }
    }

    /**
     * Supplies the current unit and shared combat facts to a rule's per-unit eligibility lambda.
     *
     * <p>This is the typed replacement for the former {@code scope} and {@code scopeExcept} strings. A rule uses it
     * to decide which participating unit models receive the rule before the engine modifier is created.
     */
    // Per-unit eligibility queries
    private record UnitScope(
            Game game,
            Player player,
            Tile tile,
            UnitHolder holder,
            CombatRollType rollType,
            List<UnitModel> allUnits,
            UnitModel unit) {
        private UnitScope {
            allUnits = List.copyOf(allUnits);
        }

        private static UnitScope from(ModifierInputs inputs, UnitModel unit) {
            return new UnitScope(
                    inputs.game(),
                    inputs.player(),
                    inputs.tile(),
                    inputs.holder(),
                    inputs.rollType(),
                    List.copyOf(inputs.units().keySet()),
                    unit);
        }

        private static UnitScope from(Context context, UnitModel unit) {
            return new UnitScope(
                    context.game(),
                    context.player(),
                    context.tile(),
                    context.combatHolder(),
                    context.rollType(),
                    List.copyOf(context.rollingUnitModels()),
                    unit);
        }

        private boolean is(String asyncId) {
            return asyncId.equals(unit.getAsyncId());
        }

        private boolean best() {
            List<UnitModel> sorted = new ArrayList<>(allUnits);
            sorted.sort(java.util.Comparator.comparingInt(
                    candidate -> candidate.getCombatDieHitsOnForAbility(rollType, player)));
            UnitModel best = sorted.getFirst();
            if (sorted.size() > 1) {
                UnitModel second = sorted.get(1);
                boolean tied = best.getCombatDieHitsOnForAbility(rollType, player)
                        == second.getCombatDieHitsOnForAbility(rollType, player);
                if (tied && second.getCombatDieCount() > best.getCombatDieCount()) best = second;
            }
            return best.getAsyncId().equals(unit.getAsyncId());
        }

        private boolean bestCapacity() {
            return allUnits.stream()
                    .filter(candidate -> candidate.getCapacityValue() > 0)
                    .min(java.util.Comparator.comparingInt(
                            candidate -> candidate.getCombatDieHitsOnForAbility(rollType, player)))
                    .map(candidate -> candidate.getAsyncId().equals(unit.getAsyncId()))
                    .orElse(false);
        }

        private boolean mostDice() {
            return allUnits.stream()
                    .min(java.util.Comparator.comparingInt(
                            candidate -> candidate.getCombatDieCountForAbility(rollType, player)))
                    .map(candidate -> candidate.getAsyncId().equals(unit.getAsyncId()))
                    .orElse(false);
        }

        private boolean ship() {
            if (unit.getIsShip()) return true;
            Tile active = game.getTileByPosition(game.getActiveSystem());
            if (active == null) return false;
            boolean opposingShips = FoWHelper.playerHasShipsInSystem(player, active)
                    && FoWHelper.otherPlayersHaveShipsInSystem(player, active, game);
            if ("purpletf_mech".equalsIgnoreCase(unit.getAlias()) && opposingShips) return true;
            return ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", player, active)
                    && FoWHelper.otherPlayersHaveShipsInSystem(player, active, game);
        }

        private boolean shipExceptFighter() {
            return unit.getIsShip() && !"fighter".equalsIgnoreCase(unit.getBaseType());
        }

        private boolean groundForce() {
            return unit.getIsGroundForce();
        }

        private boolean classifiedWeapons() {
            String stored = game.getStoredValue("classifiedWeapons");
            int separator = stored.indexOf(';');
            return separator >= 0 && stored.substring(separator + 1).equals(unit.getAsyncId());
        }
    }

    // Engine modifier construction and calculated rule values
    private record UnitContext(
            Context context,
            Map.Entry<Pair<UnitModel, UnitHolder>, Integer> rollingUnit,
            List<RollModifier> resolved,
            String displayName,
            Rule rule) {
        private UnitModel unit() {
            return rollingUnit.getKey().getLeft();
        }

        private UnitHolder holder() {
            return rollingUnit.getKey().getRight();
        }

        private int quantity() {
            return rollingUnit.getValue();
        }

        private int effectiveDice() {
            int value = unit().getCombatDieCountForAbility(context.rollType(), context.player());
            for (RollModifier modifier : resolved) {
                if (!(modifier instanceof StatModifier stat) || stat.stat() != UnitRollStat.DICE_PER_UNIT) continue;
                value = stat.operation() == StatOperation.ADD ? value + stat.value() : stat.value();
            }
            return value;
        }

        private RollModifier toHit(int value) {
            return value(ModifierEffect.TO_HIT, value);
        }

        private RollModifier extraDice(int value) {
            return value(ModifierEffect.EXTRA_DICE, value);
        }

        private RollModifier noEffect() {
            return value(ModifierEffect.TO_HIT, 0);
        }

        private RollModifier value(ModifierEffect effect, int value) {
            return new ValueModifier(rule.id(), effect, value, rule.duration(), displayName);
        }

        private RollModifier stat(UnitRollStat stat, StatOperation operation, int value) {
            return new StatModifier(rule.id(), stat, operation, value, rule.duration());
        }

        private RollModifier bonusHits(HitMatch match, int result, int hits) {
            return new HitRule(match, result, hits);
        }

        private RollModifier bonusHits(HitMatch match, int result, int hits, HitRuleTiming timing) {
            return new HitRule(match, result, hits, timing);
        }

        private RollModifier reroll(RollSource source, RerollSelector selector) {
            return new RerollRule(source, selector, 0, true);
        }

        private RollModifier additionalDice(
                RollSource source, AdditionalDiceBasis basis, HitMatch match, int result, int dice, boolean repeat) {
            return new AdditionalRollRule(source, basis, match, result, dice, repeat);
        }

        private int opponentNonFighterShips() {
            return this.context.opponent() == null
                    ? 0
                    : ButtonHelper.checkNumberNonFighterShips(this.context.opponent(), this.context.tile());
        }

        private int opponentShips() {
            return this.context.opponent() == null
                    ? 0
                    : ButtonHelper.checkNumberShips(this.context.opponent(), this.context.tile());
        }

        private int fragmentTypes() {
            int count = 0;
            if (this.context.player().isHasFoundCulFrag()) {
                ++count;
            }
            if (this.context.player().isHasFoundHazFrag()) {
                ++count;
            }
            if (this.context.player().isHasFoundIndFrag()) {
                ++count;
            }
            if (this.context.player().isHasFoundUnkFrag()) {
                ++count;
            }
            return count;
        }

        private int codeValue() {
            int dishonor;
            int honor;
            int n = honor = this.context.player().getHonorCounter() > 1 ? 1 : 0;
            if (this.context.player().getHonorCounter() > 4) {
                ++honor;
            }
            if (this.context.player().getHonorCounter() > 7) {
                ++honor;
            }
            int n2 = dishonor = this.context.player().getDishonorCounter() > 1 ? 1 : 0;
            if (this.context.player().getDishonorCounter() > 1
                    && this.context.player().getDishonorCounter() < 4) {
                dishonor += 2;
            }
            return honor + dishonor;
        }

        private int lawCount() {
            return this.context.game().getLaws().size();
        }

        private int unitUpgradeCount() {
            return (int) this.context.player().getTechs().stream()
                    .map(Mapper::getTech)
                    .filter(TechnologyModel::isUnitUpgrade)
                    .count();
        }

        private int opponentUnitUpgradeCount() {
            return this.context.opponent() == null
                    ? 0
                    : (int) this.context.opponent().getTechs().stream()
                            .map(Mapper::getTech)
                            .filter(TechnologyModel::isUnitUpgrade)
                            .count();
        }

        private int opponentFactionTechCount() {
            return this.context.opponent() == null
                    ? 0
                    : (int) this.context.opponent().getTechs().stream()
                            .map(Mapper::getTech)
                            .filter(tech -> StringUtils.isNotBlank(
                                    (CharSequence) tech.getFaction().orElse("")))
                            .count();
        }

        private int destroyerCount() {
            return ButtonHelper.getNumberOfUnitsOnTheBoard(
                    this.context.game(), this.context.player(), "destroyer", false);
        }

        private int combatRound() {
            String key = "combatRoundTracker" + this.context.getFaction() + this.context.getTilePosition()
                    + this.context.holderName();
            String stored = this.context.storedValue(key);
            return stored.isEmpty() ? 0 : Integer.parseInt(stored) - 1;
        }

        private List<Tile> adjacentTiles() {
            return FoWHelper.getAdjacentTiles(
                            this.context.game(), this.context.getTilePosition(), this.context.player(), false, true)
                    .stream()
                    .map(this.context.game()::getTileByPosition)
                    .filter(Objects::nonNull)
                    .toList();
        }

        private int adjacentAnomalies() {
            return (int) this.adjacentTiles().stream()
                    .filter(tile -> tile.isAnomaly(this.context.game(), this.context.player()))
                    .count();
        }

        private int adjacentAsteroids() {
            return (int)
                    this.adjacentTiles().stream().filter(Tile::isAsteroidField).count();
        }

        private int adjacentMechs() {
            int count = 0;
            for (Tile tile : this.adjacentTiles()) {
                for (UnitHolder candidate : tile.getUnitHolders().values()) {
                    for (Player player : this.context.game().getRealPlayers()) {
                        count += candidate.getUnitCount(Units.UnitType.Mech, player.getColor());
                    }
                }
            }
            return count;
        }

        private int spaceDocksInTile() {
            int count = 0;
            for (UnitHolder unitHolder : this.context.tile().getPlanetUnitHolders()) {
                for (Player player : this.context.game().getRealPlayers()) {
                    count += unitHolder.getUnitCount(Units.UnitType.Spacedock, player.getColor());
                }
            }
            return count;
        }

        private int arvaxiEngineValue() {
            String stored = this.context.storedValue("arvaxiMobilizationEngine");
            int firstSeparator = stored.indexOf(95);
            int lastSeparator = stored.lastIndexOf(95);
            if (stored.isEmpty()
                    || firstSeparator < 0
                    || firstSeparator == lastSeparator
                    || !this.context.getFaction().equals(stored.substring(0, firstSeparator))) {
                return 0;
            }
            String technology = stored.substring(firstSeparator + 1, lastSeparator);
            UnitModel attachedUnit = Mapper.getUnitModelByTechUpgrade(technology);
            if (attachedUnit == null
                    || !attachedUnit.getAsyncId().equals(this.unit().getAsyncId())) {
                return 0;
            }
            return this.context.storedValue("arvaxiMobilizationEngine").endsWith("_boon") ? 1 : -1;
        }

        private int otherMechsOnPlanet() {
            return this.holder() instanceof Planet
                    ? Math.max(0, this.holder().getUnitCount(Units.UnitType.Mech, this.context.getColor()) - 1)
                    : 0;
        }

        private int mechsOnPlanet() {
            return this.holder() instanceof Planet
                    ? this.holder().getUnitCount(Units.UnitType.Mech, this.context.getColor())
                    : 0;
        }

        private int controlledNonHomeSystems() {
            return (int) this.context.game().getTileMap().values().stream()
                    .filter(tile -> !tile.isHomeSystem(this.context.game()))
                    .filter(tile -> tile.getPlanetUnitHolders().stream().anyMatch(planet -> this.context
                            .player()
                            .getPlanetsAllianceMode()
                            .contains(planet.getName())))
                    .count();
        }

        private int galvanizedCount() {
            int count;
            Units.UnitKey key = Units.getUnitKey(this.unit().getUnitType(), this.context.getColorId());
            int n = count = this.context.rollType() == CombatRollType.bombardment
                    ? this.context
                            .bombardmentModifiers()
                            .galvanizedCount(this.unit().getAsyncId())
                    : this.holder().getGalvanizedUnitCount(key);
            if (this.context.rollType() == CombatRollType.SpaceCannonOffence
                    && this.unit().getDeepSpaceCannon()) {
                for (Tile adjacent : this.adjacentTiles()) {
                    for (UnitHolder candidate : adjacent.getUnitHolders().values()) {
                        count += candidate.getGalvanizedUnitCount(key);
                    }
                }
            }
            return count;
        }

        private int uniqueShips() {
            Space space = this.context.tile().getSpaceUnitHolder();
            return (int) space.getUnitsByState().keySet().stream()
                    .filter(this.context.player()::unitBelongsToPlayer)
                    .filter(key -> space.getUnitCount((Units.UnitKey) key) > 0)
                    .map(this.context.player()::getUnitFromUnitKey)
                    .filter(Objects::nonNull)
                    .filter(UnitModel::getIsShip)
                    .count();
        }

        private int mechsInSpace() {
            return "space".equalsIgnoreCase(this.holder().getName()) && this.context.playerHasUnit("bluetf_mech")
                    ? this.context.tile().getSpaceUnitHolder().getUnitCount(Units.UnitType.Mech, this.context.player())
                    : 0;
        }

        private int opponentSupports() {
            return this.context.opponent() == null ? 0 : CombatV2Modifiers.supportCount(this.context.opponent());
        }

        private int damagedUnits() {
            Units.UnitKey key = Units.getUnitKey(this.unit().getUnitType(), this.context.getColor());
            UnitHolder damagedHolder = this.context.tile().getSpaceUnitHolder();
            if (this.unit().getIsGroundForce()) {
                for (UnitHolder unitHolder : this.context.tile().getPlanetUnitHolders()) {
                    if (unitHolder.getUnitCount(key) <= 0) continue;
                    damagedHolder = unitHolder;
                }
            }
            return damagedHolder.getDamagedUnitCount(key);
        }
    }
}

record ModifierInputs(
        Player player,
        Game game,
        Tile tile,
        CombatRollType rollType,
        UnitHolder holder,
        Player opponent,
        Map<UnitModel, Integer> units,
        Map<UnitModel, Integer> opponentUnits) {}
