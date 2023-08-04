package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;
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

public class CombatHelper {

    public static HashMap<UnitModel, Integer> GetUnitsInCombat(UnitHolder unitHolder, Player player) {
        String colorID = Mapper.getColorID(player.getColor());
        HashMap<UnitModel, Integer> unitsInCombat = new HashMap<>(unitHolder.getUnitAsyncIdsOnHolder(colorID)
                .entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> player.getUnitByAsyncID(entry.getKey().toLowerCase()),
                        Entry::getValue)));

        if (unitHolder.getName() == Constants.SPACE) {
            return new HashMap<UnitModel, Integer>(unitsInCombat.entrySet().stream()
                    .filter(entry -> entry.getKey().getIsShip() != null && entry.getKey().getIsShip())
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        } else {
            return new HashMap<UnitModel, Integer>(unitsInCombat.entrySet().stream()
                    .filter(entry -> entry.getKey().getIsGroundForce() != null && entry.getKey().getIsGroundForce())
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        }
    }

    public static Player GetOpponent(Player player, UnitHolder unitHolder, Map activeMap) {
        Player opponent = null;
        String playerColorID = Mapper.getColorID(player.getColor());
        Optional<String> opponentColor = unitHolder.getUnitColorsOnHolder().stream()
                .filter(color -> !color.equals(playerColorID))
                .findFirst();
        if (opponentColor.isPresent()) {
            opponent = Helper.getPlayerFromColorOrFaction(activeMap, opponentColor.get());
        }
        return opponent;
    }

    // #region Combat modifiers
    public static List<NamedCombatModifierModel> getAlwaysOnMods(Player player, Player opponent,
            List<UnitModel> unitsInCombat,
            TileModel tile,
            Map activeMap) {
        List<NamedCombatModifierModel> alwaysOnMods = new ArrayList<>();
        HashMap<String, CombatModifierModel> combatModifiers = Mapper.getCombatModifiers();

        // TODO: See if we can shrink this to something like
        // getRelevantModifiers("ability", (abilities) -> ability.getID(), (abilities)
        // -> AbilityInfo.getAbilityRepresentation(ability));
        // Cause filtering by type, getting the id, and getting the display string is
        // the only difference here.
        for (var ability : player.getAbilities()) {
            Optional<CombatModifierModel> filteredModifierForAbility = combatModifiers.values()
                    .stream()
                    .filter(modifier -> modifier.isRelevantTo("faction_abilities", ability))
                    .findFirst();
            if (filteredModifierForAbility.isPresent()) {
                CombatModifierModel modifierForAbility = filteredModifierForAbility.get();
                if (modifierForAbility.isValid()) {
                    alwaysOnMods.add(
                            new NamedCombatModifierModel(modifierForAbility,
                                    AbilityInfo.getAbilityRepresentation(ability)));
                }
            }
        }

        for (var tech : player.getTechs()) {
            Optional<CombatModifierModel> filteredModifierForTech = combatModifiers.values()
                    .stream()
                    .filter(modifier -> modifier.isRelevantTo("technology", tech))
                    .findFirst();
            if (filteredModifierForTech.isPresent()) {
                CombatModifierModel modifierForTech = filteredModifierForTech.get();
                alwaysOnMods.add(new NamedCombatModifierModel(modifierForTech, Helper.getTechRepresentationLong(tech)));
            }
        }

        for (var relic : player.getRelics()) {
            Optional<CombatModifierModel> filteredModifierForTech = combatModifiers.values()
                    .stream()
                    .filter(modifier -> modifier.isRelevantTo("relics", relic))
                    .findFirst();
            if (filteredModifierForTech.isPresent()) {
                CombatModifierModel modifierForTech = filteredModifierForTech.get();
                alwaysOnMods.add(new NamedCombatModifierModel(modifierForTech, Helper.getRelicRepresentation(relic)));
            }
        }

        var lawAgendaIdsTargetingPlayer = activeMap.getLawsInfo().entrySet().stream()
                .filter(entry -> entry.getValue().equals(player.getFaction())).collect(Collectors.toList());
        for (var lawAgenda : lawAgendaIdsTargetingPlayer) {
            String lawAgendaAlias = lawAgenda.getKey();
            AgendaModel agenda = Mapper.getAgenda(lawAgendaAlias);
            Optional<CombatModifierModel> filteredModifierForTech = combatModifiers.values()
                    .stream()
                    .filter(modifier -> modifier.isRelevantTo("agendas", lawAgendaAlias))
                    .findFirst();
            if (filteredModifierForTech.isPresent()) {
                CombatModifierModel modifierForTech = filteredModifierForTech.get();
                alwaysOnMods.add(new NamedCombatModifierModel(modifierForTech, Emojis.Agenda + " " + agenda.getName()));
            }
        }

        for (var unit : unitsInCombat) {
            Optional<CombatModifierModel> filteredModifierForUnit = combatModifiers.values()
                    .stream()
                    .filter(modifier -> modifier.isRelevantTo("units", unit.getAlias()))
                    .findFirst();
            if (filteredModifierForUnit.isPresent()) {
                CombatModifierModel modifier = filteredModifierForUnit.get();
                if (modifier.isValid()) {
                    Boolean meetsCondition = true;
                    if (StringUtils.isNotEmpty(modifier.getCondition())) {
                        meetsCondition = CombatHelper.getModificationMatchCondition(modifier, tile, player, opponent,
                                activeMap);
                    }
                    if (meetsCondition) {
                        alwaysOnMods.add(
                                new NamedCombatModifierModel(modifier,
                                        Helper.getEmojiFromDiscord(unit.getBaseType()) + " "
                                                + unit.getName() + " " + unit.getAbility()));
                    }

                }
            }
        }

        // TODO: I think theres nothing auto that looks to other players' commanders
        // when alliance is in play, so this would miss those (eg winnu commander shared
        // by alliance.)
        for (Leader leader : player.getLeaders()) {
            if (leader.isExhausted() || leader.isLocked()) {
                continue;
            }
            Optional<CombatModifierModel> filteredModifierForLeader = combatModifiers.values()
                    .stream()
                    .filter(modifier -> modifier.isRelevantTo("leaders", leader.getId()))
                    .findFirst();
            if (filteredModifierForLeader.isPresent()) {
                CombatModifierModel modifier = filteredModifierForLeader.get();
                if (modifier.isValid()) {
                    Boolean meetsCondition = true;
                    if (StringUtils.isNotEmpty(modifier.getCondition())) {
                        meetsCondition = CombatHelper.getModificationMatchCondition(modifier, tile, player, opponent,
                                activeMap);
                    }
                    if (meetsCondition) {
                        String leaderRep = Helper.getLeaderFullRepresentation(leader);
                        alwaysOnMods.add(new NamedCombatModifierModel(modifier, leaderRep));
                    }

                }
            }
        }
        return alwaysOnMods;
    }

    public static Integer getTotalModifications(UnitModel unit, List<NamedCombatModifierModel> modifiers, Player player,
            Player opponent, Map map) {
        Integer modValue = 0;
        for (NamedCombatModifierModel namedModifier : modifiers) {
            CombatModifierModel modifier = namedModifier.getModifier();
            if (modifier.isInScopeForUnit(unit)) {
                modValue += getValueScaledModification(modifier, player, opponent, map);
            }
        }
        return modValue;
    }

    public static Boolean getModificationMatchCondition(CombatModifierModel modifier, TileModel onTile, Player player,
            Player opponent, Map map) {
        Boolean meetsCondition = false;
        switch (modifier.getCondition()) {
            case "opponent_frag": {
                if (opponent != null) {
                    meetsCondition = opponent.getFragments().size() > 0;
                }
            }
                break;
            case "opponent_stolen_faction_tech":
                if (opponent != null) {
                    String opponentFaction = opponent.getFaction();
                    meetsCondition = player.getTechs().stream()
                            .map(techId -> Mapper.getTech(techId))
                            .anyMatch(tech -> tech.getFaction().equals(opponentFaction));
                }
                break;
            case "planet_mr_legendary_home":
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
            case "has_ability_fragile":
                meetsCondition = player.getAbilities().contains("fragile");
            default:
                break;
        }
        return meetsCondition;
    }

    public static Integer getValueScaledModification(CombatModifierModel mod, Player player, Player opponent, Map map) {
        Double value = mod.getValue().doubleValue();
        Double multipler = 1.0;
        Long scalingCount = (long) 0;
        if (mod.getValueScalingMultipler() != null) {
            multipler = mod.getValueScalingMultipler();
        }
        if (StringUtils.isNotBlank(mod.getValueScalingType())) {
            switch (mod.getValueScalingType()) {
                case "fragment":
                    scalingCount = Long.valueOf(player.getFragments().size());
                    break;
                case "law":
                    scalingCount = Long.valueOf(map.getLaws().size());
                    break;
                case "po_opponent_exclusively_scored":
                    if (opponent != null) {
                        var customPublicVPList = map.getCustomPublicVP();
                        List<List<String>> scoredPOUserLists = new ArrayList<>();
                        for (Entry<String, List<String>> entry : map.getScoredPublicObjectives().entrySet()) {
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
                case "unit_tech":
                    // TODO: Could move this to PlayerHelper?
                    scalingCount = player.getTechs().stream()
                            .map(techId -> Mapper.getTech(techId))
                            .filter(tech -> tech.getType().equals(Constants.UNIT_UPGRADE))
                            .count();
                    break;
                case "destroyers":
                    // TODO: Doesnt seem like an easier way to do this? Seems slow.
                    // Maybe add to map?
                    String colorID = Mapper.getColorID(player.getColor());
                    for (Tile tile : map.getTileMap().values()) {
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
                case "opponent_unit_tech":
                    if (opponent != null) {
                        // TODO:If player.getunittech existed, you could reuse it here.
                        scalingCount = opponent.getTechs().stream()
                                .map(techId -> Mapper.getTech(techId))
                                .filter(tech -> tech.getType().equals(Constants.UNIT_UPGRADE))
                                .count();
                    }
                    break;
                case "opponent_faction_tech":
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

    // #endregion
}