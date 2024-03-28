package ti4.commands.player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;

import ti4.buttons.Buttons;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.leaders.LeaderInfo;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.search.ListMyTitles;
import ti4.commands.tech.TechInfo;
import ti4.commands.tokens.AddToken;
import ti4.commands.uncategorized.CardsInfo;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperFactionSpecific;
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
import ti4.model.Source.ComponentSource;

public class Setup extends PlayerSubcommandData {
    public Setup() {
        super(Constants.SETUP, "Player initialisation: Faction and Color");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction Name").setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units").setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.HS_TILE_POSITION,
                "HS tile position (Ghosts choose position of gate)").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SPEAKER, "True to set player as speaker."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        String factionOption = event.getOption(Constants.FACTION, null, OptionMapping::getAsString);
        if (factionOption != null)
            factionOption = StringUtils.substringBefore(factionOption.toLowerCase().replace("the ", ""), " ");
        String faction = AliasHandler.resolveFaction(factionOption);
        if (!Mapper.isValidFaction(faction)) {
            sendMessage("Faction `" + faction + "` is not valid. Valid options are: " + Mapper.getFactionIDs());
            return;
        }
        String color = AliasHandler.resolveColor(event.getOption(Constants.COLOR).getAsString().toLowerCase());
        if (!Mapper.isValidColor(color)) {
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

        // SPEAKER
        boolean setSpeaker = event.getOption(Constants.SPEAKER, false, OptionMapping::getAsBoolean);
        String positionHS = StringUtils
                .substringBefore(event.getOption(Constants.HS_TILE_POSITION, "", OptionMapping::getAsString), " "); // Substring
                                                                                                                    // to
                                                                                                                    // grab
                                                                                                                    // "305"
                                                                                                                    // from
                                                                                                                    // "305
                                                                                                                    // Moll
                                                                                                                    // Primus
                                                                                                                    // (Mentak)"
                                                                                                                    // autocomplete
        secondHalfOfPlayerSetup(player, activeGame, color, faction, positionHS, event, setSpeaker);
    }

    public void secondHalfOfPlayerSetup(Player player, Game activeGame, String color, String faction, String positionHS,
            GenericInteractionCreateEvent event, boolean setSpeaker) {
        Map<String, Player> players = activeGame.getPlayers();
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (color.equals(playerInfo.getColor())) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Player:" + playerInfo.getUserName() + " already uses color:" + color);
                    return;
                } else if (faction.equals(playerInfo.getFaction())) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Player:" + playerInfo.getUserName() + " already uses faction:" + faction);
                    return;
                }
            }
        }
        if (player.isRealPlayer() && player.getSo() > 0) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "You have SOs that would get lost to the void if you were setup again. If you wish to change color, use /player change_color. If you want to setup as another faction, discard your SOs first");
            return;
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

        if (ComponentSource.miltymod.equals(setupInfo.getSource()) && !activeGame.isMiltyModMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "MiltyMod factions are a Homebrew Faction. Please enable the MiltyMod Game Mode first if you wish to use MiltyMod factions");
            return;
        }

        // HOME SYSTEM
        if (!PositionMapper.isTilePositionValid(positionHS)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Tile position: `" + positionHS + "` is not valid. Stopping Setup.");
            return;
        }

        String hsTile = AliasHandler.resolveTile(setupInfo.getHomeSystem());
        Tile tile = new Tile(hsTile, positionHS);
        if (!StringUtils.isBlank(hsTile))
            activeGame.setTile(tile);
        player.setPlayerStatsAnchorPosition(positionHS);

        // HANDLE GHOSTS' HOME SYSTEM LOCATION
        if ("ghost".equals(faction) || "miltymod_ghost".equals(faction)) {
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
        if (activeGame.getTechnologyDeckID().contains("absol")) {
            List<String> techs = new ArrayList<>();
            techs.addAll(player.getTechs());
            for (String tech : techs) {
                if (!tech.contains("absol") && Mapper.getTech("absol_" + tech) != null) {
                    if (!player.hasTech("absol_" + tech)) {
                        player.addTech("absol_" + tech);
                    }
                    player.removeTech(tech);
                }
            }
        }

        for (String tech : setupInfo.getFactionTech()) {
            if (tech.trim().isEmpty()) {
                continue;
            }
            player.addFactionTech(tech);
        }

        if (setSpeaker) {
            activeGame.setSpeaker(player.getUserID());
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    Emojis.SpeakerToken + " Speaker assigned to: " + player.getRepresentation());
        }

        // STARTING PNs
        player.initPNs();
        Set<String> playerPNs = new HashSet<>(player.getPromissoryNotes().keySet());
        playerPNs.addAll(setupInfo.getPromissoryNotes());
        player.setPromissoryNotesOwned(playerPNs);
        if (activeGame.isBaseGameMode()) {
            Set<String> pnsOwned = new HashSet<>(player.getPromissoryNotesOwned());
            for (String pnID : pnsOwned) {
                if (pnID.endsWith("_an") && "Alliance".equals(Mapper.getPromissoryNote(pnID).getName())) {
                    player.removeOwnedPromissoryNoteByID(pnID);
                }
            }
        }
        if (activeGame.isAbsolMode()) {
            Set<String> pnsOwned = new HashSet<>(player.getPromissoryNotesOwned());
            for (String pnID : pnsOwned) {
                if (pnID.endsWith("_ps") && "Political Secret".equals(Mapper.getPromissoryNote(pnID).getName())) {
                    player.removeOwnedPromissoryNoteByID(pnID);
                    player.addOwnedPromissoryNoteByID("absol_" + pnID);
                }
            }
        }

        // STARTING OWNED UNITS
        Set<String> playerOwnedUnits = new HashSet<>(setupInfo.getUnits());
        player.setUnitsOwned(playerOwnedUnits);

        // SEND STUFF
        AbilityInfo.sendAbilityInfo(activeGame, player, event);
        TechInfo.sendTechInfo(activeGame, player, event);
        LeaderInfo.sendLeadersInfo(activeGame, player, event);
        UnitInfo.sendUnitInfo(activeGame, player, event);
        PNInfo.sendPromissoryNoteInfo(activeGame, player, false, event);

        if (player.getTechs().isEmpty() && !player.getFaction().contains("sardakk")) {
            if (player.getFaction().contains("keleres")) {
                Button getTech = Button.success("getKeleresTechOptions", "Get Keleres Tech Options");
                List<Button> buttons = new ArrayList<>();
                buttons.add(getTech);
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        player.getRepresentation(true, true)
                                + " after every other faction gets their tech, press this button to resolve Keleres tech",
                        buttons);
            } else if (player.getFaction().contains("winnu")) {
                ButtonHelperFactionSpecific.offerWinnuStartingTech(player, activeGame);
            } else if (player.getFaction().contains("argent")) {
                ButtonHelperFactionSpecific.offerArgentStartingTech(player, activeGame);
            } else {
                activeGame.setComponentAction(true);
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
                        player.getRepresentation(true, true) + " you can use the button to get your starting tech",
                        List.of(Buttons.GET_A_TECH));
            }
        }

        for (String fTech : player.getFactionTechs()) {
            if (!activeGame.getTechnologyDeck().contains(fTech) && fTech.contains("ds")) {
                activeGame.setTechnologyDeckID("techs_ds");
                break;
            }
        }

        if (player.hasAbility("diplomats")) {
            ButtonHelperAbilities.resolveFreePeopleAbility(activeGame);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    "Set up free people ability markers. " + player.getRepresentation(true, true)
                            + " any planet with the free people token on it will show up as spendable in your various spends. Once spent, the token will be removed");
        }

        if (player.hasAbility("private_fleet")) {
            String unitID = AliasHandler.resolveUnit("destroyer");
            player.setUnitCap(unitID, 12);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    "Set destroyer max to 12 for " + player.getRepresentation() + " due to the private fleet ability");
        }
        if (player.hasAbility("industrialists")) {
            String unitID = AliasHandler.resolveUnit("spacedock");
            player.setUnitCap(unitID, 4);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    "Set spacedock max to 4 for " + player.getRepresentation() + " due to the industrialists ability");
        }
        if (player.hasAbility("teeming")) {
            String unitID = AliasHandler.resolveUnit("dreadnought");
            player.setUnitCap(unitID, 7);
            unitID = AliasHandler.resolveUnit("mech");
            player.setUnitCap(unitID, 5);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    "Set dread unit max to 7 and mech unit max to 5 for " + player.getRepresentation()
                            + " due to the teeming ability");
        }
        if (player.hasAbility("necrophage")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(activeGame,
                    Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        if (player.hasAbility("oracle_ai")) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
                    player.getRepresentation(true, true)
                            + " you can peek at the next objective in your cards info (by your PNs). This holds true for anyone with your PN. Don't do this until after secrets are dealt and discarded");
        }
        CardsInfo.sendVariousAdditionalButtons(activeGame, player);

        if (!activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                    "Player: " + player.getRepresentation() + " has been set up");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player was set up.");
        }
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        for (Game activeGame2 : mapList.values()) {
            for (Player player2 : activeGame2.getRealPlayers()) {
                if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                    player.setHoursThatPlayerIsAFK(player2.getHoursThatPlayerIsAFK());
                    if (player2.getPersonalPingInterval() > 0) {
                        player.setPersonalPingInterval(player2.getPersonalPingInterval());
                    }
                    if (player2.doesPlayerPreferDistanceBasedTacticalActions()) {
                        player.setPreferenceForDistanceBasedTacticalActions(true);
                    }
                }
            }
        }

        if (!activeGame.isFoWMode()) {
            StringBuilder sb = new ListMyTitles().getPlayerTitles(player.getUserID(), player.getUserName());
            if (!sb.toString().contains("No titles yet")) {
                String msg = "In previous games, " + player.getUserName() + " has earned the titles of: \n" + sb;
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(), msg);
            }
        }
        if (hsTile.equalsIgnoreCase("d11")) {
            AddToken.addToken(event, tile, Constants.FRONTIER, activeGame);
        }

    }

    private void addUnits(FactionModel setupInfo, Tile tile, String color, GenericInteractionCreateEvent event) {
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
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Unit: " + unit + " is not valid and not supported.");
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
