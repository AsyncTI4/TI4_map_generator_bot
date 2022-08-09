package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.commands.tokens.AddCC;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.HashMap;

public class MoveUnits extends AddRemoveUnits {

    private boolean toAction = false;
    private HashMap<String, Integer> unitsDamage = new HashMap<>();
    private boolean priorityDmg = true;

    @Override
    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile) {
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
        unitParsing(event, color, tile, unitList);

        String userID = event.getUser().getId();
        MapManager mapManager = MapManager.getInstance();

        String tileID = AliasHandler.resolveTile(event.getOption(Constants.TILE_NAME_TO).getAsString().toLowerCase());
        Map activeMap = mapManager.getUserActiveMap(userID);
        tile = getTile(event, tileID, activeMap);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Tile: " + tileID + " not found");
            return;
        }

        tile = flipMallice(event, tile, activeMap);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not flip Mallice");
            return;
        }

        toAction = true;
        unitList = event.getOption(Constants.UNIT_NAMES_TO).getAsString().toLowerCase();
        switch (unitList) {
            case "0":
            case "none":
                //Do nothing, as no unit was moved to
                break;
            default:
                unitParsing(event, color, tile, unitList);
                break;
        }

        OptionMapping optionCC = event.getOption(Constants.CC);
        boolean retreat = false;
        if (optionCC != null) {
            String value = optionCC.getAsString().toLowerCase();
            if ("no".equals(value) || "n".equals(value)) {
                return;
            }
            if ("r".equals(value) || "retreat".equals(value)) {
                retreat = true;
            }
        }
        if (!retreat) {
            removeTacticsCC(event, color, tile, activeMap);
        }

        AddCC.addCC(event, color, tile);
    }

    public static Tile flipMallice(SlashCommandInteractionEvent event, Tile tile, Map activeMap) {
        if ("82a".equals(tile.getTileID())){
            String position = tile.getPosition();
            activeMap.removeTile(position);


            String planetTileName = AliasHandler.resolveTile("82b");
            if (!PositionMapper.isTilePositionValid(position, activeMap)) {
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
            activeMap.setTile(tile);
        }
        return tile;
    }

    public static void removeTacticsCC(SlashCommandInteractionEvent event, String color, Tile tile, Map activeMap) {
        for (Player player : activeMap.getPlayers().values()) {
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
    protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, String unitID, String color) {
        if (toAction) {
            tile.addUnit(planetName, unitID, count);
            tile.addUnitDamage(planetName, unitID, unitsDamage.get(unitID));
        } else {
            
            int countToRemove = 0;
            UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
            
            
            // Check for space unit holder when only single stack of unit is present anywhere on the tile
            // This allows for removes like "2 infantry" when they are the only infantry on a planet
            long nonEmptyUnitHolders = tile.getUnitHolders().values().stream()
                           .filter(unitHolderTemp -> unitHolderTemp.getUnits().getOrDefault(unitID,0) + unitHolderTemp.getUnitDamage().getOrDefault(unitID,0) > 0)
                           .count();
            
            // These calcluations will let us know if we are in a scenario where we can remove all of a particular unit from
            // the hex
            // This allows for moves like "2 infantry" when there's a hex with 0 in space and 1 infantry on each of 2 planets
            long totalUnitsOnHex = tile.getUnitHolders().values().stream()
                           .mapToInt(unitHolderTemp -> unitHolderTemp.getUnits().getOrDefault(unitID,0) + unitHolderTemp.getUnitDamage().getOrDefault(unitID,0))
                           .sum();
            
            boolean otherUnitHoldersContainUnit = tile.getUnitHolders().values().stream()
                    .filter(planetTemp -> !planetTemp.getName().equals(planetName)).anyMatch(unitHolderTemp -> unitHolderTemp.getUnits().getOrDefault(unitID, 0) + unitHolderTemp.getUnitDamage().getOrDefault(unitID, 0) > 0);
            
            if(nonEmptyUnitHolders == 1) {
                   unitHolder = tile.getUnitHolders().values().stream()
                           .filter(unitHolderTemp -> unitHolderTemp.getUnits().getOrDefault(unitID,0) + unitHolderTemp.getUnitDamage().getOrDefault(unitID,0) > 0).findFirst().get();
                   
            }

            if (!priorityDmg) {
                Integer unitCountInSystem = unitHolder.getUnits().get(unitID);
                if (unitCountInSystem != null) {
                    Integer unitDamageCountInSystem = unitHolder.getUnitDamage().get(unitID);
                    if (unitDamageCountInSystem != null) {
                        countToRemove = unitDamageCountInSystem - (unitCountInSystem - count);
                        unitsDamage.put(unitID, countToRemove);
                    }
                }
            } else {
                countToRemove = count;
                Integer unitDamageCountInSystem = unitHolder.getUnitDamage().get(unitID);
                if (unitDamageCountInSystem != null) {
                    unitsDamage.put(unitID, Math.min(unitDamageCountInSystem, countToRemove));
                }

            }
            
            tile.removeUnit(unitHolder.getName(), unitID, count);
            tile.removeUnitDamage(unitHolder.getName(), unitID, countToRemove);
            
            // Check to see if we should remove from other unitHolders
            if((totalUnitsOnHex == count) && otherUnitHoldersContainUnit) {
                   for(String unitHolderName : tile.getUnitHolders().keySet()) {
                           if(!unitHolderName.equals(planetName)) {
                                   int tempCount = tile.getUnitHolders().get(unitHolderName).getUnits().getOrDefault(unitID,0);
                                   if(tempCount != 0) {
                                       tile.removeUnit(unitHolderName, unitID, tempCount);
                                   }
                                   tempCount = tile.getUnitHolders().get(unitHolderName).getUnitDamage().getOrDefault(unitID,0);
                                   if(tempCount != 0) {
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
    public String getActionID() {
        return Constants.MOVE_UNITS;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "From System/Tile name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Unit name/s. Example: Dread, 2 Warsuns")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "To System/Tile name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES_TO, "Unit name/s. Example: Dread, 2 Warsuns")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                                .setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.CC, "Type no or n to not add CC, r or retreat to add a CC without taking it from tactics").setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.PRIORITY_NO_DAMAGE, "Priority for not damaged units. Type in yes or y"))
        );
    }
}
