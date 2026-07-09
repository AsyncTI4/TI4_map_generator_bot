package ti4.service.combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.model.AgendaModel;
import ti4.model.BreakthroughModel;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.breakthrough.ValefarZService;
import ti4.service.combat.CombatV2RollData.BombardmentModifiers;
import ti4.service.combat.CombatV2RollData.Context;
import ti4.service.combat.CombatV2RollData.ModifierKind;
import ti4.service.combat.CombatV2RollData.Modifiers;
import ti4.service.combat.CombatV2RollData.Request;
import ti4.service.combat.CombatV2RollData.UnitModifiers;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import tools.jackson.core.type.TypeReference;

/** Resolves combat modifiers and temporary rule effects for Combat V2 rolls. */
@UtilityClass
public class CombatV2ModifierService {

    static Modifiers resolve(ModifierInputs inputs) {
        Request request = inputs.request();
        ModifierIndex index = indexModifiers(inputs);
        CollectedModifiers collected = collectModifiers(inputs, index);
        List<NamedCombatModifierModel> resultModifiers = collected.hitModifiers();
        List<NamedCombatModifierModel> extraRolls = collected.extraRolls();
        BombardmentModifiers bombardment = bombardmentModifiers(inputs);
        filterBombardmentExtras(extraRolls, bombardment);

        List<NamedCombatModifierModel> temporaryModifiers = temporaryModifiers(inputs);
        if (request.anyRealPlayer(player -> player.hasAbility("control_network"))) {
            temporaryModifiers.addAll(controlNetworkModifier(inputs));
        }
        if (request.playerHasTech("beironats")) {
            extraRolls.addAll(advancedTargetingSystemsModifier(inputs));
        }
        Map<String, Long> sharedScaling = sharedScalingValues(inputs, resultModifiers, extraRolls, temporaryModifiers);
        return Modifiers.of(resultModifiers, extraRolls, temporaryModifiers, bombardment, sharedScaling);
    }

    private static List<NamedCombatModifierModel> temporaryModifiers(ModifierInputs inputs) {
        Request request = inputs.request();
        TileModel tile = request.tileModel();
        List<NamedCombatModifierModel> modifiers = new ArrayList<>(
                currentTemporaryModifiers(request.player(), false, inputs.rollType(), tile, inputs.holder()));
        modifiers.addAll(pendingTemporaryModifiers(request.player(), false, inputs.rollType()));
        modifiers.addAll(currentTemporaryModifiers(inputs.opponent(), true, inputs.rollType(), tile, inputs.holder()));
        return modifiers;
    }

    static void consume(Context context) {
        Player player = context.player();
        Player opponent = context.opponent();
        TileModel tile = context.request().tileModel();
        UnitHolder holder = context.combatHolder();

        ensureValidTempMods(player, tile, holder);
        initializeNewTempMods(player, tile, holder);
        consumeTemporaryModifiers(player, false, context.rollType());
        if (opponent != player) {
            ensureValidTempMods(opponent, tile, holder);
            consumeTemporaryModifiers(opponent, true, context.rollType());
        }
        if (context.hasAppliedCondition("wildMB")) {
            context.removeStoredValue("wildMB" + context.getFaction());
        }
        if (context.appliedModifiers().stream()
                .anyMatch(applied -> "netrunners_control_network".equals(applied.ruleId()))) {
            clearControlNetwork(context);
        }
        if (context.appliedModifiers().stream()
                .anyMatch(applied -> "iron_advanced_targeting_systems".equals(applied.ruleId()))) {
            clearAdvancedTargetingSystems(context.game(), context.player());
        }
    }

    private static List<NamedCombatModifierModel> controlNetworkModifier(ModifierInputs inputs) {
        Request request = inputs.request();
        String suffix = request.getFaction();
        boolean pending = request.getTileId().equals(request.storedValue("controlNetworkSpaceCannonTile" + suffix))
                && inputs.holder().getName().equals(request.storedValue("controlNetworkSpaceCannonHolder" + suffix))
                && inputs.rollType().toString().equals(request.storedValue("controlNetworkSpaceCannonRoll" + suffix));
        if (!pending) return List.of();

        CombatModifierModel modifier = new CombatModifierModel();
        modifier.setAlias("netrunners_control_network");
        modifier.setType(Constants.COMBAT_MODIFIERS);
        modifier.setValue(-1);
        modifier.setPersistenceType(Constants.MOD_TEMP_ONE_ROUND.toString());
        modifier.setScope("");
        modifier.setRelated(List.of());
        modifier.setForCombatAbility(inputs.rollType());
        return List.of(new NamedCombatModifierModel(
                modifier, FactionEmojis.netrunners + " Control Network: -1 to SPACE CANNON rolls"));
    }

    private static void clearControlNetwork(Context context) {
        String suffix = context.getFaction();
        context.removeStoredValue("controlNetworkSpaceCannonTile" + suffix);
        context.removeStoredValue("controlNetworkSpaceCannonHolder" + suffix);
        context.removeStoredValue("controlNetworkSpaceCannonRoll" + suffix);
    }

    private static List<NamedCombatModifierModel> advancedTargetingSystemsModifier(ModifierInputs inputs) {
        if (inputs.rollType() != CombatRollType.combatround
                || Constants.SPACE.equalsIgnoreCase(inputs.holder().getName())) return List.of();
        Request request = inputs.request();
        Game game = request.game();
        Player rollingPlayer = request.player();
        Player opposingPlayer = inputs.opponent();

        for (Player owner : List.of(rollingPlayer, opposingPlayer)) {
            Player opponent = owner == rollingPlayer ? opposingPlayer : rollingPlayer;
            String suffix = owner.getFaction();
            if (!request.getTilePosition().equals(game.getStoredValue("ironATSActiveTile_" + suffix))
                    || !opponent.getFaction().equals(game.getStoredValue("ironATSActiveOpponent_" + suffix))) {
                continue;
            }
            String boundHolder = game.getStoredValue("ironATSBoundHolder_" + suffix);
            boolean holderMatches = inputs.holder().getName().equals(boundHolder)
                    || (boundHolder.isEmpty()
                            && ButtonHelper.getPlayersWithUnitsOnAPlanet(
                                            game,
                                            request.tile(),
                                            inputs.holder().getName())
                                    .containsAll(List.of(owner, opponent)));
            if (!holderMatches
                    || rollingPlayer != owner
                    || combatRound(game, owner, request.tile(), inputs.holder()) > 0) continue;

            CombatModifierModel modifier = new CombatModifierModel();
            modifier.setAlias("iron_advanced_targeting_systems");
            modifier.setType(Constants.COMBAT_EXTRA_ROLLS);
            modifier.setValue(1);
            modifier.setApplyEachForQuantity(true);
            modifier.setPersistenceType(Constants.MOD_TEMP_ONE_ROUND.toString());
            modifier.setScope("mf");
            modifier.setRelated(List.of());
            modifier.setForCombatAbility(CombatRollType.combatround);
            return List.of(new NamedCombatModifierModel(modifier, "_Advanced Targeting Systems_"));
        }
        return List.of();
    }

    private static void clearAdvancedTargetingSystems(Game game, Player player) {
        String suffix = player.getFaction();
        game.removeStoredValue("ironATSActiveTile_" + suffix);
        game.removeStoredValue("ironATSActiveOpponent_" + suffix);
        game.removeStoredValue("ironATSBoundHolder_" + suffix);
    }

    private static BombardmentModifiers bombardmentModifiers(ModifierInputs inputs) {
        if (inputs.rollType() != CombatRollType.bombardment) return BombardmentModifiers.empty();
        Request request = inputs.request();
        String target = request.storedValue("bombardmentTarget" + request.getFaction());
        String serialized = request.storedValue("assignedBombardment" + request.getFaction());
        if (target.isBlank() || serialized.isBlank()) return BombardmentModifiers.empty();

        List<BombardmentAssignment> assignments =
                JsonMapperManager.basic().readValue(serialized, new TypeReference<List<BombardmentAssignment>>() {});
        Set<String> sourceIds = new HashSet<>();
        Map<String, Integer> galvanizedByUnit = new HashMap<>();
        for (BombardmentAssignment assignment : assignments) {
            if (!target.equals(assignment.planet()) || assignment.sourceId() == null) continue;
            sourceIds.add(assignment.sourceId());
            if (assignment.galvanized()) galvanizedByUnit.merge(assignment.sourceId(), 1, Integer::sum);
        }
        return new BombardmentModifiers(true, sourceIds, galvanizedByUnit);
    }

    private static void filterBombardmentExtras(
            List<NamedCombatModifierModel> extraRolls, BombardmentModifiers bombardment) {
        if (!bombardment.assignmentsPresent()) return;
        extraRolls.removeIf(modifier -> !isAssignedBombardmentExtra(modifier, bombardment));
    }

    private static boolean isAssignedBombardmentExtra(
            NamedCombatModifierModel modifier, BombardmentModifiers bombardment) {
        CombatModifierModel rule = modifier.getModifier();
        String alias = rule.getAlias().toLowerCase();
        return switch (alias) {
            case "plus1_roll_plasmascoring" -> bombardment.hasSource("plasmascoring");
            case "plus1_roll_argent_commander_bombard" -> bombardment.hasSource("argentcommander");
            case "roll_1_for_galvanize_bombard" ->
                !bombardment.galvanizedByUnit().isEmpty();
            default -> true;
        };
    }

    private static ModifierIndex indexModifiers(ModifierInputs inputs) {
        List<UnitModel> units = List.copyOf(inputs.units().keySet());
        Map<ModifierKey, List<CombatModifierModel>> bySource = new LinkedHashMap<>();
        Mapper.getCombatModifiers().values().stream()
                .filter(modifier -> modifier.getForCombatAbility() == inputs.rollType())
                .filter(modifier -> Constants.COMBAT_MODIFIERS.equals(modifier.getType())
                        || Constants.COMBAT_EXTRA_ROLLS.equals(modifier.getType()))
                .filter(modifier -> !modifier.getApplyToOpponent())
                .filter(modifier -> isInScopeForAnyUnit(modifier, units, inputs))
                .sorted(java.util.Comparator.comparing(CombatModifierModel::getAlias))
                .forEach(modifier -> modifier.getRelated().forEach(related -> bySource.computeIfAbsent(
                                new ModifierKey(related.getType(), related.getAlias()), ignored -> new ArrayList<>())
                        .add(modifier)));
        return new ModifierIndex(bySource);
    }

    private static boolean isInScopeForAnyUnit(
            CombatModifierModel modifier, List<UnitModel> units, ModifierInputs inputs) {
        for (UnitModel unit : units) {
            if (modifier.isInScopeForUnit(unit, units, inputs.rollType(), inputs.game(), inputs.player())) return true;
        }
        return false;
    }

    private static CollectedModifiers collectModifiers(ModifierInputs inputs, ModifierIndex index) {
        Request request = inputs.request();
        Player player = request.player();
        CollectedModifiers result = new CollectedModifiers(new ArrayList<>(), new ArrayList<>());

        addSources(
                result, index, inputs, Constants.ABILITY, player.getAbilities(), ability -> Mapper.getAbility(ability)
                        .getRepresentation());
        addSources(result, index, inputs, Constants.TECH, player.getTechs(), tech -> Mapper.getTech(tech)
                .getRepresentation(true));
        addOpponentTechnologies(result, index, inputs);
        addSources(result, index, inputs, Constants.RELIC, player.getRelics(), relic -> Mapper.getRelic(relic)
                .getSimpleRepresentation());
        addAgendas(result, index, inputs);
        addUnits(result, index, inputs);
        addLeaders(result, index, inputs);
        addBreakthroughs(result, index, inputs);
        addCustomModifiers(result, index, inputs);
        return new CollectedModifiers(distinctInOrder(result.hitModifiers()), distinctInOrder(result.extraRolls()));
    }

    private static void addSources(
            CollectedModifiers result,
            ModifierIndex index,
            ModifierInputs inputs,
            String sourceType,
            Collection<String> sourceIds,
            Function<String, String> displayName) {
        for (String sourceId : sourceIds) {
            addApplicable(result, index.find(sourceType, sourceId), inputs, displayName.apply(sourceId));
        }
    }

    private static void addApplicable(
            CollectedModifiers result,
            List<CombatModifierModel> candidates,
            ModifierInputs inputs,
            String displayName) {
        for (CombatModifierModel modifier : candidates) {
            if (passesCondition(modifier, inputs)) {
                result.add(new NamedCombatModifierModel(modifier, displayName));
            }
        }
    }

    private static void addOpponentTechnologies(CollectedModifiers result, ModifierIndex index, ModifierInputs inputs) {
        Player player = inputs.player();
        Player opponent = inputs.opponent();
        Game game = inputs.game();
        if (opponent == null
                || opponent == player
                || (player != game.getActivePlayer() && opponent != game.getActivePlayer())) return;

        addSources(result, index, inputs, "opponent_tech", opponent.getTechs(), tech -> Mapper.getTech(tech)
                .getRepresentation(true));
    }

    private static void addAgendas(CollectedModifiers result, ModifierIndex index, ModifierInputs inputs) {
        Request request = inputs.request();
        for (var law : request.game().getLawsInfo().entrySet()) {
            if (!law.getValue().equals(request.getFaction()) && !law.getValue().equals(request.getColor())) continue;
            AgendaModel agenda = Mapper.getAgenda(law.getKey());
            addApplicable(
                    result,
                    index.find(Constants.AGENDA, agenda.getAlias()),
                    inputs,
                    CardEmojis.Agenda + " " + agenda.getName());
        }
    }

    private static void addUnits(CollectedModifiers result, ModifierIndex index, ModifierInputs inputs) {
        for (UnitModel unit : inputs.units().keySet()) {
            for (CombatModifierModel modifier : index.find(Constants.UNIT, unit.getAlias())) {
                if (passesCondition(modifier, inputs)) {
                    result.add(new NamedCombatModifierModel(modifier, unitModifierName(modifier, unit)));
                }
            }
            addCopiedFlagshipModifiers(result, index, inputs, unit);
        }
    }

    private static String unitModifierName(CombatModifierModel modifier, UnitModel unit) {
        String header = unit.getUnitEmoji() + " **__" + unit.getName() + "__**";
        return modifier.getRelated().stream()
                .filter(related -> Constants.UNIT.equals(related.getType())
                        && unit.getAlias().equals(related.getAlias())
                        && related.getMessage() != null)
                .map(related -> header + ": " + related.getMessage())
                .findFirst()
                .orElse(header + " " + unit.getAbility());
    }

    private static void addCopiedFlagshipModifiers(
            CollectedModifiers result, ModifierIndex index, ModifierInputs inputs, UnitModel unit) {
        Request request = inputs.request();
        if (unit.getUnitType() != UnitType.Flagship || !request.player().hasUnlockedBreakthrough("nekrobt")) return;
        for (String flagshipId : ValefarZService.getFlagshipAbilitys(request.game(), request.player())) {
            UnitModel copied = Mapper.getUnit(flagshipId);
            if (copied == null || copied == unit) continue;
            addApplicable(
                    result,
                    index.find(Constants.UNIT, copied.getAlias()),
                    inputs,
                    copied.getUnitEmoji() + " " + copied.getName() + " " + copied.getAbility());
        }
    }

    private static void addLeaders(CollectedModifiers result, ModifierIndex index, ModifierInputs inputs) {
        Request request = inputs.request();
        for (Leader leader : request.game().playerUnlockedLeadersOrAlliance(request.player())) {
            if (leader.isExhausted() || leader.isLocked()) continue;
            addApplicable(
                    result,
                    index.find(Constants.LEADER, leader.getId()),
                    inputs,
                    Helper.getLeaderFullRepresentation(leader));
        }
    }

    private static void addBreakthroughs(CollectedModifiers result, ModifierIndex index, ModifierInputs inputs) {
        for (BreakthroughModel breakthrough : inputs.player().getBreakthroughModels()) {
            addApplicable(
                    result,
                    index.find("breakthrough", breakthrough.getAlias()),
                    inputs,
                    breakthrough.getRepresentation(true));
        }
    }

    private static void addCustomModifiers(CollectedModifiers result, ModifierIndex index, ModifierInputs inputs) {
        for (CombatModifierModel modifier : index.find(Constants.CUSTOM, Constants.CUSTOM)) {
            if (!passesCondition(modifier, inputs)) continue;
            String displayName = modifier.getRelated().getFirst().getMessage();
            UnitModel displayUnit = Mapper.getUnit(modifier.getDisplayUnitAlias());
            if (displayUnit != null) {
                displayName = displayUnit.getUnitEmoji() + " **__" + displayUnit.getName() + "__**: " + displayName;
            }
            result.add(new NamedCombatModifierModel(modifier, displayName));
        }
    }

    private static List<NamedCombatModifierModel> distinctInOrder(List<NamedCombatModifierModel> modifiers) {
        Map<String, NamedCombatModifierModel> distinct = new LinkedHashMap<>();
        for (NamedCombatModifierModel modifier : modifiers) {
            distinct.putIfAbsent(modifier.getModifier().getAlias() + "\u0000" + modifier.getName(), modifier);
        }
        return new ArrayList<>(distinct.values());
    }

    @SafeVarargs
    private static Map<String, Long> sharedScalingValues(
            ModifierInputs inputs, List<NamedCombatModifierModel>... modifierGroups) {
        Set<String> scalingTypes = new HashSet<>();
        for (List<NamedCombatModifierModel> group : modifierGroups) {
            for (NamedCombatModifierModel modifier : group) {
                String scalingType = modifier.getModifier().getValueScalingType();
                if (StringUtils.isNotBlank(scalingType)) scalingTypes.add(scalingType);
            }
        }

        Map<String, Long> values = new HashMap<>();
        for (String scalingType : scalingTypes) {
            Long value = sharedScalingValue(scalingType, inputs);
            if (value != null) values.put(scalingType, value);
        }
        return values;
    }

    private static Long sharedScalingValue(String type, ModifierInputs inputs) {
        Request request = inputs.request();
        Player player = request.player();
        Player opponent = inputs.opponent();
        Game game = request.game();
        Tile tile = request.tile();
        UnitHolder holder = inputs.holder();
        return switch (type) {
            case Constants.FRAGMENT -> (long) foundFragmentTypes(player);
            case "code" -> (long) codeScaling(player);
            case Constants.LAW -> (long) game.getLaws().size();
            case Constants.MOD_OPPONENT_PO_EXCLUSIVE_SCORED ->
                opponent == null ? 0L : exclusiveObjectivesScored(game, player, opponent);
            case Constants.UNIT_TECH -> countUnitUpgrades(player);
            case Constants.MOD_DESTROYERS ->
                (long) ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "destroyer", false);
            case Constants.MOD_OPPONENT_NON_FIGHTER_SHIP ->
                opponent == null ? 0L : (long) ButtonHelper.checkNumberNonFighterShips(opponent, tile);
            case Constants.MOD_OPPONENT_SHIP ->
                opponent == null ? 0L : (long) ButtonHelper.checkNumberShips(opponent, tile);
            case "combat_round" -> (long) combatRound(game, player, tile, holder);
            case "adjacent_mech" -> countAdjacentMechs(game, tile, player);
            case "adjacent_asteroid" ->
                adjacentTiles(game, tile, player).stream()
                        .filter(Tile::isAsteroidField)
                        .count();
            case "adjacent_anomaly" ->
                adjacentTiles(game, tile, player).stream()
                        .filter(adjacent -> adjacent.isAnomaly(game, player))
                        .count();
            case "mechs_in_space_area" ->
                "space".equalsIgnoreCase(holder.getName()) && player.hasUnit("bluetf_mech")
                        ? (long) tile.getSpaceUnitHolder().getUnitCount(UnitType.Mech, player)
                        : 0L;
            case "mechs_on_planet" ->
                holder instanceof Planet ? (long) holder.getUnitCount(UnitType.Mech, player.getColor()) : 0L;
            case "space_docks_in_tile" ->
                countAllPlayersUnits(tile.getPlanetUnitHolders(), game.getRealPlayers(), UnitType.Spacedock);
            case "mechs_on_planet_minus_one" ->
                holder instanceof Planet
                        ? (long) Math.max(0, holder.getUnitCount(UnitType.Mech, player.getColor()) - 1)
                        : 0L;
            case "opponent_sftt" -> opponent == null ? 0L : (long) getOpponentSfttCount(opponent);
            case "nonhome_system_with_planet" -> (long) getSystemsWithControlledPlanets(game, player);
            case "unique_ships" -> (long) getUniqueNonFighterShipCount(tile, player);
            case Constants.MOD_OPPONENT_UNIT_TECH -> opponent == null ? 0L : countUnitUpgrades(opponent);
            case Constants.MOD_OPPONENT_FACTION_TECH ->
                opponent == null
                        ? 0L
                        : opponent.getTechs().stream()
                                .map(Mapper::getTech)
                                .filter(tech ->
                                        StringUtils.isNotBlank(tech.getFaction().orElse("")))
                                .count();
            default -> null;
        };
    }

    private static long exclusiveObjectivesScored(Game game, Player player, Player opponent) {
        return game.getScoredPublicObjectives().entrySet().stream()
                .filter(entry -> !game.getCustomPublicVP().containsKey(entry.getKey()))
                .map(Entry::getValue)
                .filter(scored -> scored.contains(opponent.getUserID()) && !scored.contains(player.getUserID()))
                .count();
    }

    private static long countUnitUpgrades(Player player) {
        return player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(TechnologyModel::isUnitUpgrade)
                .count();
    }

    private static int combatRound(Game game, Player player, Tile tile, UnitHolder holder) {
        String key = "combatRoundTracker" + player.getFaction() + tile.getPosition() + holder.getName();
        String stored = game.getStoredValue(key);
        return stored.isEmpty() ? 0 : Integer.parseInt(stored) - 1;
    }

    private record ModifierKey(String sourceType, String sourceAlias) {}

    private record ModifierIndex(Map<ModifierKey, List<CombatModifierModel>> bySource) {
        private List<CombatModifierModel> find(String sourceType, String sourceAlias) {
            return bySource.getOrDefault(new ModifierKey(sourceType, sourceAlias), List.of());
        }
    }

    private record CollectedModifiers(
            List<NamedCombatModifierModel> hitModifiers, List<NamedCombatModifierModel> extraRolls) {
        private void add(NamedCombatModifierModel modifier) {
            if (Constants.COMBAT_EXTRA_ROLLS.equals(modifier.getModifier().getType())) extraRolls.add(modifier);
            else hitModifiers.add(modifier);
        }
    }

    static UnitModifiers forUnit(Context context, UnitModel unit, int quantity, UnitHolder holder) {
        List<UnitModel> allUnits = List.copyOf(context.rollingUnitModels());
        int toHit = 0;
        int extraDice = 0;
        for (var applied : context.appliedModifiers()) {
            CombatModifierModel modifier = applied.rule();
            if (!modifier.isInScopeForUnit(unit, allUnits, context.rollType(), context.game(), context.player()))
                continue;

            int value = getVariableModValue(modifier, context, unit, holder);
            if (modifier.getApplyEachForQuantity()) value *= quantity;
            if (applied.kind() == ModifierKind.EXTRA_DIE) extraDice += value;
            else toHit += value;
        }
        return new UnitModifiers(toHit, extraDice);
    }

    private static boolean passesCondition(CombatModifierModel modifier, ModifierInputs inputs) {
        Request request = inputs.request();
        TileModel onTile = request.tileModel();
        Player player = request.player();
        Player opponent = inputs.opponent();
        Map<UnitModel, Integer> unitsByQuantity = inputs.units();
        Map<UnitModel, Integer> opponentUnitsByQuantity = inputs.opponentUnits();
        UnitHolder unitHolder = inputs.holder();
        Game game = request.game();
        boolean meetsCondition = false;

        Tile tile = null;
        String onTileId = onTile == null ? null : onTile.getId();
        if (onTileId != null) {
            tile = game.getTile(onTileId);
        }
        String condition = "";
        if (modifier != null && modifier.getCondition() != null) {
            condition = modifier.getCondition();
        }
        switch (condition) {
            case Constants.MOD_OPPONENT_TEKKLAR_PLAYER_OWNER -> {
                var promissoryNotes = player.getPromissoryNotesOwned();
                if (opponent != null
                        && (promissoryNotes.stream().anyMatch("tekklar"::equals)
                                || promissoryNotes.stream().anyMatch("sigma_tekklar_legion"::equals))) {
                    meetsCondition = opponent.getTempCombatModifiers().stream()
                                    .anyMatch(mod -> "tekklar".equals(mod.getRelatedID())
                                            && Constants.PROMISSORY_NOTES.equals(mod.getRelatedType()))
                            || opponent.getNewTempCombatModifiers().stream()
                                    .anyMatch(mod -> "tekklar".equals(mod.getRelatedID())
                                            && Constants.PROMISSORY_NOTES.equals(mod.getRelatedType()));
                }
            }
            case Constants.MOD_OPPONENT_FRAG -> {
                if (opponent != null) {
                    meetsCondition = !opponent.getFragments().isEmpty();
                }
            }
            case Constants.MOD_OPPONENT_STOLEN_TECH -> {
                if (ButtonHelper.isLawInPlay(game, "articles_war")) {
                    return false;
                }
                if (opponent != null) {
                    String opponentFaction = opponent.getFaction();
                    if (opponentFaction.contains("keleres")) {
                        meetsCondition = player.getTechs().stream()
                                .map(Mapper::getTech)
                                .anyMatch(tech ->
                                        "keleres".equals(tech.getFaction().orElse("")));
                    } else {
                        meetsCondition = player.getTechs().stream()
                                .map(Mapper::getTech)
                                .anyMatch(tech -> {
                                    String faction = tech.getFaction().orElse("");
                                    return faction.equals(opponentFaction);
                                });
                    }
                }
            }
            case Constants.MOD_PLANET_MR_LEGEND_HOME -> {
                if (onTile == null) break;
                Tile homeSystemTile = player.getHomeSystemTile();
                if (homeSystemTile != null && onTileId.equals(homeSystemTile.getTileID())) {
                    meetsCondition = true;
                }
                if (onTile.getPlanets() != null
                        && onTile.getPlanets().stream()
                                .anyMatch(planetId -> StringUtils.isNotBlank(
                                        Mapper.getPlanet(planetId).getLegendaryAbilityName()))) {
                    meetsCondition = true;
                }
                if (onTile.getPlanets() != null && onTile.getPlanets().contains(Constants.MR)) {
                    meetsCondition = true;
                }
                if (game.getTile(onTileId) != null) {
                    if (ButtonHelper.isTileLegendary(game.getTile(onTileId))) {
                        meetsCondition = true;
                    }
                }
            }
            case Constants.MOD_HAS_FRAGILE ->
                meetsCondition = player.hasAbility("fragile") && !ButtonHelper.isLawInPlay(game, "articles_war");
            case Constants.MOD_OPPONENT_NO_CC_FLEET ->
                meetsCondition = !player.getMahactCC().contains(opponent.getColor());
            case "next_to_structure" ->
                meetsCondition = (!ButtonHelperAgents.getAdjacentTilesWithStructuresInThem(player, game, tile)
                                .isEmpty()
                        || ButtonHelperAgents.doesTileHaveAStructureInIt(player, tile));
            case "fracture_combat" ->
                meetsCondition = tile != null && tile.getPosition().contains("frac");
            case Constants.MOD_UNITS_TWO_MATCHING_NOT_FF ->
                meetsCondition = hasTwoMatchingNonFighterFormation(unitsByQuantity);
            case Constants.MOD_NEBULA_DEFENDER -> {
                Player activePlayer = game.getActivePlayer();
                if (onTile != null
                        && (onTile.isNebula() || tile.isNebula(game))
                        && activePlayer != null
                        && !activePlayer.getUserID().equals(player.getUserID())
                        && !activePlayer.getAllianceMembers().contains(player.getFaction())
                        && !game.getStoredValue("mahactHeroTarget").equalsIgnoreCase(player.getFaction())) {
                    meetsCondition = true;
                }
            }
            case "nebula_cosmic_defender" -> {
                Player activePlayer = game.getActivePlayer();
                if (game.isCosmicPhenomenaeMode()
                        && onTile != null
                        && (onTile.isNebula() || tile.isNebula(game))
                        && activePlayer != null
                        && !activePlayer.getUserID().equals(player.getUserID())
                        && !activePlayer.getAllianceMembers().contains(player.getFaction())
                        && !game.getStoredValue("mahactHeroTarget").equalsIgnoreCase(player.getFaction())) {
                    meetsCondition = true;
                }
            }
            case "arcane_defender" -> meetsCondition = isArcaneCitadelDefense(tile, player, game, unitsByQuantity);
            case "vaylerianhero" -> {
                if (player == game.getActivePlayer()
                        && !game.getStoredValue("vaylerianHeroActive").isEmpty()) {
                    meetsCondition = true;
                }
            }
            case "tnelisopponentfs" -> {
                if (ButtonHelper.doesPlayerHaveFSHere("tnelis_flagship", opponent, tile)
                        && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)
                        && FoWHelper.playerHasShipsInSystem(player, tile)) {
                    meetsCondition = true;
                }
            }
            case "solagent" -> {
                if (game.getStoredValue("solagent").contains(player.getFaction())) {
                    meetsCondition = true;
                }
            }
            case "letnevagent" -> {
                if (game.getStoredValue("letnevagent").contains(player.getFaction())) {
                    meetsCondition = true;
                }
            }
            case "classifiedWeapons" -> {
                if (game.getStoredValue("classifiedWeapons").startsWith(player.getFaction() + ";")) {
                    meetsCondition = true;
                }
            }
            case "thalnosPlusOne" -> {
                if ("true".equalsIgnoreCase(game.getStoredValue("thalnosPlusOne"))) {
                    meetsCondition = true;
                }
            }
            case "galvanized" ->
                meetsCondition = hasRelevantGalvanizedUnit(tile, unitHolder, player, game, unitsByQuantity.keySet());
            case "opponent_has_sftt" -> {
                if (player.hasUnlockedBreakthrough("winnubt") && getOpponentSfttCount(opponent) > 0) {
                    meetsCondition = true;
                }
            }
            case "opponent_has_been_asailed" -> {
                if (player.hasAbility("marionettes")
                        && player.getPuppetedFactionsForPlot("assail").contains(opponent.getFaction())) {
                    meetsCondition = true;
                }
            }
            case "nivyn_commander_damaged" -> {
                if (game.playerHasLeaderUnlockedOrAlliance(player, "nivyncommander")) {
                    meetsCondition = true;
                }
            }
            case "toldar_commander_particular" -> {
                if (game.playerHasLeaderUnlockedOrAlliance(player, "toldarcommander")) {
                    meetsCondition = totalUnits(unitsByQuantity) < totalUnits(opponentUnitsByQuantity);
                }
            }
            case "lizho_commander_particular" -> {
                if (game.playerHasLeaderUnlockedOrAlliance(player, "lizhocommander")) {
                    UnitSummary units = summarizeUnits(unitsByQuantity);
                    meetsCondition = units.ships() > 0 ? units.nonFighterShips() < 2 : units.infantry() < 2;
                }
            }
            case "naazFS" -> {
                if (ButtonHelper.doesPlayerHaveFSHere(
                                "naaz_flagship", player, game.getTileByPosition(game.getActiveSystem()))
                        || ButtonHelper.doesPlayerHaveFSHere(
                                "sigma_naazrokha_flagship_2", player, game.getTileByPosition(game.getActiveSystem()))) {
                    meetsCondition = true;
                }
            }
            case "technotemplar" -> meetsCondition = hasTechnotemplarMech(tile, player, game, unitsByQuantity.keySet());
            case "opponent_strat_cards_exhausted" ->
                meetsCondition = opponent != null && game.getPlayedSCs().containsAll(opponent.getSCs());
            case "space_dock_on_holder" ->
                meetsCondition = unitHolder != null && hasAnyPlayersUnit(unitHolder, game, UnitType.Spacedock);
            case "arvaxi_engine" -> {
                String stored = game.getStoredValue("arvaxiMobilizationEngine");
                if (!stored.isEmpty()) {
                    int firstSep = stored.indexOf('_');
                    meetsCondition = firstSep > 0 && player.getFaction().equals(stored.substring(0, firstSep));
                }
            }
            case "bluetfMech" ->
                meetsCondition = player.hasUnit("bluetf_mech")
                        && unitsByQuantity.keySet().stream().anyMatch(unit -> unit.getCapacityValue() > 0);
            case "wildMB" -> {
                meetsCondition = game.isWildWildGalaxyMode()
                        && !game.getStoredValue("wildMB" + player.getFaction()).isEmpty();
            }
            case "sigma_argent_flagship_1" ->
                meetsCondition = ButtonHelper.doesPlayerHaveFSHere(
                        "sigma_argent_flagship_1", player, game.getTileByPosition(game.getActiveSystem()));
            case "sigma_argent_flagship_2" -> {
                meetsCondition = ButtonHelper.doesPlayerHaveFSHere("sigma_argent_flagship_2", player, tile);
                meetsCondition |=
                        FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false).stream()
                                .map(game::getTileByPosition)
                                .anyMatch(adjacent ->
                                        ButtonHelper.doesPlayerHaveFSHere("sigma_argent_flagship_2", player, adjacent));
            }
            case "" -> meetsCondition = true;
            default ->
                BotLogger.warning("Combat V2 ignored unknown modifier condition `" + condition + "` on modifier `"
                        + modifier.getAlias() + "`.");
        }
        return meetsCondition;
    }

    private static boolean hasTwoMatchingNonFighterFormation(Map<UnitModel, Integer> units) {
        List<Entry<UnitModel, Integer>> entries = List.copyOf(units.entrySet());
        if (entries.size() == 1) {
            return entries.getFirst().getValue() == 2
                    && !isBaseType(entries.getFirst().getKey(), "fighter");
        }
        if (entries.size() == 2) {
            Entry<UnitModel, Integer> first = entries.get(0);
            Entry<UnitModel, Integer> second = entries.get(1);
            if (isBaseType(first.getKey(), "fighter") || isBaseType(second.getKey(), "fighter")) {
                Entry<UnitModel, Integer> nonFighter = isBaseType(first.getKey(), "fighter") ? second : first;
                return nonFighter.getValue() == 2;
            }
            return isFlagshipOrLady(first.getKey()) && isFlagshipOrLady(second.getKey());
        }
        return entries.size() == 3
                && entries.stream()
                        .allMatch(entry -> isBaseType(entry.getKey(), "fighter") || isFlagshipOrLady(entry.getKey()));
    }

    private static boolean isFlagshipOrLady(UnitModel unit) {
        return isBaseType(unit, "flagship") || isBaseType(unit, "lady");
    }

    private static boolean isBaseType(UnitModel unit, String type) {
        return type.equalsIgnoreCase(unit.getBaseType());
    }

    private static boolean isArcaneCitadelDefense(Tile tile, Player player, Game game, Map<UnitModel, Integer> units) {
        if (tile == null || game.getActivePlayer() == player) return false;
        return tile.getPlanetUnitHolders().stream()
                .filter(holder -> holder.getTokenList().contains("attachment_arcane_citadel.png"))
                .filter(holder -> player.getPlanets().contains(holder.getName()))
                .anyMatch(holder -> allUnitsAreInHolder(holder, player, units));
    }

    private static boolean allUnitsAreInHolder(UnitHolder holder, Player player, Map<UnitModel, Integer> units) {
        return units.entrySet().stream()
                .allMatch(entry -> holder.getUnitCount(entry.getKey().getUnitType(), player) == entry.getValue());
    }

    private static boolean hasRelevantGalvanizedUnit(
            Tile tile, UnitHolder combatHolder, Player player, Game game, Collection<UnitModel> rollingUnits) {
        if (combatHolder != null && hasGalvanizedUnit(combatHolder, player)) return true;
        if (tile != null
                && tile.getUnitHolders().values().stream().anyMatch(holder -> hasGalvanizedUnit(holder, player)))
            return true;
        if (rollingUnits.stream().noneMatch(unit -> "xxcha_flagship".equalsIgnoreCase(unit.getId()))) return false;
        return ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Flagship).stream()
                .flatMap(flagshipTile -> flagshipTile.getUnitHolders().values().stream())
                .anyMatch(holder -> hasGalvanizedUnit(holder, player));
    }

    private static boolean hasGalvanizedUnit(UnitHolder holder, Player player) {
        return holder.getUnitsByStateForPlayer(player.getColorID()).keySet().stream()
                .anyMatch(key -> holder.getGalvanizedUnitCount(key) > 0);
    }

    private static int totalUnits(Map<UnitModel, Integer> units) {
        return units.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static UnitSummary summarizeUnits(Map<UnitModel, Integer> units) {
        int ships = 0;
        int nonFighterShips = 0;
        int infantry = 0;
        for (Entry<UnitModel, Integer> entry : units.entrySet()) {
            UnitModel unit = entry.getKey();
            int quantity = entry.getValue();
            if (unit.getIsShip()) {
                ships += quantity;
                if (!isBaseType(unit, "fighter")) nonFighterShips += quantity;
            } else if (isBaseType(unit, "infantry")) {
                infantry += quantity;
            }
        }
        return new UnitSummary(ships, nonFighterShips, infantry);
    }

    private static boolean hasTechnotemplarMech(
            Tile tile, Player player, Game game, Collection<UnitModel> rollingUnits) {
        if (tile == null || !player.hasUnit("vyserix_mech")) return false;
        List<Tile> tiles = new ArrayList<>(List.of(tile));
        if (rollingUnits.stream().anyMatch(UnitModel::getDeepSpaceCannon)) {
            FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true).stream()
                    .map(game::getTileByPosition)
                    .filter(java.util.Objects::nonNull)
                    .forEach(tiles::add);
        }
        return tiles.stream()
                .flatMap(candidate -> candidate.getPlanetUnitHolders().stream())
                .anyMatch(holder -> holder.getUnitCount(UnitType.Mech, player.getColor()) > 0);
    }

    private static boolean hasAnyPlayersUnit(UnitHolder holder, Game game, UnitType type) {
        return game.getRealPlayers().stream().anyMatch(player -> holder.getUnitCount(type, player.getColor()) > 0);
    }

    private record UnitSummary(int ships, int nonFighterShips, int infantry) {}

    private static Integer getVariableModValue(
            CombatModifierModel mod, Context context, UnitModel origUnit, UnitHolder unitHolder) {
        Player player = context.player();
        Player opponent = context.opponent();
        Game game = context.game();
        Tile activeSystem = context.tile();
        CombatRollType rollType = context.rollType();
        double value = mod.getValue().doubleValue();
        double multiplier = 1.0;
        long scalingCount = 0;
        if (mod.getValueScalingMultiplier() != null) {
            multiplier = mod.getValueScalingMultiplier();
        }
        if (StringUtils.isNotBlank(mod.getValueScalingType())) {
            String scalingType = mod.getValueScalingType();
            Long sharedValue = context.modifiers().sharedScalingValues().get(scalingType);
            if (sharedValue != null) {
                scalingCount = sharedValue;
            } else {
                switch (scalingType) {
                    case "arvaxi_engine" -> {
                        if (arvaxiEngineAttached(game, player, origUnit)) {
                            scalingCount = game.getStoredValue("arvaxiMobilizationEngine")
                                            .endsWith("_boon")
                                    ? 1
                                    : -1;
                        }
                    }
                    case "damaged_units_max_2", "damaged_units" ->
                        scalingCount = damagedUnitCount(activeSystem, origUnit, player);
                    case "galvanized_unit_count" -> {
                        scalingCount = rollType == CombatRollType.bombardment
                                ? context.modifiers().bombardment().galvanizedCount(origUnit.getAsyncId())
                                : getGalvanizedUnitCount(unitHolder, origUnit, player);
                        if (rollType == CombatRollType.SpaceCannonOffence && origUnit.getDeepSpaceCannon()) {
                            scalingCount += adjacentTiles(game, activeSystem, player).stream()
                                    .flatMap(tile -> tile.getUnitHolders().values().stream())
                                    .mapToInt(holder -> getGalvanizedUnitCount(holder, origUnit, player))
                                    .sum();
                        }
                    }
                    default -> {}
                }
            }
            value *= multiplier * scalingCount;
        }
        value = Math.floor(value); // to make sure eg +1 per 2 destroyer doesn't return 2.5 etc
        return (int) value;
    }

    private static int foundFragmentTypes(Player player) {
        int count = 0;
        if (player.isHasFoundCulFrag()) count++;
        if (player.isHasFoundHazFrag()) count++;
        if (player.isHasFoundIndFrag()) count++;
        if (player.isHasFoundUnkFrag()) count++;
        return count;
    }

    private static int codeScaling(Player player) {
        int honor = player.getHonorCounter() > 1 ? 1 : 0;
        if (player.getHonorCounter() > 4) honor++;
        if (player.getHonorCounter() > 7) honor++;
        int dishonor = player.getDishonorCounter() > 1 ? 1 : 0;
        if (player.getDishonorCounter() > 1 && player.getDishonorCounter() < 4) dishonor += 2;
        return honor + dishonor;
    }

    private static List<Tile> adjacentTiles(Game game, Tile tile, Player player) {
        return FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true).stream()
                .map(game::getTileByPosition)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static long countAdjacentMechs(Game game, Tile tile, Player player) {
        return countAllPlayersUnits(
                adjacentTiles(game, tile, player).stream()
                        .flatMap(adjacent -> adjacent.getUnitHolders().values().stream())
                        .toList(),
                game.getRealPlayers(),
                UnitType.Mech);
    }

    private static long countAllPlayersUnits(
            Collection<? extends UnitHolder> holders, Collection<Player> players, UnitType type) {
        long count = 0;
        for (UnitHolder holder : holders) {
            for (Player player : players) count += holder.getUnitCount(type, player.getColor());
        }
        return count;
    }

    private static int damagedUnitCount(Tile tile, UnitModel unit, Player player) {
        UnitKey key = Units.getUnitKey(unit.getUnitType(), player.getColor());
        UnitHolder holder = tile.getSpaceUnitHolder();
        if (unit.getIsGroundForce()) {
            for (UnitHolder planet : tile.getPlanetUnitHolders()) {
                if (planet.getUnitCount(key) > 0) holder = planet;
            }
        }
        return holder.getDamagedUnitCount(key);
    }

    private static int getUniqueNonFighterShipCount(Tile activeSystem, Player player) {
        UnitHolder space = activeSystem.getSpaceUnitHolder();
        return (int) space.getUnitsByState().keySet().stream()
                .filter(player::unitBelongsToPlayer)
                .filter(key -> space.getUnitCount(key) > 0)
                .map(player::getUnitFromUnitKey)
                .filter(java.util.Objects::nonNull)
                .filter(UnitModel::getIsShip)
                .count();
    }

    private static int getOpponentSfttCount(Player player) {
        return (int) player.getPromissoryNotesInPlayArea().stream()
                .map(Mapper::getPromissoryNote)
                .filter(pn -> "Support for the Throne".equals(pn.getName()))
                .count();
    }

    private static int getSystemsWithControlledPlanets(Game game, Player player) {
        return (int) game.getTileMap().values().stream()
                .filter(tile -> !tile.isHomeSystem(game))
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(holder -> player.getPlanetsAllianceMode().contains(holder.getName())))
                .count();
    }

    private static int getGalvanizedUnitCount(UnitHolder uH, UnitModel origUnit, Player player) {
        UnitKey uk = Units.getUnitKey(origUnit.getUnitType(), player.getColorID());
        return uH.getGalvanizedUnitCount(uk);
    }

    private static boolean arvaxiEngineAttached(Game game, Player player, UnitModel unit) {
        String stored = game.getStoredValue("arvaxiMobilizationEngine");
        int firstSeparator = stored.indexOf('_');
        int lastSeparator = stored.lastIndexOf('_');
        if (stored.isEmpty()
                || firstSeparator < 0
                || firstSeparator == lastSeparator
                || !player.getFaction().equals(stored.substring(0, firstSeparator))) return false;
        String technology = stored.substring(firstSeparator + 1, lastSeparator);
        UnitModel attachedUnit = Mapper.getUnitModelByTechUpgrade(technology);
        return attachedUnit != null && attachedUnit.getAsyncId().equals(unit.getAsyncId());
    }

    static void initializeNewTempMods(Player player, TileModel tile, UnitHolder holder) {
        List<TemporaryCombatModifierModel> unusedMods = player.getNewTempCombatModifiers().stream()
                .filter(mod -> mod.getUseInTurn() == player.getNumberOfTurns())
                .toList();
        for (TemporaryCombatModifierModel mod : unusedMods) {
            mod.setUseInSystem(tile.getId());
            mod.setUseInUnitHolder(holder.getName());
            player.addTempCombatMod(mod);
        }
        player.clearNewTempCombatModifiers();
    }

    static void ensureValidTempMods(Player player, TileModel tile, UnitHolder holder) {
        List<TemporaryCombatModifierModel> tempMods = new ArrayList<>(player.getTempCombatModifiers());
        for (TemporaryCombatModifierModel mod : tempMods) {
            if (mod.getUseInTurn() != player.getNumberOfTurns()) {
                player.removeTempMod(mod);
                continue;
            }
            switch (mod.getModifier().getPersistenceType()) {
                case Constants.MOD_TEMP_ONE_COMBAT -> {
                    if (!mod.getUseInUnitHolder().equals(holder.getName())
                            || !mod.getUseInSystem().equals(tile.getId())) player.removeTempMod(mod);
                }
                case Constants.MOD_TEMP_ONE_TACTICAL_ACTION -> {
                    if (!mod.getUseInSystem().equals(tile.getId())) player.removeTempMod(mod);
                }
            }
        }
    }

    static List<NamedCombatModifierModel> currentTemporaryModifiers(
            Player player, boolean applyToOpponent, CombatRollType rollType) {
        return player.getTempCombatModifiers().stream()
                .filter(temporary ->
                        temporary.getModifier().getApplyToOpponent().equals(applyToOpponent))
                .filter(temporary -> temporary.getModifier().getForCombatAbility() == rollType)
                .map(CombatV2ModifierService::namedTemporaryModifier)
                .toList();
    }

    private static List<NamedCombatModifierModel> currentTemporaryModifiers(
            Player player, boolean applyToOpponent, CombatRollType rollType, TileModel tile, UnitHolder holder) {
        return player.getTempCombatModifiers().stream()
                .filter(temporary -> isValidTemporaryModifier(temporary, player, tile, holder))
                .filter(temporary ->
                        temporary.getModifier().getApplyToOpponent().equals(applyToOpponent))
                .filter(temporary -> temporary.getModifier().getForCombatAbility() == rollType)
                .map(CombatV2ModifierService::namedTemporaryModifier)
                .toList();
    }

    private static List<NamedCombatModifierModel> pendingTemporaryModifiers(
            Player player, boolean applyToOpponent, CombatRollType rollType) {
        return player.getNewTempCombatModifiers().stream()
                .filter(temporary -> temporary.getUseInTurn() == player.getNumberOfTurns())
                .filter(temporary ->
                        temporary.getModifier().getApplyToOpponent().equals(applyToOpponent))
                .filter(temporary -> temporary.getModifier().getForCombatAbility() == rollType)
                .map(CombatV2ModifierService::namedTemporaryModifier)
                .toList();
    }

    private static NamedCombatModifierModel namedTemporaryModifier(TemporaryCombatModifierModel temporary) {
        return new NamedCombatModifierModel(
                temporary.getModifier(), Mapper.getRelatedName(temporary.getRelatedID(), temporary.getRelatedType()));
    }

    private static boolean isValidTemporaryModifier(
            TemporaryCombatModifierModel temporary, Player player, TileModel tile, UnitHolder holder) {
        if (temporary.getUseInTurn() != player.getNumberOfTurns()) return false;
        return switch (temporary.getModifier().getPersistenceType()) {
            case Constants.MOD_TEMP_ONE_COMBAT ->
                holder.getName().equals(temporary.getUseInUnitHolder())
                        && tile.getId().equals(temporary.getUseInSystem());
            case Constants.MOD_TEMP_ONE_TACTICAL_ACTION -> tile.getId().equals(temporary.getUseInSystem());
            default -> true;
        };
    }

    private static void consumeTemporaryModifiers(Player player, boolean applyToOpponent, CombatRollType rollType) {
        for (TemporaryCombatModifierModel temporary : new ArrayList<>(player.getTempCombatModifiers())) {
            CombatModifierModel rule = temporary.getModifier();
            if (!rule.getApplyToOpponent().equals(applyToOpponent) || rule.getForCombatAbility() != rollType) continue;
            if (Constants.MOD_TEMP_ONE_ROUND.equals(rule.getPersistenceType()) && rollType != CombatRollType.AFB)
                player.removeTempMod(temporary);
        }
    }
}

record ModifierInputs(
        Request request,
        CombatRollType rollType,
        UnitHolder holder,
        Player opponent,
        Map<UnitModel, Integer> units,
        Map<UnitModel, Integer> opponentUnits) {
    Player player() {
        return request.player();
    }

    Game game() {
        return request.game();
    }

    Tile tile() {
        return request.tile();
    }
}
