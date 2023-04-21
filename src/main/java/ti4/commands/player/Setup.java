package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapStringMapper;
import ti4.map.Player;
import ti4.map.Tile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.StringTokenizer;

public class Setup extends PlayerSubcommandData {
    public Setup() {
        super(Constants.SETUP, "Player initialisation: Faction and Color");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction Name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction"));
        addOptions(new OptionData(OptionType.STRING, Constants.HS_TILE_POSITION, "HS tile position"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        if (!activeMap.isMapOpen()) {
            sendMessage("Can do faction setup only when map is open and not locked");
            return;
        }

        @SuppressWarnings("ConstantConditions")
        String faction = AliasHandler.resolveFaction(event.getOption(Constants.FACTION).getAsString().toLowerCase());
        if (!Mapper.isFaction(faction)) {
            sendMessage("Faction not valid");
            return;
        }
        @SuppressWarnings("ConstantConditions")
        String color = AliasHandler.resolveColor(event.getOption(Constants.COLOR).getAsString().toLowerCase());
        if (!Mapper.isColorValid(color)) {
            sendMessage("Color not valid");
            return;
        }
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        LinkedHashMap<String, Player> players = activeMap.getPlayers();
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (color.equals(playerInfo.getColor())) {
                    sendMessage("Player:" + playerInfo.getUserName() + " already uses color:" + color);
                    return;
                } else if (faction.equals(playerInfo.getFaction())) {
                    sendMessage("Player:" + playerInfo.getUserName() + " already uses faction:" + faction);
                    return;
                }
            }
        }

        player.setColor(color);
        player.setFaction(faction);
        player.getPlanets().clear();
        player.getTechs().clear();

        int index = -1;
        for (Player player_ : players.values()) {
            index++;
            if (player_ == player) {
                break;
            }
        }

        String[] setupInfo = player.getFactionSetupInfo();
       
        //HOME SYSTEM
        String hsTile = setupInfo[1];

        ArrayList<String> setup;
        boolean useSpecified = false;
        OptionMapping option = event.getOption(Constants.HS_TILE_POSITION);
        String positionHS = option != null ? option.getAsString().toLowerCase() : "";
        boolean is6playerMap = true;
        if (activeMap.getPlayerCountForMap() == 6){
            setup = Constants.setup6p;
            if (PositionMapper.isTilePositionValid(positionHS)){
                useSpecified = true;
            }
        } else {
            setup = Constants.setup8p;
            is6playerMap = false;
            if (activeMap.getRingCount() == 8)
            {
                useSpecified = true;
            } else if (PositionMapper.isTilePositionValid(positionHS)){
                useSpecified = true;
            }
        }
        if (!positionHS.isEmpty() && !useSpecified){
            sendMessage("Tile position: " + positionHS + " not found in map. Stopping setup");
            return;
        }
        String position = useSpecified && !positionHS.isEmpty() ? positionHS : setup.get(index);
        Tile tile = new Tile(hsTile, position);
        activeMap.setTile(tile);

        //HANDLE GHOSTS' HOME SYSTEM LOCATION
        if ("ghost".equals(faction)){
            if (useSpecified){
                position = "tr";
            } else {
                int positionNumber = 1;
                for (Player playerInfo : players.values()) {
                    if (playerInfo.equals(player)) {
                        break;
                    }
                    positionNumber++;
                }


                if (is6playerMap) {
                   if (positionNumber == 1 || positionNumber == 2){
                       position = "tr";
                   } else if (positionNumber == 3 || positionNumber == 4) {
                       position = "br";
                   } else if (positionNumber == 5){
                       position = "bl";
                   } else {
                       position = "tl";
                       Tile mallice = activeMap.getTile(position);
                       mallice.setPosition("tr");
                       activeMap.removeTile("tl");
                       activeMap.setTile(mallice);
                   }
                }else{
                    if (positionNumber == 1 || positionNumber == 2 || positionNumber == 3 ){
                        position = "tr";
                    } else if (positionNumber == 4 || positionNumber == 5) {
                        position = "br";
                    } else if (positionNumber == 6 || positionNumber == 7){
                        position = "bl";
                    } else {
                        position = "tl";
                        Tile mallice = activeMap.getTile(position);
                        mallice.setPosition("tr");
                        activeMap.removeTile("tl");
                        activeMap.setTile(mallice);
                    }
                }
            }
            tile = new Tile("51", position);
            activeMap.setTile(tile);
        }

        //STARTING COMMODITIES
        player.setCommoditiesTotal(Integer.parseInt(setupInfo[3]));
        for (String tech : setupInfo[5].split(",")) {
            if (tech.trim().isEmpty()){
                continue;
            }
            player.addTech(tech);
        }

        //STARTING PLANETS
        for (String planet : setupInfo[6].split(",")) {
            if (planet.isEmpty()){
                continue;
            }
            String planetResolved = AliasHandler.resolvePlanet(planet.toLowerCase());
            new PlanetAdd().doAction(player, planetResolved, activeMap);
            player.refreshPlanet(planetResolved);
        }

        player.getExhaustedPlanets().clear();

        //STARTING UNITS
        addUnits(setupInfo, tile, color, event);
        if(!activeMap.isFoWMode()) {
            sendMessage("Player: " + Helper.getPlayerRepresentation(event, player) + " has been set up");
        } else {
            sendMessage("Player was set up.");
        }
    }

    private void addUnits(String[] setupInfo, Tile tile, String color, SlashCommandInteractionEvent event) {
        String units = setupInfo[2];
        units = units.replace(", ", ",");
        StringTokenizer tokenizer = new StringTokenizer(units, ",");
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
                sendMessage("Unit: " + unit + " is not valid and not supported.");
                continue;
            }
            if (unitInfoTokenizer.hasMoreTokens()) {
                planetName = AliasHandler.resolvePlanet(unitInfoTokenizer.nextToken());
            }
            planetName = AddRemoveUnits.getPlanet(event, tile, planetName);
            tile.addUnit(planetName, unitID, count);
        }
    }
}
