package ti4.commands.units;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.apache.commons.lang3.StringUtils;
import ti4.ResourceHelper;
import ti4.commands.tokens.AddCC;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class MoveUnits extends AddRemoveUnits {

    private boolean toAction;
    private Map<UnitKey, Integer> unitsDamage = new HashMap<>();
    private boolean priorityDmg = true;

    @Override
    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile, Game game) {
        unitsDamage = new HashMap<>();
        toAction = false;
        OptionMapping optionDmg = event.getOption(Constants.PRIORITY_NO_DAMAGE);
        priorityDmg = true;
        if (optionDmg != null) {
            String value = optionDmg.getAsString().toLowerCase();
            if ("yes".equals(value) || "y".equals(value)) {
                priorityDmg = false;
            }
        }

        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString().toLowerCase();
        unitParsing(event, color, tile, unitList, game);

        String tileID;
        String tileOption = event.getOption(Constants.TILE_NAME_TO, null, OptionMapping::getAsString);
        if (tileOption != null) { //get TILE_TO
            tileOption = StringUtils.substringBefore(event.getOption(Constants.TILE_NAME_TO, null, OptionMapping::getAsString).toLowerCase(), " ");
            tileID = AliasHandler.resolveTile(tileOption);
        } else { //USE TILE_FROM
            tileID = tile.getTileID();
        }
        tile = TileHelper.getTile(event, tileID, game);

        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Tile: " + tileID + " not found. Please try a different name or just use position coordinate");
            return;
        }

        tile = flipMallice(event, tile, game);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not flip Mallice");
            return;
        }

        toAction = true;
        String unitListTo = event.getOption(Constants.UNIT_NAMES_TO, null, OptionMapping::getAsString);
        if (unitListTo != null) { //PROCEED AS NORMAL
            unitList = unitListTo.toLowerCase();
        } else { //USE UNIT_NAMES_FROM LIST
            //CLEAN PLANETS FROM THE UNITLIST
            System.out.println(unitList);
            unitList = Arrays.stream(StringUtils.splitPreserveAllTokens(unitList, ",")).map(String::trim).map(s -> {
                if (Arrays.asList(s.split(" ", -1)).size() > 2) {
                    return StringUtils.substringBeforeLast(s, " ");
                }
                return s;
            })
                .collect(Collectors.joining(", "));
            System.out.println(unitList);
        }

        //IF NO UNIT_LIST_TO AND TILE_NAME_TO THEN STOP, NO CHANGES HAPPEN
        if (unitListTo == null && tileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No change in tile or unit list. Please use either the `tile_name_to` option, the `unit_names_to` option, or both");
            return;
        }

        switch (unitList) {
            case "0", "none" -> {
                //Do nothing, as no unit was moved to
            }
            default -> unitParsing(event, color, tile, unitList, game);
        }

        OptionMapping optionCC = event.getOption(Constants.CC_USE);
        Boolean retreat = null;
        if (optionCC != null) {
            String value = optionCC.getAsString().toLowerCase();
            if ("t".equals(value) || "tactics".equals(value) || "t/tactics".equals(value)) {
                retreat = false;
            }
            if ("r".equals(value) || "retreat".equals(value) || "reinforcements".equals(value) || "r/retreat/reinforcements".equals(value)) {
                retreat = true;
            }
        }
        if (retreat == null) return;

        if (!retreat) {
            removeTacticsCC(event, color, tile, game);
        }

        AddCC.addCC(event, color, tile, false);
        Helper.isCCCountCorrect(event, game, color);
        for (UnitHolder unitHolder_ : tile.getUnitHolders().values()) {
            addPlanetToPlayArea(event, tile, unitHolder_.getName(), game);
        }
    }

    public static Tile flipMallice(SlashCommandInteractionEvent event, Tile tile, Game game) {
        if ("82a".equals(tile.getTileID())) {
            String position = tile.getPosition();
            game.removeTile(position);

            String planetTileName = AliasHandler.resolveTile("82b");
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Position tile not allowed");
                return null;
            }

            String tileName = Mapper.getTileID(planetTileName);
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
                return null;
            }
            tile = new Tile(planetTileName, position);
            game.setTile(tile);
        } else if ("82ah".equals(tile.getTileID())) {
            String position = tile.getPosition();
            game.removeTile(position);

            String planetTileName = AliasHandler.resolveTile("82bh");
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Position tile not allowed");
                return null;
            }

            String tileName = Mapper.getTileID(planetTileName);
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
                return null;
            }
            tile = new Tile(planetTileName, position);
            game.setTile(tile);
        }
        return tile;
    }

    public static Tile flipMallice(ButtonInteractionEvent event, Tile tile, Game game) {
        if ("82a".equals(tile.getTileID())) {
            String position = tile.getPosition();
            game.removeTile(position);
            String planetTileName = AliasHandler.resolveTile("82b");
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Position tile not allowed");
                return null;
            }

            String tileName = Mapper.getTileID(planetTileName);
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
                return null;
            }
            tile = new Tile(planetTileName, position);
            game.setTile(tile);
        } else if ("82ah".equals(tile.getTileID())) {
            String position = tile.getPosition();
            game.removeTile(position);
            String planetTileName = AliasHandler.resolveTile("82bh");
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Position tile not allowed");
                return null;
            }

            String tileName = Mapper.getTileID(planetTileName);
            String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
            if (tilePath == null) {
                MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
                return null;
            }
            tile = new Tile(planetTileName, position);
            game.setTile(tile);
        }
        return tile;
    }

    public static void removeTacticsCC(SlashCommandInteractionEvent event, String color, Tile tile, Game game) {
        for (Player player : game.getPlayers().values()) {
            if (color.equals(player.getColor())) {
                int cc = player.getTacticalCC();
                if (cc == 0) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "You don't have CC in Tactics");
                    break;
                } else if (!AddCC.hasCC(event, color, tile)) {
                    cc -= 1;
                    player.setTacticalCC(cc);
                    break;
                }
            }
        }
    }

    @Override
    protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game game) {
        unitAction((GenericInteractionCreateEvent) event, tile, count, planetName, unitID, color, game);
    }

    @Override
    protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game game) {
        if (toAction) {
            tile.addUnit(planetName, unitID, count);
            tile.addUnitDamage(planetName, unitID, unitsDamage.get(unitID));
        } else {
            int countToRemove = 0;
            UnitHolder unitHolder = tile.getUnitHolders().get(planetName);

            // Check for space unit holder when only single stack of unit is present anywhere on the tile
            // This allows for removes like "2 infantry" when they are the only infantry on a planet
            long nonEmptyUnitHolders = tile.getUnitHolders().values().stream()
                .filter(uh -> uh.getUnits().getOrDefault(unitID, 0) + uh.getUnitDamage().getOrDefault(unitID, 0) > 0)
                .count();

            // These calcluations will let us know if we are in a scenario where we can remove all of a particular unit from
            // the hex
            // This allows for moves like "2 infantry" when there's a hex with 0 in space and 1 infantry on each of 2 planets
            long totalUnitsOnHex = tile.getUnitHolders().values().stream()
                .mapToInt(uh -> uh.getUnits().getOrDefault(unitID, 0) + uh.getUnitDamage().getOrDefault(unitID, 0))
                .sum();

            boolean otherUnitHoldersContainUnit = tile.getUnitHolders().values().stream()
                .filter(planetTemp -> !planetTemp.getName().equals(planetName))
                .anyMatch(uh -> uh.getUnits().getOrDefault(unitID, 0) + uh.getUnitDamage().getOrDefault(unitID, 0) > 0);

            if (nonEmptyUnitHolders == 1) {
                unitHolder = tile.getUnitHolders().values().stream()
                    .filter(uh -> uh.getUnits().getOrDefault(unitID, 0) + uh.getUnitDamage().getOrDefault(unitID, 0) > 0).findFirst().get();

            }

            if (!priorityDmg) {
                Integer unitCountInSystem = unitHolder.getUnits().get(unitID);
                if (unitCountInSystem != null) {
                    Integer unitDamageCountInSystem = null;
                    if (unitHolder.getUnitDamage() != null) {
                        unitDamageCountInSystem = unitHolder.getUnitDamage().get(unitID);
                    }

                    if (unitDamageCountInSystem != null) {
                        countToRemove = unitDamageCountInSystem - (unitCountInSystem - count);
                        unitsDamage.put(unitID, countToRemove);
                    }
                }
            } else {
                countToRemove = count;
                Integer unitDamageCountInSystem = null;
                if (unitHolder != null && unitHolder.getUnitDamage() != null) {
                    unitDamageCountInSystem = unitHolder.getUnitDamage().get(unitID);
                }
                if (unitDamageCountInSystem != null) {
                    unitsDamage.put(unitID, Math.min(unitDamageCountInSystem, countToRemove));
                }

            }

            if (unitHolder != null) {
                tile.removeUnit(unitHolder.getName(), unitID, count);
                tile.removeUnitDamage(unitHolder.getName(), unitID, countToRemove);
            }
            // Check to see if we should remove from other unitHolders
            if ((totalUnitsOnHex == count) && otherUnitHoldersContainUnit) {
                for (String unitHolderName : tile.getUnitHolders().keySet()) {
                    if (!unitHolderName.equals(planetName)) {
                        int tempCount = tile.getUnitHolders().get(unitHolderName).getUnits().getOrDefault(unitID, 0);
                        if (tempCount != 0) {
                            tile.removeUnit(unitHolderName, unitID, tempCount);
                        }
                        tempCount = tile.getUnitHolders().get(unitHolderName).getUnitDamage().getOrDefault(unitID, 0);
                        if (tempCount != 0) {
                            tile.removeUnitDamage(unitHolderName, unitID, tempCount);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected String getActionDescription() {
        return "Move units from one system to another system";
    }

    @Override
    public String getName() {
        return Constants.MOVE_UNITS;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getName(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile to move units from").setRequired(true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri").setRequired(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "System/Tile to move units to")
                    .setAutoComplete(true).setRequired(true))
                .addOptions(
                    new OptionData(OptionType.STRING, Constants.UNIT_NAMES_TO, "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri").setRequired(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setAutoComplete(true))
                .addOptions(
                    new OptionData(OptionType.STRING, Constants.CC_USE, "Type t or tactics to add a CC from tactics, r or retreat to add a CC without taking it from tactics").setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.PRIORITY_NO_DAMAGE, "Priority for not damaged units. Type in yes or y"))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command")));
    }
}
