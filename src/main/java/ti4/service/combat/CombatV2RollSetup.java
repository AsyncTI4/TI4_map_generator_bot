package ti4.service.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.service.breakthrough.ValefarZService;
import ti4.service.combat.CombatV2DiceData.AdditionalDiceBasis;
import ti4.service.combat.CombatV2DiceData.AdditionalRollRule;
import ti4.service.combat.CombatV2DiceData.HitMatch;
import ti4.service.combat.CombatV2DiceData.HitRule;
import ti4.service.combat.CombatV2DiceData.RerollRule;
import ti4.service.combat.CombatV2DiceData.RerollSelector;
import ti4.service.combat.CombatV2DiceData.RollPlan;
import ti4.service.combat.CombatV2DiceData.RollSource;
import ti4.service.combat.CombatV2DiceData.UnitRollPlan;
import ti4.service.combat.CombatV2RollData.Context;
import ti4.service.combat.CombatV2RollData.ContextResult;
import ti4.service.combat.CombatV2RollData.Modifiers;
import ti4.service.combat.CombatV2RollData.PreparedRoll;
import ti4.service.combat.CombatV2RollData.Rejected;
import ti4.service.combat.CombatV2RollData.Request;
import ti4.service.combat.CombatV2RollData.UnitModifiers;

/** Collects the units, opponent, and rules needed before rolling dice. */
@UtilityClass
class CombatV2RollSetup {

    static ContextResult combatRound(Request request) {
        ContextAssembly assembly = assembleContext(request, CombatRollType.combatround, false);
        if (assembly.rejection() != null) return new Rejected(assembly.rejection());
        return new PreparedRoll(assembly.context(), combatRoundPlan(assembly.context()));
    }

    static ContextResult automatedCombatRound(Request request) {
        ContextAssembly assembly = assembleContext(request, CombatRollType.combatround, true);
        if (assembly.rejection() != null) return new Rejected(assembly.rejection());
        return new PreparedRoll(assembly.context(), combatRoundPlan(assembly.context()));
    }

    static ContextResult antiFighterBarrage(Request request) {
        ContextAssembly assembly = assembleContext(request, CombatRollType.AFB, false);
        if (assembly.rejection() != null) return new Rejected(assembly.rejection());
        return new PreparedRoll(assembly.context(), antiFighterBarragePlan(assembly.context()));
    }

    static ContextResult bombardment(Request request) {
        ContextAssembly assembly = assembleContext(request, CombatRollType.bombardment, false);
        if (assembly.rejection() != null) return new Rejected(assembly.rejection());
        return new PreparedRoll(assembly.context(), bombardmentPlan(assembly.context()));
    }

    static ContextResult spaceCannonOffense(Request request) {
        ContextAssembly assembly = assembleContext(request, CombatRollType.SpaceCannonOffence, false);
        if (assembly.rejection() != null) return new Rejected(assembly.rejection());
        return new PreparedRoll(assembly.context(), spaceCannonOffensePlan(assembly.context()));
    }

    static ContextResult spaceCannonDefense(Request request) {
        ContextAssembly assembly = assembleContext(request, CombatRollType.SpaceCannonDefence, false);
        if (assembly.rejection() != null) return new Rejected(assembly.rejection());
        return new PreparedRoll(assembly.context(), spaceCannonDefensePlan(assembly.context()));
    }

    private static String validationError(Request request, CombatRollType rollType) {
        UnitHolder holder = request.unitHolder();
        if (holder == null) {
            return CombatV2Messages.missingHolder(request.unitHolderName(), request.getTilePosition());
        }
        if (rollType == CombatRollType.SpaceCannonDefence && !(holder instanceof Planet)) {
            return CombatV2Messages.spaceCannonNeedsPlanet(request.getTilePosition());
        }
        return null;
    }

    private static ContextAssembly assembleContext(Request request, CombatRollType rollType, boolean automated) {
        String validationError = validationError(request, rollType);
        if (validationError != null) return ContextAssembly.rejected(validationError);

        UnitHolder holder = request.unitHolder();
        CombatV2UnitService.UnitSelection selection = CombatV2UnitService.select(request, holder, rollType);
        Map<Pair<UnitModel, UnitHolder>, Integer> rollingUnits = selection.units();
        List<String> notices = selection.notices();

        if (rollingUnits.isEmpty()) {
            String fightingOn = Constants.SPACE.equalsIgnoreCase(request.unitHolderName())
                    ? request.unitHolderName()
                    : Helper.getPlanetRepresentation(request.unitHolderName(), request.game());
            return ContextAssembly.rejected(CombatV2Messages.noUnits(
                    fightingOn, request.getTilePosition(), request.getColor(), request.getFactionEmoji(), rollType));
        }

        List<UnitHolder> opponentHolders = new ArrayList<>(List.of(holder));
        if (rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence) {
            opponentHolders.add(request.spaceHolder());
        }
        Player opponent = bombardmentOpponent(request, rollType);
        if (opponent == null) {
            opponent = CombatV2UnitService.getOpponent(request, opponentHolders);
        }
        if (opponent == null) opponent = request.player();

        Map<UnitModel, Integer> rollingUnitsFlat = selection.flatUnits();
        Map<UnitModel, Integer> opponentUnits =
                CombatV2UnitService.getUnitsInCombat(request, holder, opponent, rollType);
        Modifiers modifiers = CombatV2ModifierService.resolve(
                new ModifierInputs(request, rollType, holder, opponent, rollingUnitsFlat, opponentUnits));
        Context context = new Context(
                request,
                rollType,
                automated,
                holder,
                opponent,
                rollingUnits,
                rollingUnitsFlat,
                opponentUnits,
                modifiers,
                notices);
        return ContextAssembly.accepted(context);
    }

    private record ContextAssembly(Context context, String rejection) {
        private static ContextAssembly accepted(Context context) {
            return new ContextAssembly(context, null);
        }

        private static ContextAssembly rejected(String message) {
            return new ContextAssembly(null, message);
        }
    }

    private static Player bombardmentOpponent(Request request, CombatRollType rollType) {
        if (rollType != CombatRollType.bombardment) return null;
        String target = request.storedValue("bombardmentTarget" + request.getFaction());
        if (target.isBlank()) return null;
        return request.playerOwningPlanet(target);
    }

    private static RollPlan combatRoundPlan(Context context) {
        List<UnitRollPlan> units = new ArrayList<>();
        List<HitRule> sharedHitRules = combatRoundHitRules(context);
        for (var entry : context.rollingUnitEntries()) {
            UnitModel unit = entry.getKey().getLeft();
            CombatStats stats = combatStats(unit, context.player(), context.tile(), context.opponent());
            units.add(planUnit(
                    context, entry, stats.dice(), stats.hitsOn(), combatRoundRerolls(context, unit), sharedHitRules));
        }
        return new RollPlan(units, 0);
    }

    private static RollPlan antiFighterBarragePlan(Context context) {
        return abilityRoll(context, CombatRollType.AFB, List.of());
    }

    private static RollPlan bombardmentPlan(Context context) {
        List<HitRule> rules = context.playerHasTech("x89c4") ? List.of(new HitRule(HitMatch.SUCCESS, 0, 1)) : List.of();
        return abilityRoll(context, CombatRollType.bombardment, rules);
    }

    private static RollPlan spaceCannonOffensePlan(Context context) {
        return abilityRoll(context, CombatRollType.SpaceCannonOffence, spaceCannonHitRules(context));
    }

    private static RollPlan spaceCannonDefensePlan(Context context) {
        return abilityRoll(context, CombatRollType.SpaceCannonDefence, spaceCannonHitRules(context));
    }

    private static RollPlan abilityRoll(Context context, CombatRollType type, List<HitRule> sharedHitRules) {
        List<UnitRollPlan> units = new ArrayList<>();
        for (var entry : context.rollingUnitEntries()) {
            UnitModel unit = entry.getKey().getLeft();
            int dice = unit.getCombatDieCountForAbility(type, context.player());
            int hitsOn = unit.getCombatDieHitsOnForAbility(type, context.player());
            List<RerollRule> rerolls = abilityRerolls(context, unit, type);
            units.add(planUnit(context, entry, dice, hitsOn, rerolls, sharedHitRules));
        }
        return new RollPlan(units, 0);
    }

    private static UnitRollPlan planUnit(
            Context context,
            Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry,
            int dice,
            int hitsOn,
            List<RerollRule> rerolls,
            List<HitRule> sharedHitRules) {
        UnitModel unit = entry.getKey().getLeft();
        UnitHolder holder = entry.getKey().getRight();
        int quantity = entry.getValue();
        UnitModifiers modifiers = CombatV2ModifierService.forUnit(context, unit, quantity, holder);
        List<HitRule> hitRules = new ArrayList<>(sharedHitRules);
        addUnitHitRules(context, unit, hitRules);
        return new UnitRollPlan(
                unit.getId(),
                unit.getAsyncId(),
                unit.getBaseType(),
                unit.getName(),
                displayName(unit),
                unit.getUnitEmoji().toString(),
                quantity,
                dice,
                Math.max(0, modifiers.extraDice()),
                hitsOn,
                modifiers.toHit(),
                0,
                hitRules,
                additionalRolls(unit),
                rerolls);
    }

    private static List<AdditionalRollRule> additionalRolls(UnitModel unit) {
        String id = unit.getId();
        if ("sigma_jolnar_flagship_1".equalsIgnoreCase(id) || "sigma_jolnar_flagship_2".equalsIgnoreCase(id)) {
            return List.of(new AdditionalRollRule(
                    RollSource.SIGMA_JOL_NAR_FLAGSHIP, AdditionalDiceBasis.HITS, HitMatch.SUCCESS, 0, 1, true));
        }
        return List.of();
    }

    private static List<RerollRule> combatRoundRerolls(Context context, UnitModel unit) {
        List<RerollRule> rerolls = commonRerolls(context, unit, CombatRollType.combatround);
        boolean munitions = context.getFaction().equalsIgnoreCase(context.storedValue("munitionsReserves"));
        boolean thalnos = "true".equalsIgnoreCase(context.storedValue("thalnosPlusOne"));
        if (munitions && !thalnos) {
            rerolls.add(new RerollRule(RollSource.MUNITIONS_RESERVES, RerollSelector.MISSES, 0, true));
        }
        return rerolls;
    }

    private static List<RerollRule> abilityRerolls(Context context, UnitModel unit, CombatRollType type) {
        List<RerollRule> rerolls = commonRerolls(context, unit, type);
        boolean jolNarCommander = context.playerHasLeaderUnlockedOrAlliance("jolnarcommander");
        if (jolNarCommander || context.playerHasTech("tf-tacticalbrilliance")) {
            boolean rerollHits = type == CombatRollType.bombardment
                    && context.opponent() == context.player()
                    && context.playerHasTech("proxima");
            rerolls.add(new RerollRule(
                    rerollHits ? RollSource.JOL_NAR_COMMANDER_HITS : RollSource.JOL_NAR_COMMANDER_MISSES,
                    rerollHits ? RerollSelector.HITS : RerollSelector.MISSES,
                    0,
                    true));
        }
        return rerolls;
    }

    private static List<RerollRule> commonRerolls(Context context, UnitModel unit, CombatRollType type) {
        List<RerollRule> rerolls = new ArrayList<>();
        if (context.game().playerHasLeaderUnlockedOrAlliance(context.player(), "ironcommander")
                && type == CombatRollType.combatround
                && unit.getUnitType() == UnitType.Mech) {
            rerolls.add(new RerollRule(RollSource.IRON_COMMANDER_MISSES, RerollSelector.MISSES, 0, true));
        }
        if (context.playerHasLeaderUnlockedOrAlliance("kaltrimcommander")) {
            rerolls.add(new RerollRule(RollSource.KALTRIM_COMMANDER_ONES, RerollSelector.ONES, 0, true));
        }
        return rerolls;
    }

    private static List<HitRule> spaceCannonHitRules(Context context) {
        return context.playerHasLeaderUnlockedOrAlliance("zephyrioncommander")
                ? List.of(new HitRule(HitMatch.EXACT_RESULT, 10, 1))
                : List.of();
    }

    private static List<HitRule> combatRoundHitRules(Context context) {
        boolean x89GroundCombat = context.playerHasTech("x89c4") && context.combatHolder() instanceof Planet;
        return x89GroundCombat ? List.of(new HitRule(HitMatch.SUCCESS, 0, 1)) : List.of();
    }

    private static void addUnitHitRules(Context context, UnitModel unit, List<HitRule> rules) {
        if ("sigma_arborec_flagship_2".equalsIgnoreCase(unit.getId())) {
            rules.add(new HitRule(HitMatch.AT_LEAST_RESULT, 9, 1));
        }
        if (unit.getUnitType() == UnitType.Flagship
                && ValefarZService.hasFlagshipAbility(context.game(), context.player(), "jolnar_flagship")) {
            rules.add(new HitRule(HitMatch.AT_LEAST_RESULT, 9, 2));
        }
        if (unit.getUnitType() == UnitType.Infantry && context.playerHasUnit("tk-tekklarelite")) {
            rules.add(new HitRule(HitMatch.SUCCESS, 0, 1));
        }
        if (context.playerHasTech("tf-valortf")) rules.add(new HitRule(HitMatch.EXACT_RESULT, 10, 1));
    }

    private static CombatStats combatStats(UnitModel unit, Player player, ti4.game.Tile tile, Player opponent) {
        int dice = unit.getCombatDieCountForAbility(CombatRollType.combatround, player);
        if (unit.getUnitType() == UnitType.Mech && player.ownsUnit("tf-eidolonlandwaster")) dice++;
        if (isEchoFlagship(unit, player)) dice++;
        if ("winnu_flagship".equals(unit.getId()) && dice <= 0 && opponent != null) {
            dice = ButtonHelper.checkNumberNonFighterShips(opponent, tile);
        }

        int hitsOn = unit.getCombatDieHitsOnForAbility(CombatRollType.combatround, player);
        if (unit.getUnitType() == UnitType.Mech && player.ownsUnit("tf-eidolonterminus")) hitsOn--;
        if (isEchoFlagship(unit, player)) hitsOn--;
        return new CombatStats(dice, Math.max(1, hitsOn));
    }

    private static boolean isEchoFlagship(UnitModel unit, Player player) {
        return unit.getUnitType() == UnitType.Flagship && player.ownsUnit("tf-echoofascension");
    }

    private static String displayName(UnitModel unit) {
        return unit.getUpgradesFromUnitId().isPresent() || unit.getFaction().isPresent() ? unit.getName() : "";
    }

    private record CombatStats(int dice, int hitsOn) {}
}
