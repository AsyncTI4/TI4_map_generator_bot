package ti4.commands.special;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.collections4.KeyValue;
import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import software.amazon.awssdk.services.s3.endpoints.internal.Value.Bool;
import ti4.commands.player.AbilityInfo;
import ti4.commands.tech.TechInfo;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.CombatModifierModel;
import ti4.model.TechnologyModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;

public class CombatRoll extends SpecialSubcommandData {

    @Data
    private class NamedCombatModifier {
        private CombatModifierModel modifier;
        private String name;

        public NamedCombatModifier(CombatModifierModel modifier, String name) {
            this.modifier = modifier;
            this.name = name;
        }
    }

    public CombatRoll() {
        super(Constants.COMBAT_ROLL,
                "*BETA* Combat rolls for units on tile. Doesnt consider abilities/upgrades etc, only units on tile");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COMBAT_MODIFIERS,
                "+/- <unit type>. Eg -1 all, +2 mech, +1 fighter").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET,
                "(optional) Planet to have combat on. By default rolls for space combat.").setAutoComplete(true)
                .setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.COMBAT_EXTRA_ROLLS,
                "comma list of <count> <unit> eg 2 fighter 1 dreadnought for extra roll")
                .setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "roll for player (by default your units)")
                .setAutoComplete(true).setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        OptionMapping mods = event.getOption(Constants.COMBAT_MODIFIERS);
        OptionMapping planetOption = event.getOption(Constants.PLANET);
        OptionMapping extraRollsOption = event.getOption(Constants.COMBAT_EXTRA_ROLLS);

        if (tileOption == null) {
            return;
        }

        String unitHolderName = Constants.SPACE;
        if (planetOption != null) {
            unitHolderName = planetOption.getAsString();
        }

        // Get tile info
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, activeMap);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Tile " + tileOption.getAsString() + " not found");
            return;
        }
        TileModel tileModel = TileHelper.getAllTiles().get(tile.getTileID());

        String tileName = tile.getTilePath();
        tileName = tileName.substring(tileName.indexOf("_") + 1);
        tileName = tileName.substring(0, tileName.indexOf(".png"));
        tileName = " - " + tileName + "[" + tile.getTileID() + "]";
        StringBuilder sb = new StringBuilder();

        String userID = getUser().getId();
        Player player = activeMap.getPlayer(userID);
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        UnitHolder combatOnHolder = tile.getUnitHolders().get(unitHolderName);
        HashMap<UnitModel, Integer> unitsByQuantity = getUnitsFromUnitHolder(player, combatOnHolder);

        // TODO: This could be done better
        Player opponent = null;
        for (Player otherPlayer : activeMap.getPlayers().values()) {
            if (player.getUserID() != otherPlayer.getUserID()) {
                HashMap<UnitModel, Integer> otherPlayerUnits = getUnitsFromUnitHolder(otherPlayer, combatOnHolder);
                if (otherPlayerUnits.size() > 0) {
                    opponent = otherPlayer;
                    break;
                }
            }
        }
        // if (opponent == null) {
        // MessageHelper.sendMessageToChannel(event.getChannel(),
        // String.format("There's noone to do combat with in %s",
        // combatOnHolder.getName()));
        // return;
        // }

        List<NamedCombatModifier> modsParsed = new ArrayList<>();
        if (mods != null) {
            modsParsed = parseUnitMods(mods.getAsString());
        }
        // TODO: add faction ability specific mods
        // TODO: add tech specific mods that player owns
        // TODO: add PN specific mods that player owns
        // TOOD: add leader specific mods that player has unlocked
        HashMap<String, Integer> extraRollsParsed = new HashMap<String, Integer>();
        if (extraRollsOption != null) {
            extraRollsParsed = parseUnits(extraRollsOption.getAsString());
        }

        List<UnitModel> unitsInCombat = new ArrayList<>(unitsByQuantity.keySet());
        modsParsed.addAll(getAlwaysOnMods(player, opponent, unitsInCombat, tileModel));

        String message = String.format("%s combat rolls for %s on %s: \n",
                StringUtils.capitalize(combatOnHolder.getName()), Helper.getFactionIconFromDiscord(player.getFaction()),
                tile.getPosition());
        message += rollUnits(unitsByQuantity, extraRollsParsed, modsParsed, player, opponent, activeMap);

        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        message = StringUtils.removeEnd(message, ";\n");
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }

    private List<NamedCombatModifier> getAlwaysOnMods(Player player, Player opponent, List<UnitModel> unitsInCombat,
            TileModel tile) {
        List<NamedCombatModifier> alwaysOnMods = new ArrayList<>();
        HashMap<String, CombatModifierModel> combatModifiers = Mapper.getCombatModifiers();

        for (var ability : player.getAbilities()) {
            Optional<CombatModifierModel> filteredModifierForAbility = combatModifiers.values()
                    .stream()
                    .filter(modifier -> modifier.isRelevantTo("faction_abilities", ability))
                    .findFirst();
            if (filteredModifierForAbility.isPresent()) {
                CombatModifierModel modifierForAbility = filteredModifierForAbility.get();
                if (modifierForAbility.isValid()) {
                    alwaysOnMods.add(
                            new NamedCombatModifier(modifierForAbility, AbilityInfo.getAbilityRepresentation(ability)));
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
                alwaysOnMods.add(new NamedCombatModifier(modifierForTech, Helper.getTechRepresentationLong(tech)));
            }
        }

        for (var relic : player.getRelics()) {
            Optional<CombatModifierModel> filteredModifierForTech = combatModifiers.values()
                    .stream()
                    .filter(modifier -> modifier.isRelevantTo("relics", relic))
                    .findFirst();
            if (filteredModifierForTech.isPresent()) {
                CombatModifierModel modifierForTech = filteredModifierForTech.get();
                alwaysOnMods.add(new NamedCombatModifier(modifierForTech, Helper.getRelicRepresentation(relic)));
            }
        }

        Map activeMap = getActiveMap();
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
                alwaysOnMods.add(new NamedCombatModifier(modifierForTech, Emojis.Agenda + " " + agenda.getName()));
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
                        meetsCondition = getModificationMatchCondition(modifier, tile, player, opponent, activeMap);
                    }
                    if (meetsCondition) {
                        alwaysOnMods.add(
                                new NamedCombatModifier(modifier, Helper.getEmojiFromDiscord(unit.getBaseType()) + " "
                                        + unit.getName() + " " + unit.getAbility()));
                    }

                }
            }
        }

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
                        meetsCondition = getModificationMatchCondition(modifier, tile, player, opponent, activeMap);
                    }
                    if (meetsCondition) {
                        String leaderRep = Helper.getLeaderFullRepresentation(leader);
                        alwaysOnMods.add(new NamedCombatModifier(modifier, leaderRep));
                    }

                }
            }
        }
        return alwaysOnMods;
    }

    private List<NamedCombatModifier> getConditionalMods(Player player, List<UnitModel> unitsInCombat) {
        List<NamedCombatModifier> conditionalMods = new ArrayList<>();
        HashMap<String, CombatModifierModel> combatModifiers = Mapper.getCombatModifiers();

        for (var unit : unitsInCombat) {
            Optional<CombatModifierModel> filteredModifierForUnit = combatModifiers.values()
                    .stream()
                    .filter(modifier -> modifier.isRelevantTo("units", unit.getAlias()))
                    .findFirst();
            if (filteredModifierForUnit.isPresent()) {
                CombatModifierModel modifier = filteredModifierForUnit.get();
                if (modifier.isValid()) {
                    conditionalMods.add(
                            new NamedCombatModifier(modifier, unit.getName() + " " + unit.getAbility()));
                }
            }
        }
        return conditionalMods;
    }

    private UnitModel getUnitModel(String unitHolderString, Player player) {
        UnitModel result = null;
        String colorID = Mapper.getColorID(player.getColor());
        String unitHolderFileSuffix = ".png";
        if (unitHolderString.startsWith(colorID)) {
            // format is <color>_<asyncID>.png
            String unitAsyncID = unitHolderString.substring(colorID.length());
            unitAsyncID = unitAsyncID.substring(1, unitAsyncID.length() - unitHolderFileSuffix.length());
            result = player.getUnitByAsyncID(unitAsyncID.toLowerCase());
        }
        return result;
    }

    private java.util.HashMap<UnitModel, Integer> getUnitsFromUnitHolder(Player player, UnitHolder unitHolder) {
        java.util.HashMap<UnitModel, Integer> unitModels = new HashMap<>();

        HashMap<String, Integer> units = unitHolder.getUnits();
        for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {

            String unitType = unitEntry.getKey();
            Integer quantity = unitEntry.getValue();

            UnitModel unitModel = getUnitModel(unitType, player);
            if (unitModel != null) {
                // TODO: Check for racial exceptions like:
                // - Nekro & NRA mechs that could be in space
                // - (For ground combat) titan pds
                if (unitHolder.getName() == Constants.SPACE && unitModel.getIsShip() != null && unitModel.getIsShip()) {
                    unitModels.put(unitModel, quantity);
                } else if (unitHolder.getName() != Constants.SPACE
                        && unitModel.getIsGroundForce() != null
                        && unitModel.getIsGroundForce()) {
                    unitModels.put(unitModel, quantity);
                }
            }
        }
        return unitModels;
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        super.reply(event);
    }

    private static Boolean inScopeForUnits(List<UnitModel> units, CombatModifierModel modifier) {
        for (UnitModel unitModel : units) {
            if (getIsInScope(unitModel, modifier)) {
                return true;
            }
        }
        return false;
    }

    private String rollUnits(java.util.Map<UnitModel, Integer> units,
            HashMap<String, Integer> extraRolls, List<NamedCombatModifier> mods, Player player, Player opponent,
            Map map) {
        String result = "";

        // Display modifiers info
        List<NamedCombatModifier> alwaysOnModifiers = mods.stream()
                .filter(model -> model.modifier.getPersistanceType() != null
                        && !model.modifier.getPersistanceType().equals("CUSTOM"))
                .filter(model -> inScopeForUnits(new ArrayList<>(units.keySet()), model.modifier))
                .toList();
        List<NamedCombatModifier> customModifiers = mods.stream()
                .filter(model -> model.modifier.getPersistanceType() != null
                        && model.modifier.getPersistanceType().equals("CUSTOM"))
                .filter(model -> inScopeForUnits(new ArrayList<>(units.keySet()), model.modifier))
                .toList();
        result += getModifiersText("With automatic modifiers: \n", units, alwaysOnModifiers);
        result += getModifiersText("With custom modifiers: \n", units, customModifiers);

        // Display extra rolls info
        List<UnitModel> unitsWithExtraRolls = units.keySet().stream()
                .filter(unit -> extraRolls.containsKey(unit.getAsyncId()))
                .collect(Collectors.toList());
        if (!extraRolls.isEmpty()) {
            result += "With ";
            ArrayList<String> extraRollMessages = new ArrayList<String>();
            for (UnitModel unit : unitsWithExtraRolls) {
                String plusPrefix = "+";
                Integer numExtraRolls = extraRolls.get(unit.getAsyncId());
                String unitAsnycEmoji = Helper.getEmojiFromDiscord(unit.getBaseType());
                if (numExtraRolls < 0) {
                    plusPrefix = "";
                }
                extraRollMessages.add(String.format("%s%s rolls for %s", plusPrefix, numExtraRolls, unitAsnycEmoji));
            }
            result += String.join(", ", extraRollMessages) + "\n";
        }

        // Actually roll for each unit
        int totalHits = 0;
        for (java.util.Map.Entry<UnitModel, Integer> entry : units.entrySet()) {
            UnitModel unit = entry.getKey();
            int numOfUnit = entry.getValue();

            int toHit = unit.getCombatHitsOn();
            int modifierToHit = getTotalModifications(unit, mods, player, opponent, map);
            int extraRollsForUnit = 0;
            if (extraRolls.containsKey(unit.getAsyncId())) {
                extraRollsForUnit = extraRolls.get(unit.getAsyncId());
            }
            int numRollsPerUnit = unit.getCombatDieCount();
            int numRolls = (numOfUnit * numRollsPerUnit) + extraRollsForUnit;
            int[] resultRolls = new int[numRolls];
            for (int index = 0; index < numRolls; index++) {
                resultRolls[index] = randomRange(1, 10);
            }

            int[] hitRolls = Arrays.stream(resultRolls)
                    .filter(roll -> {
                        return roll >= toHit - modifierToHit;
                    })
                    .toArray();

            String rollsSuffix = "";
            totalHits += hitRolls.length;
            if (hitRolls.length > 1) {
                rollsSuffix = "s";
            }

            String unitTypeHitsInfo = String.format("hits on %s", toHit);
            if (unit.getCombatDieCount() > 1) {
                unitTypeHitsInfo = String.format("%s rolls, hits on %s", unit.getCombatDieCount(), toHit);
            }
            if (modifierToHit != 0) {
                String modifierToHitString = Integer.toString(modifierToHit);
                if (modifierToHit > 0) {
                    modifierToHitString = "+" + modifierToHitString;
                }

                if ((toHit - modifierToHit) <= 1) {
                    unitTypeHitsInfo = String.format("always hits (%s mods)",
                            modifierToHitString);
                    if (unit.getCombatDieCount() > 1) {
                        unitTypeHitsInfo = String.format("%s rolls, always hits (%s mods)", unit.getCombatDieCount(),
                                modifierToHitString);
                    }
                } else {
                    unitTypeHitsInfo = String.format("hits on %s (%s mods)", (toHit - modifierToHit),
                            modifierToHitString);
                    if (unit.getCombatDieCount() > 1) {
                        unitTypeHitsInfo = String.format("%s rolls, hits on %s (%s mods)", unit.getCombatDieCount(),
                                (toHit - modifierToHit), modifierToHitString);
                    }
                }

            }
            String upgradedUnitName = "";
            if (!StringUtils.isBlank(unit.getRequiredTechId())) {
                upgradedUnitName = String.format(" %s", unit.getName());
            }
            String unitEmoji = Helper.getEmojiFromDiscord(unit.getBaseType());
            result += String.format("%s %s%s %s %s - %s hit%s\n", numOfUnit, unitEmoji, upgradedUnitName,
                    unitTypeHitsInfo,
                    Arrays.toString(resultRolls), hitRolls.length, rollsSuffix);
        }

        result += String.format("\n**Total hits %s**\n", totalHits);
        return result.toString();
    }

    private String getModifiersText(String prefixText, java.util.Map<UnitModel, Integer> units,
            List<NamedCombatModifier> modifiers) {
        String result = "";
        if (!modifiers.isEmpty()) {

            result += prefixText;
            ArrayList<String> modifierMessages = new ArrayList<String>();
            for (NamedCombatModifier namedModifier : modifiers) {
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

    private static Boolean getIsInScope(UnitModel unit, CombatModifierModel modifier) {
        Boolean isInScope = false;
        if (modifier.getScopeExcept() != null) {
            if (!modifier.getScopeExcept().equals(unit.getAsyncId())) {
                isInScope = true;
            }
        } else {
            if (StringUtils.isBlank(modifier.getScope())
                    || modifier.getScope().equals("all")
                    || modifier.getScope().equals(unit.getAsyncId())) {
                isInScope = true;
            }
        }
        return isInScope;
    }

    private Integer getTotalModifications(UnitModel unit, List<NamedCombatModifier> modifiers, Player player,
            Player opponent, Map map) {
        Integer modValue = 0;
        for (NamedCombatModifier namedModifier : modifiers) {
            CombatModifierModel modifier = namedModifier.getModifier();
            Boolean isInScope = getIsInScope(unit, modifier);

            if (isInScope) {
                modValue += getValueScaledModification(modifier, player, opponent, map);
            }
        }
        return modValue;
    }

    public Boolean getModificationMatchCondition(CombatModifierModel modifier, TileModel onTile, Player player,
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
            default:
                break;
        }
        return meetsCondition;
    }

    public Integer getValueScaledModification(CombatModifierModel mod, Player player, Player opponent, Map map) {
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
                    scalingCount = player.getTechs().stream()
                            .map(techId -> Mapper.getTech(techId))
                            .filter(tech -> tech.getType().equals(Constants.UNIT_UPGRADE))
                            .count();
                    break;
                case "destroyers":
                    // TODO: Doesnt seem like an easier way to do this? Seems slow.
                    for (Tile tile : map.getTileMap().values()) {
                        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                            for (Entry<String, Integer> unitEntry : unitHolder.getUnits().entrySet()) {
                                String key = unitEntry.getKey();
                                Integer count = unitEntry.getValue();
                                if (count == null) {
                                    count = 0;
                                }
                                String colorID = Mapper.getColorID(player.getColor());

                                if (key.equals(colorID + "_dd.png")) {
                                    scalingCount += unitEntry.getValue();
                                }
                            }
                        }
                    }
                    break;
                case "opponent_unit_tech":
                    if (opponent != null) {
                        scalingCount = opponent.getTechs().stream()
                                .map(techId -> Mapper.getTech(techId))
                                .filter(tech -> tech.getType().equals(Constants.UNIT_UPGRADE))
                                .count();
                    }
                    break;
                case "opponent_faction_tech":
                    if (opponent != null) {
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
        value = Math.max(value, 0);
        value = Math.floor(value); // to make sure eg +1 per 2 destroyer doesnt return 2.5 etc
        return value.intValue();
    }

    private List<NamedCombatModifier> parseUnitMods(String unitList) {
        List<NamedCombatModifier> resultList = new ArrayList<>();
        unitList = unitList.replace(", ", ",");
        StringTokenizer unitListTokenizer = new StringTokenizer(unitList, ",");
        while (unitListTokenizer.hasMoreTokens()) {
            String unitListToken = unitListTokenizer.nextToken();
            StringTokenizer unitInfoTokenizer = new StringTokenizer(unitListToken, " ");
            int count = 1;
            boolean numberIsSet = false;

            String unit = "";
            if (unitInfoTokenizer.hasMoreTokens()) {
                String ifNumber = unitInfoTokenizer.nextToken();
                try {
                    count = Integer.parseInt(ifNumber);
                    numberIsSet = true;
                } catch (Exception e) {
                    unit = AliasHandler.resolveUnit(ifNumber);
                }
            }
            if (unitInfoTokenizer.hasMoreTokens() && numberIsSet) {
                unit = AliasHandler.resolveUnit(unitInfoTokenizer.nextToken());
            }

            if (unit != null) {
                CombatModifierModel combatModifier = new CombatModifierModel();
                combatModifier.setValue(count);
                combatModifier.setScope(unit);
                combatModifier.setPersistanceType("CUSTOM");
                resultList.add(new NamedCombatModifier(combatModifier, ""));
            }
        }
        return resultList;
    }

    private HashMap<String, Integer> parseUnits(String unitList) {
        HashMap<String, Integer> resultList = new HashMap<String, Integer>();
        unitList = unitList.replace(", ", ",");
        StringTokenizer unitListTokenizer = new StringTokenizer(unitList, ",");
        while (unitListTokenizer.hasMoreTokens()) {
            String unitListToken = unitListTokenizer.nextToken();
            StringTokenizer unitInfoTokenizer = new StringTokenizer(unitListToken, " ");
            int count = 1;
            boolean numberIsSet = false;

            String unit = "";
            if (unitInfoTokenizer.hasMoreTokens()) {
                String ifNumber = unitInfoTokenizer.nextToken();
                try {
                    count = Integer.parseInt(ifNumber);
                    numberIsSet = true;
                } catch (Exception e) {
                    unit = AliasHandler.resolveUnit(ifNumber);
                }
            }
            if (unitInfoTokenizer.hasMoreTokens() && numberIsSet) {
                unit = AliasHandler.resolveUnit(unitInfoTokenizer.nextToken());
            }

            if (unit != null) {
                resultList.put(unit, count);
            }
        }
        return resultList;
    }

    private static int randomRange(int min, int max) {
        Random random = new Random();
        return random.nextInt(max - min + 1) + min;
    }
}