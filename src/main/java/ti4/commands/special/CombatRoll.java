package ti4.commands.special;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.CombatHelper;
import ti4.helpers.CombatModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;

public class CombatRoll extends SpecialSubcommandData {

    public CombatRoll() {
        super(Constants.COMBAT_ROLL,
                "*BETA* Combat rolls for units on tile. *Auto includes always on mods* (from units, abilities, etc");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COMBAT_MODIFIERS,
                "+/- <unit type>. Eg -1 all, +2 mech. Temp ACs/PN/exhaust-tech mods should be added here")
                .setRequired(false));
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
        HashMap<UnitModel, Integer> unitsByQuantity = CombatHelper.GetUnitsInCombat(combatOnHolder, player);

        Player opponent = CombatHelper.GetOpponent(player, combatOnHolder, activeMap);

        List<NamedCombatModifierModel> customMods = new ArrayList<>();
        if (mods != null) {
            customMods = parseCustomUnitMods(mods.getAsString());
        }

        HashMap<String, Integer> extraRollsParsed = new HashMap<String, Integer>();
        if (extraRollsOption != null) {
            extraRollsParsed = parseUnits(extraRollsOption.getAsString());
        }

    
        List<UnitModel> unitsInCombat = new ArrayList<>(unitsByQuantity.keySet());
        List<NamedCombatModifierModel> autoMods = CombatModHelper.CalculateAutomaticMods(player, opponent, unitsInCombat, tileModel, activeMap);

        customMods = CombatModHelper.FilterRelevantMods(customMods, unitsInCombat);
        autoMods = CombatModHelper.FilterRelevantMods(autoMods, unitsInCombat);

        String message = String.format("%s combat rolls for %s on %s: \n",
                StringUtils.capitalize(combatOnHolder.getName()), Helper.getFactionIconFromDiscord(player.getFaction()),
                tile.getPosition());
        message += CombatHelper.RollForUnits(unitsByQuantity, extraRollsParsed, customMods, autoMods, player, opponent, activeMap);

        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        message = StringUtils.removeEnd(message, ";\n");
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        super.reply(event);
    }

    private List<NamedCombatModifierModel> parseCustomUnitMods(String unitList) {
        List<NamedCombatModifierModel> resultList = new ArrayList<>();
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
                resultList.add(new NamedCombatModifierModel(combatModifier, ""));
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
}