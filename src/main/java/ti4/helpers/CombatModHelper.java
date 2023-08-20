package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import ti4.commands.player.AbilityInfo;
import ti4.generator.Mapper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.model.AgendaModel;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;

public class CombatModHelper {
    public static String GetModifiersText(String prefixText, java.util.Map<UnitModel, Integer> units,
            List<NamedCombatModifierModel> modifiers) {
        String result = "";
        if (!modifiers.isEmpty()) {

            result += prefixText;
            ArrayList<String> modifierMessages = new ArrayList<String>();
            for (NamedCombatModifierModel namedModifier : modifiers) {
                CombatModifierModel mod = namedModifier.getModifier();
                String unitScope = mod.getScope(); 
                if (StringUtils.isNotBlank(unitScope)) {
                    Optional<UnitModel> unitScopeModel = units.keySet().stream()
                            .filter(unit -> unit.getAsyncId().equals(mod.getScope())).findFirst();
                    if (unitScopeModel.isPresent()) {
                        unitScope = Helper.getEmojiFromDiscord(unitScopeModel.get().getBaseType());
                    }
                } else {
                    unitScope = "all";
                }

                String plusPrefix = "+";
                Integer modifierValue = mod.getValue();
                if (modifierValue < 0) {
                    plusPrefix = "";
                }
                String modifierName = namedModifier.getName();
                if (StringUtils.isNotBlank(modifierName)) {
                    modifierMessages.add(modifierName);
                } else {
                    modifierMessages.add(String.format("%s%s for %s", plusPrefix, modifierValue, unitScope));
                }

            }
            result += String.join("\n", modifierMessages) + "\n";
        }
        return result;
    }
    public static Boolean IsModInScopeForUnits(List<UnitModel> units, CombatModifierModel modifier) {
        for (UnitModel unit : units) {
            if (modifier.isInScopeForUnit(unit)) {
                return true;
            }
        }
        return false;
    }

    /// Retrieves Always on modifiers, 
    /// based on the player's info (techs, owned relics, active leaders, units), current laws in play, units in combat, and opponent race abilities)
    public static List<NamedCombatModifierModel> CalculateAutomaticMods(Player player, Player opponent,
            List<UnitModel> unitsInCombat,
            TileModel tile,
            Map activeMap) {
        List<NamedCombatModifierModel> alwaysOnMods = new ArrayList<>();
        HashMap<String, CombatModifierModel> combatModifiers = Mapper.getCombatModifiers();

        for (String ability : player.getAbilities()) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.ABILITY, ability))
                    .findFirst();
            
            if (relevantMod.isPresent() 
                && checkModPassesCondition(relevantMod.get(), tile, player, opponent, activeMap)) {
                alwaysOnMods.add(new NamedCombatModifierModel(relevantMod.get(), AbilityInfo.getAbilityRepresentation(ability)));
            }
        }

        for (String tech : player.getTechs()) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.TECH, tech))
                    .findFirst();
            
            if (relevantMod.isPresent() 
                && checkModPassesCondition(relevantMod.get(), tile, player, opponent, activeMap)) {
                alwaysOnMods.add(new NamedCombatModifierModel(relevantMod.get(), Helper.getTechRepresentationLong(tech)));
            }
        }

        for (String relic : player.getRelics()) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.RELIC, relic))
                    .findFirst();
            
            if (relevantMod.isPresent() 
                && checkModPassesCondition(relevantMod.get(), tile, player, opponent, activeMap)) {
                alwaysOnMods.add(new NamedCombatModifierModel(relevantMod.get(), Helper.getRelicRepresentation(relic)));
            }
        }

        List<AgendaModel> lawAgendasTargetingPlayer = activeMap.getLawsInfo().entrySet().stream()
                .filter(entry -> entry.getValue().equals(player.getFaction()))
                .map(entry -> Mapper.getAgenda(entry.getKey()))
                .collect(Collectors.toList());
        for (AgendaModel agenda : lawAgendasTargetingPlayer) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.AGENDA, agenda.getAlias()))
                    .findFirst();
            
            if (relevantMod.isPresent() 
                && checkModPassesCondition(relevantMod.get(), tile, player, opponent, activeMap)) {
                alwaysOnMods.add(new NamedCombatModifierModel(relevantMod.get(), Emojis.Agenda + " " + agenda.getName()));
            }
        }

        for (UnitModel unit : unitsInCombat) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.UNIT, unit.getAlias()))
                    .findFirst();
            
            if (relevantMod.isPresent() 
                && checkModPassesCondition(relevantMod.get(), tile, player, opponent, activeMap)) {
                alwaysOnMods.add(
                                new NamedCombatModifierModel(relevantMod.get(),
                                        Helper.getEmojiFromDiscord(unit.getBaseType()) + " "
                                                + unit.getName() + " " + unit.getAbility()));
            }
        }
            
        for (Leader leader : activeMap.playerUnlockedLeadersOrAlliance(player)) {
            if (leader.isExhausted() || leader.isLocked()) {
                continue;
            }
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.LEADER, leader.getId()))
                    .findFirst();
            
            if (relevantMod.isPresent() 
            && checkModPassesCondition(relevantMod.get(), tile, player, opponent, activeMap)) {
                alwaysOnMods.add(new NamedCombatModifierModel(relevantMod.get(), Helper.getLeaderFullRepresentation(leader)));
            }
        }
        
        return alwaysOnMods;
    }

    public static Integer GetCombinedModifierForUnit(UnitModel unit, List<NamedCombatModifierModel> modifiers, Player player,
            Player opponent, Map activeMap) {
        Integer modValue = 0;
        for (NamedCombatModifierModel namedModifier : modifiers) {
            CombatModifierModel modifier = namedModifier.getModifier();
            if (modifier.isInScopeForUnit(unit)) {
                modValue += GetVariableModValue(modifier, player, opponent, activeMap);
            }
        }
        return modValue;
    }

    public static Boolean checkModPassesCondition(CombatModifierModel modifier, TileModel onTile, Player player,
            Player opponent, Map activeMap) {
        Boolean meetsCondition = false;
        String condition = "";
        if(modifier != null && modifier.getCondition() != null){
            condition = modifier.getCondition();
        }
        switch (condition) {
            case Constants.MOD_OPPONENT_FRAG: 
                if (opponent != null) {
                    meetsCondition = opponent.getFragments().size() > 0;
                }
                break;
            case Constants.MOD_OPPONENT_STOLEN_TECH:
                if (opponent != null) {
                    String opponentFaction = opponent.getFaction();
                    meetsCondition = player.getTechs().stream()
                            .map(techId -> Mapper.getTech(techId))
                            .anyMatch(tech -> tech.getFaction().equals(opponentFaction));
                }
                break;
            case Constants.MOD_PLANET_MR_LEGEND_HOME:
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
                break;
            case Constants.MOD_HAS_FRAGILE:
                meetsCondition = player.getAbilities().contains("fragile");
                break;
            case Constants.MOD_OPPONENT_NO_CC_FLEET:
                meetsCondition = !player.getMahactCC().contains(opponent.getColor());
                break;
            default:
                meetsCondition = true;
                break;
        }
        return meetsCondition;
    }

    ///
    /// The amount of the mod is usually static (eg +2 fighters)
    /// But for some (mostly flagships), the value is scaled depending on game state like how many fragments you have
    /// or how many POs the opponent has scored that you havent etc.
    ///
    public static Integer GetVariableModValue(CombatModifierModel mod, Player player, Player opponent, Map activeMap) {
        Double value = mod.getValue().doubleValue();
        Double multipler = 1.0;
        Long scalingCount = (long) 0;
        if (mod.getValueScalingMultipler() != null) {
            multipler = mod.getValueScalingMultipler();
        }
        if (StringUtils.isNotBlank(mod.getValueScalingType())) {
            switch (mod.getValueScalingType()) {
                case Constants.FRAGMENT:
                    scalingCount = Long.valueOf(player.getFragments().size());
                    break;
                case Constants.LAW:
                    scalingCount = Long.valueOf(activeMap.getLaws().size());
                    break;
                case Constants.MOD_OPPONENT_PO_EXCLUSIVE_SCORED:
                    if (opponent != null) {
                        var customPublicVPList = activeMap.getCustomPublicVP();
                        List<List<String>> scoredPOUserLists = new ArrayList<>();
                        for (Entry<String, List<String>> entry : activeMap.getScoredPublicObjectives().entrySet()) {
                            // Ensure its actually a revealed PO not imperial or a relic
                            if (!customPublicVPList.containsKey(entry.getKey().toString())) {
                                scoredPOUserLists.add(entry.getValue());
                            }
                        }
                        scalingCount = scoredPOUserLists.stream()
                                .filter(scoredPlayerList -> scoredPlayerList.contains(opponent.getUserID())
                                        && !scoredPlayerList.contains(player.getUserID()))
                                .count();
                    }
                    break;
                case Constants.UNIT_TECH:
                    scalingCount = player.getTechs().stream()
                            .map(techId -> Mapper.getTech(techId))
                            .filter(tech -> tech.getType().equals(Constants.UNIT_UPGRADE))
                            .count();
                    break;
                case Constants.MOD_DESTROYERS:
                    // TODO: Doesnt seem like an easier way to do this? Seems slow.
                    String colorID = Mapper.getColorID(player.getColor());
                    for (Tile tile : activeMap.getTileMap().values()) {
                        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {

                            HashMap<String, Integer> unitsOnHolder = unitHolder.getUnitAsyncIdsOnHolder(colorID);
                            for (Entry<String, Integer> unitEntry : unitsOnHolder.entrySet()) {
                                Integer count = unitEntry.getValue();
                                if (count == null) {
                                    count = 0;
                                }
                                if (unitEntry.getKey().equals("dd")) {
                                    scalingCount += unitEntry.getValue();
                                }
                            }
                        }
                    }
                    break;
                case Constants.MOD_OPPONENT_UNIT_TECH:
                    if (opponent != null) {
                        // TODO:If player.getunittech existed, you could reuse it here.
                        scalingCount = opponent.getTechs().stream()
                                .map(techId -> Mapper.getTech(techId))
                                .filter(tech -> tech.getType().equals(Constants.UNIT_UPGRADE))
                                .count();
                    }
                    break;
                case Constants.MOD_OPPONENT_FACTION_TECH:
                    if (opponent != null) {
                        // player.getFactionTEchs?
                        scalingCount = opponent.getTechs().stream()
                                .map(techId -> Mapper.getTech(techId))
                                .filter(tech -> StringUtils.isNotBlank(tech.getFaction()))
                                .count();
                    }
                    break;
                default:
                    break;
            }
            value = value * multipler * scalingCount.doubleValue();
        }
        value = Math.floor(value); // to make sure eg +1 per 2 destroyer doesnt return 2.5 etc
        return value.intValue();
    }
    public static List<NamedCombatModifierModel> FilterRelevantMods(List<NamedCombatModifierModel> mods, List<UnitModel> units){
        
        return mods.stream()
                .filter(model -> CombatModHelper.IsModInScopeForUnits(units, model.getModifier()))
                .toList();
    }
}