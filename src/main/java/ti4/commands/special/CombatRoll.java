package ti4.commands.special;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
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
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
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
                "*V2* *BETA* Combat rolls for units on tile. *Auto includes always on mods*");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COMBAT_MODIFIERS,
                "+/- <unit type>. Eg -1 all, +2 mech. Temp ACs/PN/exhaust-tech mods")
                .setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET,
                "(optional) Planet to have combat on. Default is space combat.").setAutoComplete(true)
                .setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.COMBAT_EXTRA_ROLLS,
                "comma list of <count> <unit> eg 2 fighter 1 dreadnought")
                .setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "roll for player (default you)")
                .setAutoComplete(true).setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        OptionMapping mods = event.getOption(Constants.COMBAT_MODIFIERS);
        OptionMapping planetOption = event.getOption(Constants.PLANET);
        OptionMapping extraRollsOption = event.getOption(Constants.COMBAT_EXTRA_ROLLS);

        String userID = getUser().getId();
        Player player = activeGame.getPlayer(userID);
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);

         if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        if (tileOption == null) {
            return;
        }
        List<NamedCombatModifierModel> customMods = new ArrayList<>();
        if (mods != null) {
            customMods = parseCustomUnitMods(mods.getAsString());
        }

        HashMap<String, Integer> extraRollsParsed = new HashMap<>();
        if (extraRollsOption != null) {
            extraRollsParsed = parseUnits(extraRollsOption.getAsString());
        }

        String unitHolderName = Constants.SPACE;
        if (planetOption != null) {
            unitHolderName = planetOption.getAsString();
        }

        // Get tile info
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, activeGame);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Tile " + tileOption.getAsString() + " not found");
            return;
        }

        secondHalfOfCombatRoll(player, activeGame, event, tile, unitHolderName, extraRollsParsed, customMods);
    }

    public void secondHalfOfCombatRoll(Player player, Game activeGame, GenericInteractionCreateEvent event, Tile tile, String unitHolderName, HashMap<String, Integer> extraRollsParsed, List<NamedCombatModifierModel> customMods){

        TileModel tileModel = TileHelper.getAllTiles().get(tile.getTileID());
        String tileName = tile.getTilePath();
        tileName = tileName.substring(tileName.indexOf("_") + 1);
        tileName = tileName.substring(0, tileName.indexOf(".png"));
        String sb = "";
        UnitHolder combatOnHolder = tile.getUnitHolders().get(unitHolderName);
        if(combatOnHolder == null){
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Cannot find the planet " + unitHolderName + " on tile " + tile.getPosition());
            return;
        }
        
        HashMap<UnitModel, Integer> unitsByQuantity = CombatHelper.GetUnitsInCombat(combatOnHolder, player, event);
        if (activeGame.getLaws().containsKey("articles_war")) {
            if (unitsByQuantity.keySet().stream().anyMatch(unit -> "naaz_mech_space".equals(unit.getAlias()))) {
                unitsByQuantity = new HashMap<>(unitsByQuantity.entrySet().stream().filter(e -> !"naaz_mech_space".equals(e.getKey().getAlias()))
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Skipping " + Helper.getFactionIconFromDiscord("naaz") + " Z-Grav Eidolon due to Articles of War agenda.");
            }
        }
        if(unitsByQuantity.size() == 0){
            String fightingOnUnitHolderName = unitHolderName;
            if(!unitHolderName.equalsIgnoreCase(Constants.SPACE)){
                fightingOnUnitHolderName = Helper.getPlanetRepresentation(unitHolderName, activeGame);
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There are no units in " + fightingOnUnitHolderName +" on tile " + tile.getPosition() + " for player " + player.getColor() + " " + Helper.getFactionIconFromDiscord(player.getFaction()) + "\n" 
            + "Ping bothelper if this seems to be in error.");

            return;
        }
        Player opponent = CombatHelper.GetOpponent(player, combatOnHolder, activeGame);
        
        List<NamedCombatModifierModel> autoMods = CombatModHelper.CalculateAutomaticMods(player, opponent, unitsByQuantity, tileModel, activeGame);

        List<UnitModel> unitsInCombat = new ArrayList<>(unitsByQuantity.keySet());
        customMods = CombatModHelper.FilterRelevantMods(customMods, unitsInCombat);
        autoMods = CombatModHelper.FilterRelevantMods(autoMods, unitsInCombat);

        String message = String.format("%s combat rolls for %s on %s %s:  \n",
                StringUtils.capitalize(combatOnHolder.getName()), Helper.getFactionIconFromDiscord(player.getFaction()),
                tile.getPosition(), Emojis.RollDice);
        message += CombatHelper.RollForUnits(unitsByQuantity, extraRollsParsed, customMods, autoMods, player, opponent, activeGame);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);
        message = StringUtils.removeEnd(message, ";\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
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

            CombatModifierModel combatModifier = new CombatModifierModel();
            combatModifier.setValue(count);
            combatModifier.setScope(unit);
            combatModifier.setPersistanceType("CUSTOM");
            resultList.add(new NamedCombatModifierModel(combatModifier, ""));
        }
        return resultList;
    }

    private HashMap<String, Integer> parseUnits(String unitList) {
        HashMap<String, Integer> resultList = new HashMap<>();
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

            resultList.put(unit, count);
        }
        return resultList;
    }
}