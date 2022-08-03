package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapStringMapper;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

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
        addOptions(new OptionData(OptionType.STRING, Constants.KELERES_HS, "Keleres HS").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        if (!activeMap.isMapOpen()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Can do faction setup only when map is open and not locked");
            return;
        }

        @SuppressWarnings("ConstantConditions")
        String faction = AliasHandler.resolveFaction(event.getOption(Constants.FACTION).getAsString().toLowerCase());
        if (!Mapper.isFaction(faction)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Faction not valid");
            return;
        }
        @SuppressWarnings("ConstantConditions")
        String color = AliasHandler.resolveColor(event.getOption(Constants.COLOR).getAsString().toLowerCase());
        if (!Mapper.isColorValid(color)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Color not valid");
            return;
        }
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        if (playerOption != null) {
            String playerID = playerOption.getAsUser().getId();
            if (activeMap.getPlayer(playerID) != null) {
                player = activeMap.getPlayers().get(playerID);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Player:" + playerOption.getAsUser().getName() + " could not be found in map:" + activeMap.getName());
                return;
            }
        }

        LinkedHashMap<String, Player> players = activeMap.getPlayers();
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (color.equals(playerInfo.getColor())) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Player:" + playerInfo.getUserName() + " already uses color:" + color);
                    return;
                } else if (faction.equals(playerInfo.getFaction())) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Player:" + playerInfo.getUserName() + " already uses faction:" + faction);
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
        String playerSetup = null;
        if ("keleres".equals(faction)) {
            OptionMapping option = event.getOption(Constants.KELERES_HS);
            if (option != null) {
                playerSetup = Mapper.getPlayerSetup(option.getAsString());
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not setup keleres, select Keleres HS option");
                return;
            }
        } else {
            playerSetup = Mapper.getPlayerSetup(faction);
        }

        if (playerSetup == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not setup faction. Report to ADMIN");
            return;
        }




        String[] setupInfo = playerSetup.split(";");
        String hsTile = setupInfo[1];

        ArrayList<String> setup;
        boolean useSpecified = false;
        OptionMapping option = event.getOption(Constants.HS_TILE_POSITION);
        String positionHS = option != null ? option.getAsString().toLowerCase() : "";
        if (activeMap.getPlayerCountForMap() == 6){
            setup = Constants.setup6p;
            if (MapStringMapper.mapFor6Player.contains(positionHS)){
                useSpecified = true;
            }
        } else {
            setup = Constants.setup8p;
            if (MapStringMapper.mapFor8Player.contains(positionHS)){
                useSpecified = true;
            }
        }
        if (!positionHS.isEmpty() && !useSpecified){
            MessageHelper.sendMessageToChannel(event.getChannel(), "Tile position: " + positionHS + " not found in map. Stopping setup");
            return;
        }
        String position = useSpecified && !positionHS.isEmpty() ? positionHS : setup.get(index);
        Tile tile = new Tile(hsTile, position);
        activeMap.setTile(tile);


        player.setCommoditiesTotal(Integer.parseInt(setupInfo[3]));
        for (String tech : setupInfo[5].split(",")) {
            if (tech.trim().isEmpty()){
                continue;
            }
            player.addTech(tech);
        }

        for (String planet : setupInfo[6].split(",")) {
            if (planet.isEmpty()){
                continue;
            }
            String planetResolved = AliasHandler.resolvePlanet(planet.toLowerCase());
            new PlanetAdd().doAction(player, planetResolved, activeMap);
            player.refreshPlanet(planetResolved);
        }
        player.getExhaustedPlanets().clear();
        addUnits(setupInfo, tile, color, event);
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
                MessageHelper.sendMessageToChannel(event.getChannel(), "Unit: " + unit + " is not valid and not supported.");
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
