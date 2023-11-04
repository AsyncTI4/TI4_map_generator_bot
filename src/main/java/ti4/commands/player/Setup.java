package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.leaders.LeaderInfo;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.tech.TechInfo;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        Game activeGame = getActiveGame();

        String factionOption = event.getOption(Constants.FACTION, null, OptionMapping::getAsString);
        if (factionOption != null) factionOption = StringUtils.substringBefore(factionOption.toLowerCase().replace("the ", ""), " ");
        String faction = AliasHandler.resolveFaction(factionOption);
        if (!Mapper.isFaction(faction)) {
            sendMessage("Faction `" + faction + "` is not valid. Valid options are: " + Mapper.getFactions());
            return;
        }
        String color = AliasHandler.resolveColor(event.getOption(Constants.COLOR).getAsString().toLowerCase());
        if (!Mapper.isColorValid(color)) {
            sendMessage("Color `" + color + "` is not valid. Options are: " + Mapper.getColors());
            return;
        }
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        LinkedHashMap<String, Player> players = activeGame.getPlayers();
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
        player.setFactionEmoji(null);
        player.getPlanets().clear();
        player.getTechs().clear();
        player.getFactionTechs().clear();

        if (activeGame.isBaseGameMode()) {
            player.setLeaders(new ArrayList<>());
        }

        FactionModel setupInfo = player.getFactionSetupInfo();

        // HOME SYSTEM
        String positionHS = event.getOption(Constants.HS_TILE_POSITION, "", OptionMapping::getAsString);
        if (!PositionMapper.isTilePositionValid(positionHS)) {
            sendMessage("Tile position: `" + positionHS + "` is not valid. Stopping Setup.");
            return;
        }

        String hsTile = AliasHandler.resolveTile(setupInfo.getHomeSystem());
        Tile tile = new Tile(hsTile, positionHS);
        activeGame.setTile(tile);
        player.setPlayerStatsAnchorPosition(positionHS);

        // HANDLE GHOSTS' HOME SYSTEM LOCATION
        if ("ghost".equals(faction)) {

            tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
            tile = new Tile("51", "tr");
            activeGame.setTile(tile);
        }

        // STARTING COMMODITIES
        player.setCommoditiesTotal(setupInfo.getCommodities());

        // STARTING PLANETS
        for (String planet : setupInfo.getHomePlanets()) {
            if (planet.isEmpty()) {
                continue;
            }
            String planetResolved = AliasHandler.resolvePlanet(planet.toLowerCase());
            new PlanetAdd().doAction(player, planetResolved, activeGame);
            player.refreshPlanet(planetResolved);
        }

        player.getExhaustedPlanets().clear();

        // STARTING UNITS
        addUnits(setupInfo, tile, color, event);

        // STARTING TECH
        for (String tech : setupInfo.getStartingTech()) {
            if (tech.trim().isEmpty()) {
                continue;
            }
            player.addTech(tech);
        }

        for (String tech : setupInfo.getFactionTech()) {
            if (tech.trim().isEmpty()) {
                continue;
            }
            player.addFactionTech(tech);
        }

        // SPEAKER
        boolean setSpeaker = event.getOption(Constants.SPEAKER, false, OptionMapping::getAsBoolean);
        if (setSpeaker) {
            activeGame.setSpeaker(player.getUserID());
            sendMessage(Emojis.SpeakerToken + " Speaker assigned to: " + player.getRepresentation());
        }

        // STARTING PNs
        player.initPNs();
        HashSet<String> playerPNs = new HashSet<>(player.getPromissoryNotes().keySet());
        player.setPromissoryNotesOwned(playerPNs);
        if (activeGame.isBaseGameMode()) {
            Set<String> pnsOwned = new HashSet<>(player.getPromissoryNotesOwned());
            for (String pnID : pnsOwned) {
                if (pnID.endsWith("_an") && Mapper.getPromissoryNoteByID(pnID).getName().equals("Alliance")) {
                    player.removeOwnedPromissoryNoteByID(pnID);
                }
            }
        }
        if (activeGame.isAbsolMode()) {
            Set<String> pnsOwned = new HashSet<>(player.getPromissoryNotesOwned());
            for (String pnID : pnsOwned) {
                if (pnID.endsWith("_ps") && Mapper.getPromissoryNoteByID(pnID).getName().equals("Political Secret")) {
                    player.removeOwnedPromissoryNoteByID(pnID);
                    player.addOwnedPromissoryNoteByID("absol_" + pnID);
                }
            }
        }

        // STARTING OWNED UNITS
        HashSet<String> playerOwnedUnits = new HashSet<>(setupInfo.getUnits());
        player.setUnitsOwned(playerOwnedUnits);

        // SEND STUFF
        AbilityInfo.sendAbilityInfo(activeGame, player, event);
        TechInfo.sendTechInfo(activeGame, player, event);
        LeaderInfo.sendLeadersInfo(activeGame, player, event);
        UnitInfo.sendUnitInfo(activeGame, player, event);
        PNInfo.sendPromissoryNoteInfo(activeGame, player, false, event);
        if (player.getTechs().isEmpty() && !player.getFaction().contains("sardakk")) {
            activeGame.setComponentAction(true);
            Button getTech = Button.success("acquireATech", "Get a tech");
            List<Button> buttons = new ArrayList<>();
            buttons.add(getTech);
            MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                ButtonHelper.getTrueIdentity(player, activeGame) + " you can use the button to get your starting tech", buttons);
        }

        if(player.hasAbility("diplomats")){
            ButtonHelperAbilities.resolveFreePeopleAbility(activeGame);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set up free people ability markers. "+  ButtonHelper.getTrueIdentity(player, activeGame) + " any planet with the free people token on it will show up as spendable in your various spends. Once spent, the token will be removed");
        }

        if(player.hasAbility("private_fleet")){
            String unitID = AliasHandler.resolveUnit("destroyer");
            player.setUnitCap(unitID, 12);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set destroyer max to 12 for "+player.getRepresentation() +" due to the private fleet ability");
        }
        if(player.hasAbility("teeming")){
            String unitID = AliasHandler.resolveUnit("dreadnought");
            player.setUnitCap(unitID, 7);
            unitID = AliasHandler.resolveUnit("mech");
            player.setUnitCap(unitID, 5);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set dread unit max to 7 and mech unit max to 5 for "+player.getRepresentation() +" due to the teeming ability");
        }
        if (player.hasAbility("oracle_ai")) {
            activeGame.setUpPeakableObjectives(10);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set up peekable objective decks due to auger player. "+  ButtonHelper.getTrueIdentity(player, activeGame) + " you can peek at the next objective in your cards info (by your PNs). This holds true for anyone with your PN.");
            GameSaveLoadManager.saveMap(activeGame, event);
        }

        if (!activeGame.isFoWMode()) {
            sendMessage("Player: " + player.getRepresentation() + " has been set up");
        } else {
            sendMessage("Player was set up.");
        }
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        for (Game activeGame2 : mapList.values()) {
            for (Player player2 : activeGame2.getRealPlayers()) {
                if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                    player.setHoursThatPlayerIsAFK(player2.getHoursThatPlayerIsAFK());
                }
            }
        }
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
            UnitKey unitID = Mapper.getUnitKey(unit, color);
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
