package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands.player.PlanetAdd;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.map.Map;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

abstract public class AddRemoveUnits implements Command {

    @Override
    public List<String> limitChannelsKeywords() {
        return Arrays.asList("actions", "action");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        MapManager mapManager = MapManager.getInstance();
        if (!mapManager.isUserWithActiveMap(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        } else {
            Map activeMap = mapManager.getUserActiveMap(userID);
            String color = Helper.getColor(activeMap, event);
            if (!Mapper.isColorValid(color)) {
                MessageHelper.replyToMessage(event, "Color/Faction not valid");
                return;
            }

            String tileID = AliasHandler.resolveTile(event.getOption(Constants.TILE_NAME).getAsString().toLowerCase());
            Tile tile = getTile(event, tileID, activeMap);
            if (tile == null) return;

            unitParsingForTile(event, color, tile);
            MapSaveLoadManager.saveMap(activeMap);

            File file = GenerateMap.getInstance().saveImage(activeMap);
            MessageHelper.replyToMessage(event, file);
        }
    }

    public static Tile getTile(SlashCommandInteractionEvent event, String tileID, Map activeMap) {
        if (activeMap.isTileDuplicated(tileID)) {
            MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
            return null;
        }
        Tile tile = activeMap.getTile(tileID);
        if (tile == null) {
            tile = activeMap.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.replyToMessage(event, "Tile in map not found");
            return null;
        }
        return tile;
    }

    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile) {
        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString().toLowerCase();
        unitParsing(event, color, tile, unitList);
    }

    protected void unitParsing(SlashCommandInteractionEvent event, String color, Tile tile, String unitList) {
        unitList = unitList.replace(", ", ",");
        StringTokenizer tokenizer = new StringTokenizer(unitList, ",");
        while (tokenizer.hasMoreTokens()) {
            StringTokenizer unitInfoTokenizer = new StringTokenizer(tokenizer.nextToken(), " ");

            int count = 1;
            boolean numberIsSet = false;
            String planetName = Constants.SPACE;
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
            String unitID = Mapper.getUnitID(unit, color);
            String unitPath = tile.getUnitPath(unitID);
            if (unitPath == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Unit: " + unit + " is not valid and not supported.");
                continue;
            }
            if (unitInfoTokenizer.hasMoreTokens()) {
                planetName = AliasHandler.resolvePlanet(unitInfoTokenizer.nextToken());
            }
            planetName = getPlanet(event, tile, planetName);
            unitAction(event, tile, count, planetName, unitID, color);

            addPlanetToPlayArea(event, tile, planetName);
        }
        actionAfterAll(event, tile, color);
    }

    public void addPlanetToPlayArea(SlashCommandInteractionEvent event, Tile tile, String planetName) {
        String userID = event.getUser().getId();
        MapManager mapManager = MapManager.getInstance();
        Map activeMap = mapManager.getUserActiveMap(userID);
        if (!activeMap.isAllianceMode() && !Constants.SPACE.equals(planetName)){
            UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
            if (unitHolder != null){
                Set<String> allUnitsOnPlanet = unitHolder.getUnits().keySet();
                Set<String> unitColors = new HashSet<>();
                for (String unit_ : allUnitsOnPlanet) {
                    String unitColor = unit_.substring(0, unit_.indexOf("_"));
                    unitColors.add(unitColor);
                }
               if (unitColors.size() == 1){
                   String unitColor = unitColors.iterator().next();
                   for (Player player : activeMap.getPlayers().values()) {
                       if (player.getFaction() != null && player.getColor() != null) {
                           String colorID = Mapper.getColorID(player.getColor());
                           if (unitColor.equals(colorID)){
                               if (!player.getPlanets().contains(planetName)) {
                                   new PlanetAdd().doAction(player, planetName, activeMap);
                               }
                               break;
                           }
                       }
                   }
               }
            }
        }
    }

    public static String getPlanet(SlashCommandInteractionEvent event, Tile tile, String planetName) {
        if (!tile.isSpaceHolderValid(planetName)) {
            Set<String> unitHolderIDs = new HashSet<>(tile.getUnitHolders().keySet());
            unitHolderIDs.remove(Constants.SPACE);
            String finalPlanetName = planetName;
            List<String> validUnitHolderIDs = unitHolderIDs.stream().filter(unitHolderID -> unitHolderID.startsWith(finalPlanetName))
                    .collect(Collectors.toList());
            if (validUnitHolderIDs.size() == 1) {
                planetName = validUnitHolderIDs.get(0);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Planet: " + planetName + " is not valid and not supported.");
            }
        }
        return planetName;
    }

    abstract protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, String unitID, String color);

    protected void actionAfterAll(SlashCommandInteractionEvent event, Tile tile, String color){
        //do nothing, overriden by child classes
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                        .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Unit name/s. Example: Dread, 2 Warsuns")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                                .setAutoComplete(true))
        );
    }

    abstract protected String getActionDescription();


}
