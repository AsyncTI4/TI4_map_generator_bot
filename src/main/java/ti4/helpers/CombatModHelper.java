package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.model.AbilityModel;
import ti4.model.AgendaModel;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;

public class CombatModHelper {

    public static Boolean IsModInScopeForUnits(List<UnitModel> units, CombatModifierModel modifier,
            CombatRollType rollType) {
        for (UnitModel unit : units) {
            if (modifier.isInScopeForUnit(unit, units, rollType)) {
                return true;
            }
        }
        return false;
    }

    public static List<NamedCombatModifierModel> GetModifiers(Player player, Player opponent,
            Map<UnitModel, Integer> unitsByQuantity,
            TileModel tile,
            Game activeGame,
            CombatRollType rollType,
            String modifierType) {
        List<NamedCombatModifierModel> modifiers = new ArrayList<>();
        HashMap<String, CombatModifierModel> combatModifiers = new HashMap<>(Mapper.getCombatModifiers());
        combatModifiers = new HashMap<>(combatModifiers.entrySet().stream()
                .filter(entry -> entry.getValue().getForCombatAbility().equals(rollType.toString()))
                .filter(entry -> entry.getValue().getType().equals(modifierType))
                .filter(entry -> !entry.getValue().getApplyToOpponent())
                .filter(entry -> IsModInScopeForUnits(new ArrayList<>(unitsByQuantity.keySet()), entry.getValue(), rollType))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

        for (String ability : player.getAbilities()) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.ABILITY, ability))
                    .findFirst();

            if (relevantMod.isPresent()
                    && checkModPassesCondition(relevantMod.get(), tile, player, opponent, unitsByQuantity,
                            activeGame)) {
                AbilityModel abilityModel = Mapper.getAbility(ability);
                modifiers.add(new NamedCombatModifierModel(relevantMod.get(), abilityModel.getRepresentation()));
            }
        }

        for (String tech : player.getTechs()) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.TECH, tech))
                    .findFirst();

            if (relevantMod.isPresent()
                    && checkModPassesCondition(relevantMod.get(), tile, player, opponent, unitsByQuantity,
                            activeGame)) {
                TechnologyModel technologyModel = Mapper.getTech(tech);
                modifiers
                        .add(new NamedCombatModifierModel(relevantMod.get(), technologyModel.getRepresentation(true)));
            }
        }

        if (opponent != null && opponent != player && ((player != activeGame.getActivePlayerObject() && opponent == activeGame.getActivePlayerObject()) || player == activeGame.getActivePlayerObject())) {
            for (String tech : opponent.getTechs()) {
                Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                        .filter(modifier -> modifier.isRelevantTo("opponent_tech", tech))
                        .findFirst();

                if (relevantMod.isPresent()
                        && checkModPassesCondition(relevantMod.get(), tile, player, opponent, unitsByQuantity,
                                activeGame)) {
                    TechnologyModel technologyModel = Mapper.getTech(tech);
                    modifiers.add(
                            new NamedCombatModifierModel(relevantMod.get(), technologyModel.getRepresentation(true)));
                }
            }
        }

        for (String relic : player.getRelics()) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.RELIC, relic))
                    .findFirst();

            if (relevantMod.isPresent() && checkModPassesCondition(relevantMod.get(), tile, player, opponent,
                    unitsByQuantity, activeGame)) {
                RelicModel relicModel = Mapper.getRelic(relic);
                modifiers.add(new NamedCombatModifierModel(relevantMod.get(), relicModel.getSimpleRepresentation()));
            }
        }

        List<AgendaModel> lawAgendasTargetingPlayer = activeGame.getLawsInfo().entrySet().stream()
                .filter(entry -> entry.getValue().equals(player.getFaction()))
                .map(entry -> Mapper.getAgenda(entry.getKey()))
                .toList();
        for (AgendaModel agenda : lawAgendasTargetingPlayer) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.AGENDA, agenda.getAlias()))
                    .findFirst();

            if (relevantMod.isPresent()
                    && checkModPassesCondition(relevantMod.get(), tile, player, opponent, unitsByQuantity,
                            activeGame)) {
                modifiers
                        .add(new NamedCombatModifierModel(relevantMod.get(), Emojis.Agenda + " " + agenda.getName()));
            }
        }

        List<UnitModel> unitsInCombat = new ArrayList<>(unitsByQuantity.keySet());
        for (UnitModel unit : unitsInCombat) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.UNIT, unit.getAlias()))
                    .findFirst();

            if (relevantMod.isPresent()
                    && checkModPassesCondition(relevantMod.get(), tile, player, opponent, unitsByQuantity,
                            activeGame)) {
                modifiers.add(
                        new NamedCombatModifierModel(relevantMod.get(),
                                Emojis.getEmojiFromDiscord(unit.getBaseType()) + " "
                                        + unit.getName() + " " + unit.getAbility()));
            }
        }

        for (Leader leader : activeGame.playerUnlockedLeadersOrAlliance(player)) {
            if (leader.isExhausted() || leader.isLocked()) {
                continue;
            }
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.LEADER, leader.getId()))
                    .findFirst();

            if (relevantMod.isPresent()
                    && checkModPassesCondition(relevantMod.get(), tile, player, opponent, unitsByQuantity,
                            activeGame)) {
                modifiers.add(
                        new NamedCombatModifierModel(relevantMod.get(), Helper.getLeaderFullRepresentation(leader)));
            }
        }

        List<CombatModifierModel> customAlwaysRelveantMods = combatModifiers.values().stream()
                .filter(modifier -> modifier.isRelevantTo(Constants.CUSTOM, Constants.CUSTOM))
                .toList();
        for (CombatModifierModel relevantMod : customAlwaysRelveantMods) {
            if (checkModPassesCondition(relevantMod, tile, player, opponent, unitsByQuantity,
                    activeGame)) {
                modifiers.add(
                        new NamedCombatModifierModel(relevantMod, relevantMod.getRelated().get(0).getMessage()));
            }
        }

        return modifiers;
    }

    public static Integer GetCombinedModifierForUnit(UnitModel unit, Integer numOfUnit,
            List<NamedCombatModifierModel> modifiers, Player player,
            Player opponent, Game activeGame, List<UnitModel> playerUnits, List<UnitModel> opponentUnits, CombatRollType rollType) {
        int modsValue = 0;
        for (NamedCombatModifierModel namedModifier : modifiers) {
            CombatModifierModel modifier = namedModifier.getModifier();
            if (modifier.isInScopeForUnit(unit, playerUnits, rollType)) {
                Integer modValue = GetVariableModValue(modifier, player, opponent, activeGame, opponentUnits);
                Integer perUnitCount = 1;
                if (modifier.getApplyEachForQuantity()) {
                    perUnitCount = numOfUnit;
                }
                modsValue += (modValue * perUnitCount);
            }
        }
        return modsValue;
    }

    public static Boolean checkModPassesCondition(CombatModifierModel modifier, TileModel onTile, Player player,
            Player opponent, Map<UnitModel, Integer> unitsByQuantity, Game game) {
        boolean meetsCondition = false;
        String condition = "";
        if (modifier != null && modifier.getCondition() != null) {
            condition = modifier.getCondition();
        }
        switch (condition) {
            case Constants.MOD_OPPONENT_TEKKLAR_PLAYER_OWNER -> {
                if (opponent != null
                        && player.getPromissoryNotesOwned().stream().anyMatch("tekklar"::equals)) {
                    meetsCondition = opponent.getTempCombatModifiers().stream().anyMatch(
                            mod -> "tekklar".equals(mod.getRelatedID())
                                    && mod.getRelatedType().equals(Constants.PROMISSORY_NOTES))
                            ||
                            opponent.getNewTempCombatModifiers().stream().anyMatch(
                                    mod -> "tekklar".equals(mod.getRelatedID())
                                            && mod.getRelatedType().equals(Constants.PROMISSORY_NOTES));
                }
            }
            case Constants.MOD_OPPONENT_FRAG -> {
                if (opponent != null) {
                    meetsCondition = opponent.getFragments().size() > 0;
                }
            }
            case Constants.MOD_OPPONENT_STOLEN_TECH -> {
                if (opponent != null) {
                    String opponentFaction = opponent.getFaction();
                    meetsCondition = player.getTechs().stream()
                            .map(Mapper::getTech)
                            .anyMatch(tech -> tech.getFaction().orElse("").equals(opponentFaction));
                }
            }
            case Constants.MOD_PLANET_MR_LEGEND_HOME -> {
                if (onTile.getId().equals(player.getFactionSetupInfo().getHomeSystem())) {
                    meetsCondition = true;
                }
                if (onTile.getPlanets().stream().anyMatch(
                        planetId -> StringUtils.isNotBlank(Mapper.getPlanet(planetId).getLegendaryAbilityName()))) {
                    meetsCondition = true;
                }
                if (onTile.getPlanets().contains(Constants.MR)) {
                    meetsCondition = true;
                }
                if(game.getTile(onTile.getId()) != null){
                    if(ButtonHelper.isTileLegendary(game.getTile(onTile.getId()), game)){
                        meetsCondition = true;
                    }
                }
                
            }
            case Constants.MOD_HAS_FRAGILE -> meetsCondition = player.getAbilities().contains("fragile");
            case Constants.MOD_OPPONENT_NO_CC_FLEET ->
                meetsCondition = !player.getMahactCC().contains(opponent.getColor());
            case Constants.MOD_UNITS_TWO_MATCHING_NOT_FF -> {
                if (unitsByQuantity.entrySet().size() == 1) {
                    Entry<UnitModel, Integer> unitByQuantity = new ArrayList<>(unitsByQuantity.entrySet()).get(0);
                    meetsCondition = unitByQuantity.getValue() == 2
                            && !"ff".equals(unitByQuantity.getKey().getAsyncId());
                }
            }
            case Constants.MOD_NEBULA_DEFENDER -> {
                if (onTile.isNebula() && !game.getActivePlayer().equals(player.getUserID())) {
                    meetsCondition = true;
                }
            }
            case "vaylerianhero" -> {
                if (player == game.getActivePlayerObject() && !game.getFactionsThatReactedToThis("vaylerianHeroActive").isEmpty()) {
                    meetsCondition = true;
                }
            }
            default -> meetsCondition = true;
        }
        return meetsCondition;
    }

    ///
    /// The amount of the mod is usually static (eg +2 fighters)
    /// But for some (mostly flagships), the value is scaled depending on game state
    /// like how many fragments you have
    /// or how many POs the opponent has scored that you havent etc.
    ///
    public static Integer GetVariableModValue(CombatModifierModel mod, Player player, Player opponent,
            Game activeGame, List<UnitModel> opponentUnitsInCombat) {
        double value = mod.getValue().doubleValue();
        double multiplier = 1.0;
        Long scalingCount = (long) 0;
        if (mod.getValueScalingMultiplier() != null) {
            multiplier = mod.getValueScalingMultiplier();
        }
        if (StringUtils.isNotBlank(mod.getValueScalingType())) {
            switch (mod.getValueScalingType()) {
                case Constants.FRAGMENT -> {
                    if (player.hasFoundCulFrag()) {
                        scalingCount += 1;
                    }
                    if (player.hasFoundHazFrag()) {
                        scalingCount += 1;
                    }
                    if (player.hasFoundIndFrag()) {
                        scalingCount += 1;
                    }
                    if (player.hasFoundUnkFrag()) {
                        scalingCount += 1;
                    }
                }
                case Constants.LAW -> scalingCount = (long) activeGame.getLaws().size();
                case Constants.MOD_OPPONENT_PO_EXCLUSIVE_SCORED -> {
                    if (opponent != null) {
                        var customPublicVPList = activeGame.getCustomPublicVP();
                        List<List<String>> scoredPOUserLists = new ArrayList<>();
                        for (Entry<String, List<String>> entry : activeGame.getScoredPublicObjectives().entrySet()) {
                            // Ensure its actually a revealed PO not imperial or a relic
                            if (!customPublicVPList.containsKey(entry.getKey())) {
                                scoredPOUserLists.add(entry.getValue());
                            }
                        }
                        scalingCount = scoredPOUserLists.stream()
                                .filter(scoredPlayerList -> scoredPlayerList.contains(opponent.getUserID())
                                        && !scoredPlayerList.contains(player.getUserID()))
                                .count();
                    }
                }
                case Constants.UNIT_TECH -> scalingCount = player.getTechs().stream()
                        .map(Mapper::getTech)
                        .filter(tech -> tech.getType() == TechnologyModel.TechnologyType.UNITUPGRADE)
                        .count();
                case Constants.MOD_DESTROYERS -> {
                    // TODO: Doesnt seem like an easier way to do this? Seems slow.
                    String colorID = Mapper.getColorID(player.getColor());
                    for (Tile tile : activeGame.getTileMap().values()) {
                        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                            Map<String, Integer> unitsOnHolder = unitHolder.getUnitAsyncIdsOnHolder(colorID);
                            for (Entry<String, Integer> unitEntry : unitsOnHolder.entrySet()) {
                                if ("dd".equals(unitEntry.getKey())) {
                                    scalingCount += unitEntry.getValue();
                                }
                            }
                        }
                    }
                }
                case Constants.MOD_OPPONENT_NON_FIGHTER_SHIP -> {
                    scalingCount += opponentUnitsInCombat.stream()
                            .filter(unit -> unit.getBaseType() != UnitType.Fighter.value)
                            .count();
                }
                case Constants.MOD_OPPONENT_UNIT_TECH -> {
                    if (opponent != null) {
                        scalingCount = opponent.getTechs().stream()
                                .map(Mapper::getTech)
                                .filter(tech -> tech.getType() == TechnologyModel.TechnologyType.UNITUPGRADE)
                                .count();
                    }
                }
                case Constants.MOD_OPPONENT_FACTION_TECH -> {
                    if (opponent != null) {
                        scalingCount = opponent.getTechs().stream()
                                .map(Mapper::getTech)
                                .filter(tech -> StringUtils.isNotBlank(tech.getFaction().orElse("")))
                                .count();
                    }
                }
                default -> {
                }
            }
            value = value * multiplier * scalingCount.doubleValue();
        }
        value = Math.floor(value); // to make sure eg +1 per 2 destroyer doesnt return 2.5 etc
        return (int) value;
    }

    // public static List<NamedCombatModifierModel> FilterRelevantMods(List<NamedCombatModifierModel> mods,
    //         List<UnitModel> units, CombatRollType rollType) {
    //     return mods.stream()
    //             .filter(model -> IsModInScopeForUnits(units, model.getModifier(), rollType))
    //             .toList();
    // }
}