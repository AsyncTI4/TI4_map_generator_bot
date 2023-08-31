package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.leaders.LeaderInfo;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.tech.TechInfo;
import ti4.commands.tokens.AddToken;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.model.FactionModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;

public class Setup extends PlayerSubcommandData {
    public Setup() {
        super(Constants.SETUP, "Player initialisation: Faction and Color");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction Name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.HS_TILE_POSITION, "HS tile position (Ghosts choose position of gate)").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SPEAKER, "True to set player as speaker."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        if (!activeMap.isMapOpen()) {
            sendMessage("Can do faction setup only when map is open and not locked. Use `/game set_status open`");
            return;
        }

        @SuppressWarnings("ConstantConditions")
        String factionOption = event.getOption(Constants.FACTION, null, OptionMapping::getAsString);
        if (factionOption != null) factionOption = StringUtils.substringBefore(factionOption.toLowerCase().replace("the ", ""), " ");
        String faction = AliasHandler.resolveFaction(factionOption);
        if (!Mapper.isFaction(faction)) {
            sendMessage("Faction `" + faction + "` is not valid. Valid options are: " + Mapper.getFactions());
            return;
        }
        @SuppressWarnings("ConstantConditions")
        String color = AliasHandler.resolveColor(event.getOption(Constants.COLOR).getAsString().toLowerCase());
        if (!Mapper.isColorValid(color)) {
            sendMessage("Color `" + color + "` is not valid. Options are: " + Mapper.getColors());
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

        FactionModel setupInfo = player.getFactionSetupInfo();

        //HOME SYSTEM
        String positionHS = event.getOption(Constants.HS_TILE_POSITION, "", OptionMapping::getAsString);
        if (!PositionMapper.isTilePositionValid(positionHS)) {
            sendMessage("Tile position: `" + positionHS + "` is not valid. Stopping Setup.");
            return;
        }
        
        String hsTile = AliasHandler.resolveTile(setupInfo.getHomeSystem());
        Tile tile = new Tile(hsTile, positionHS);
        activeMap.setTile(tile);
        player.setPlayerStatsAnchorPosition(positionHS);

        //HANDLE GHOSTS' HOME SYSTEM LOCATION
        if ("ghost".equals(faction)){
            tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
            tile = new Tile("51", "tr");
            activeMap.setTile(tile);
        }

        //STARTING COMMODITIES
        player.setCommoditiesTotal(setupInfo.getCommodities());
        
        //STARTING PLANETS
        for (String planet : setupInfo.getHomePlanets()) {
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
        if (!activeMap.isFoWMode()) {
            sendMessage("Player: " + Helper.getPlayerRepresentation(player, activeMap) + " has been set up");
        } else {
            sendMessage("Player was set up.");
        }
        
        //STARTING TECH
        for (String tech : setupInfo.getStartingTech()) {
            if (tech.trim().isEmpty()){
                continue;
            }
            player.addTech(tech);
        }

        boolean setSpeaker = event.getOption(Constants.SPEAKER, false, OptionMapping::getAsBoolean);

        if (setSpeaker) {
            activeMap.setSpeaker(player.getUserID());
            sendMessage(Emojis.SpeakerToken + " Speaker assigned to: " + Helper.getPlayerRepresentation(player, activeMap));
        }        

        //STARTING PNs
        player.initPNs(activeMap);
        HashSet<String> playerPNs = new HashSet<>(player.getPromissoryNotes().keySet());
        player.setPromissoryNotesOwned(playerPNs);

        //STARTING OWNED UNITS
        HashSet<String> playerOwnedUnits = new HashSet<>(setupInfo.getUnits());
        player.setUnitsOwned(playerOwnedUnits);

        //SEND STUFF
        AbilityInfo.sendAbilityInfo(activeMap, player, event);
        TechInfo.sendTechInfo(activeMap, player, event);
        LeaderInfo.sendLeadersInfo(activeMap, player, event);
        UnitInfo.sendUnitInfo(activeMap, player, event);
        PNInfo.sendPromissoryNoteInfo(activeMap, player, false, event);
    }

    private void addUnits(FactionModel setupInfo, Tile tile, String color, SlashCommandInteractionEvent event) {
        String units = setupInfo.getStartingFleet();
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
            String unitPath = Tile.getUnitPath(unitID);
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
