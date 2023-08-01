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
import ti4.commands.player.AbilityInfo;
import ti4.commands.tech.TechInfo;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.CombatModifierModel;
import ti4.model.TechnologyModel;
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
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "roll for player (by default your units)").setAutoComplete(true).setRequired(false));
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
        java.util.HashMap<UnitModel, Integer> unitsByQuantity = getUnitsFromUnitHolder(player, combatOnHolder);
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

        modsParsed.addAll(getAlwaysOnMods(player));

        String message = String.format("%s combat rolls for %s on %s: \n",
                StringUtils.capitalize(combatOnHolder.getName()), Helper.getFactionIconFromDiscord(player.getFaction()),
                tile.getPosition());
        message += rollUnits(unitsByQuantity, extraRollsParsed, modsParsed);

        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        message = StringUtils.removeEnd(message, ";\n");
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }

    private List<NamedCombatModifier> getAlwaysOnMods(Player player) {
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
        return alwaysOnMods;
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

    private String rollUnits(java.util.Map<UnitModel, Integer> units,
            HashMap<String, Integer> extraRolls, List<NamedCombatModifier> mods) {
        String result = "";

        // Display modifiers info
        List<NamedCombatModifier> alwaysOnModifiers = mods.stream()
                .filter(model -> model.modifier.getPersistanceType() != null
                        && !model.modifier.getPersistanceType().equals("CUSTOM"))
                .toList();
        List<NamedCombatModifier> customModifiers = mods.stream()
                .filter(model -> model.modifier.getPersistanceType() != null
                        && model.modifier.getPersistanceType().equals("CUSTOM"))
                .toList();
        result += getModifiersText("With always on modifiers; \n", units, alwaysOnModifiers);
        result += getModifiersText("With custom modifiers; \n", units, customModifiers);

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
            int modifierToHit = getTotalModifications(unit, mods);
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
                unitTypeHitsInfo = String.format("hits on %s (%s mods)", (toHit - modifierToHit),
                        modifierToHitString);
                if (unit.getCombatDieCount() > 1) {
                    unitTypeHitsInfo = String.format("%s rolls, hits on %s, (%s mods)", unit.getCombatDieCount(),
                            (toHit - modifierToHit), modifierToHitString);
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

    private Integer getTotalModifications(UnitModel unit, List<NamedCombatModifier> modifiers) {
        Integer modValue = 0;
        for (NamedCombatModifier namedModifier : modifiers) {
            CombatModifierModel modifier = namedModifier.getModifier();
            if (StringUtils.isBlank(modifier.getScope())
                    || modifier.getScope().equals("all")
                    || modifier.getScope().equals(unit.getAsyncId())) {
                modValue += modifier.getValue();
            }
        }
        return modValue;
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