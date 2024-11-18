package ti4.commands2.player;

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
import ti4.commands.leaders.LeaderInfo;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.tech.TechInfo;
import ti4.commands.tokens.AddToken;
import ti4.commands.units.AddRemoveUnits;
import ti4.commands2.GameStateSubcommand;
import ti4.commands2.uncategorized.CardsInfo;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ColorChangeHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.TitlesHelper;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel;
import ti4.service.info.AbilityInfoService;

public class Setup extends GameStateSubcommand {

    public Setup() {
        super(Constants.SETUP, "Player initialisation: Faction and Color", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction Name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.HS_TILE_POSITION, "HS tile position (Creuss choose position of gate)").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units").setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SPEAKER, "True to set player as speaker."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String faction = event.getOption(Constants.FACTION, null, OptionMapping::getAsString);
        if (faction != null) {
            faction = StringUtils.substringBefore(faction.toLowerCase().replace("the ", ""), " ");
        }

        faction = AliasHandler.resolveFaction(faction);
        if (!Mapper.isValidFaction(faction)) {
            MessageHelper.sendMessageToEventChannel(event, "Faction `" + faction + "` is not valid. Valid options are: " + Mapper.getFactionIDs());
            return;
        }

        Player player = getPlayer();

        String color = AliasHandler.resolveColor(event.getOption(Constants.COLOR, player.getNextAvailableColour(), OptionMapping::getAsString).toLowerCase());
        if (!Mapper.isValidColor(color)) {
            MessageHelper.sendMessageToEventChannel(event, "Color `" + color + "` is not valid. Options are: " + Mapper.getColors());
            return;
        }

        // SPEAKER
        boolean setSpeaker = event.getOption(Constants.SPEAKER, false, OptionMapping::getAsBoolean);
        String positionHS = StringUtils.substringBefore(event.getOption(Constants.HS_TILE_POSITION, "", OptionMapping::getAsString), " "); // Substring to grab "305" from "305 Moll Primus (Mentak)" autocomplete
        secondHalfOfPlayerSetup(player, game, color, faction, positionHS, event, setSpeaker);
    }

    public static void secondHalfOfPlayerSetup(Player player, Game game, String color, String faction, String positionHS, GenericInteractionCreateEvent event, boolean setSpeaker) {
        Map<String, Player> players = game.getPlayers();
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (color.equals(playerInfo.getColor())) {
                    String newColor = player.getNextAvailableColour();
                    String message = "Player:" + playerInfo.getUserName() + " already uses color:" + color + " - changing color to " + newColor;
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    return;
                } else if (faction.equals(playerInfo.getFaction())) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Setup Failed - Player:" + playerInfo.getUserName() + " already uses faction:" + faction);
                    return;
                }
            }
        }

        if (ColorChangeHelper.colorIsExclusive(color, player)) {
            color = player.getNextAvailableColorIgnoreCurrent();
        }

        if (player.isRealPlayer() && player.getSo() > 0) {
            String message = player.getRepresentationNoPing() + "has SOs that would get lost to the void if they were setup again. If they wish to change color, use /player change_color. If they want to setup as another faction, they must discard their SOs first";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            SecretObjectiveHelper.sendSecretObjectiveInfo(game, player);
            return;
        }

        player.setColor(color);
        player.setFaction(faction);
        player.setFactionEmoji(null);
        player.getPlanets().clear();
        player.getTechs().clear();
        player.getFactionTechs().clear();

        FactionModel factionModel = player.getFactionSetupInfo();

        if (game.isBaseGameMode()) {
            player.setLeaders(new ArrayList<>());
        }

        if (ComponentSource.miltymod.equals(factionModel.getSource()) && !game.isMiltyModMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "MiltyMod factions are a Homebrew Faction. Please enable the MiltyMod Game Mode first if you wish to use MiltyMod factions");
            return;
        }

        // HOME SYSTEM
        if (!PositionMapper.isTilePositionValid(positionHS)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Tile position: `" + positionHS + "` is not valid. Stopping Setup.");
            return;
        }

        String hsTile = AliasHandler.resolveTile(factionModel.getHomeSystem());
        Tile tile = new Tile(hsTile, positionHS);
        if (!StringUtils.isBlank(hsTile)) {
            game.setTile(tile);
        }

        String statsAnchor = PositionMapper.getEquivalentPositionAtRing(game.getRingCount(), positionHS);
        player.setPlayerStatsAnchorPosition(statsAnchor);

        // HANDLE GHOSTS' HOME SYSTEM LOCATION
        if ("ghost".equals(faction) || "miltymod_ghost".equals(faction)) {
            tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
            tile = new Tile("51", "tr");
            game.setTile(tile);
        }

        // STARTING COMMODITIES
        player.setCommoditiesTotal(factionModel.getCommodities());

        // STARTING PLANETS
        for (String planet : factionModel.getHomePlanets()) {
            if (planet.isEmpty()) {
                continue;
            }
            String planetResolved = AliasHandler.resolvePlanet(planet.toLowerCase());
            PlanetAdd.doAction(player, planetResolved, game, event, true);
            player.refreshPlanet(planetResolved);
        }

        player.getExhaustedPlanets().clear();

        // STARTING UNITS
        addUnits(factionModel, tile, color, event);

        // STARTING TECH
        List<String> startingTech = factionModel.getStartingTech();
        if (startingTech != null) {
            for (String tech : factionModel.getStartingTech()) {
                if (tech.trim().isEmpty()) {
                    continue;
                }
                player.addTech(tech);
            }
        }

        if (game.getTechnologyDeckID().contains("absol")) {
            List<String> techs = new ArrayList<>(player.getTechs());
            for (String tech : techs) {
                if (!tech.contains("absol") && Mapper.getTech("absol_" + tech) != null) {
                    if (!player.hasTech("absol_" + tech)) {
                        player.addTech("absol_" + tech);
                    }
                    player.removeTech(tech);
                }
            }
        }

        for (String tech : factionModel.getFactionTech()) {
            if (tech.trim().isEmpty()) {
                continue;
            }
            player.addFactionTech(tech);
        }

        if (setSpeaker) {
            game.setSpeakerUserID(player.getUserID());
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), Emojis.SpeakerToken + " Speaker assigned to: " + player.getRepresentation());
        }

        // STARTING PNs
        player.initPNs();
        Set<String> playerPNs = new HashSet<>(player.getPromissoryNotes().keySet());
        playerPNs.addAll(factionModel.getPromissoryNotes());
        player.setPromissoryNotesOwned(playerPNs);
        if (game.isBaseGameMode()) {
            Set<String> pnsOwned = new HashSet<>(player.getPromissoryNotesOwned());
            for (String pnID : pnsOwned) {
                if (pnID.endsWith("_an") && "Alliance".equals(Mapper.getPromissoryNote(pnID).getName())) {
                    player.removeOwnedPromissoryNoteByID(pnID);
                }
            }
        }
        if (game.isAbsolMode()) {
            Set<String> pnsOwned = new HashSet<>(player.getPromissoryNotesOwned());
            for (String pnID : pnsOwned) {
                if (pnID.endsWith("_ps") && "Political Secret".equals(Mapper.getPromissoryNote(pnID).getName())) {
                    player.removeOwnedPromissoryNoteByID(pnID);
                    player.addOwnedPromissoryNoteByID("absol_" + pnID);
                }
            }
        }

        // STARTING OWNED UNITS
        Set<String> playerOwnedUnits = new HashSet<>(factionModel.getUnits());
        player.setUnitsOwned(playerOwnedUnits);

        // Don't do special stuff if Franken Faction
        if (faction.startsWith("franken")) {
            return;
        }

        // SEND STUFF
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, factionModel.getFactionSheetMessage());
        AbilityInfoService.sendAbilityInfo(game, player, event);
        TechInfo.sendTechInfo(game, player, event);
        LeaderInfo.sendLeadersInfo(game, player, event);
        UnitInfo.sendUnitInfo(game, player, event, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false, event);

        if (player.getTechs().isEmpty() && !player.getFaction().contains("sardakk")) {
            if (player.getFaction().contains("keleres")) {
                Button getTech = Buttons.green("getKeleresTechOptions", "Get Keleres Tech Options");
                String msg = player.getRepresentationUnfogged() + " after every other faction gets their tech, press this button to resolve Keleres tech";
                MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), msg, getTech);
            } else {
                // STARTING TECH OPTIONS
                Integer bonusOptions = factionModel.getStartingTechAmount();
                List<String> startingTechOptions = factionModel.getStartingTechOptions();
                if (startingTechOptions != null && bonusOptions != null && bonusOptions > 0) {
                    List<TechnologyModel> techs = new ArrayList<>();
                    if (!startingTechOptions.isEmpty()) {
                        for (String tech : game.getTechnologyDeck()) {
                            TechnologyModel model = Mapper.getTech(tech);
                            boolean homebrewReplacesAnOption = model.getHomebrewReplacesID().map(startingTechOptions::contains).orElse(false);
                            if (startingTechOptions.contains(model.getAlias()) || homebrewReplacesAnOption) {
                                techs.add(model);
                            }
                        }
                    }

                    List<Button> buttons = Helper.getTechButtons(techs, player, "nekro");
                    String msg = player.getRepresentationUnfogged() + " use the buttons to choose your starting technology:";
                    if (techs.isEmpty()) {
                        buttons = List.of(Buttons.GET_A_FREE_TECH, Buttons.DONE_DELETE_BUTTONS);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                    } else {
                        for (int x = 0; x < bonusOptions; x++) {
                            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                        }
                    }
                }
            }
        }

        for (String fTech : player.getFactionTechs()) {
            if (!game.getTechnologyDeck().contains(fTech) && fTech.contains("ds")) {
                game.setTechnologyDeckID("techs_ds");
                break;
            }
        }

        if (player.hasAbility("diplomats")) {
            ButtonHelperAbilities.resolveFreePeopleAbility(game);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Set up free people ability markers. " + player.getRepresentationUnfogged()
                    + " any planet with the free people token on it will show up as spendable in your various spends. Once spent, the token will be removed");
        }

        if (player.hasAbility("private_fleet")) {
            String unitID = AliasHandler.resolveUnit("destroyer");
            player.setUnitCap(unitID, 12);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Set destroyer max to 12 for " + player.getRepresentation() + " due to the private fleet ability");
        }
        if (player.hasAbility("industrialists")) {
            String unitID = AliasHandler.resolveUnit("spacedock");
            player.setUnitCap(unitID, 4);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Set space dock max to 4 for " + player.getRepresentation() + " due to the industrialists ability");
        }
        if (player.hasAbility("teeming")) {
            String unitID = AliasHandler.resolveUnit("dreadnought");
            player.setUnitCap(unitID, 7);
            unitID = AliasHandler.resolveUnit("mech");
            player.setUnitCap(unitID, 5);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Set dreadnought unit max to 7 and mech unit max to 5 for " + player.getRepresentation()
                    + " due to the teeming ability");
        }
        if (player.hasAbility("necrophage") && player.getCommoditiesTotal() < 5 && !player.getFaction().contains("franken")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(game,
                Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        if (player.hasAbility("oracle_ai")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                    + " you may peek at the next objective in your cards info (by your PNs). This holds true for anyone with your PN. Don't do this until after secrets are dealt and discarded.");
        }
        CardsInfo.sendVariousAdditionalButtons(game, player);

        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "Player: " + player.getRepresentation() + " has been set up");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player was set up.");
        }

        Map<String, Game> mapList = GameManager.getGameNameToGame();
        for (Game game2 : mapList.values()) {
            for (Player player2 : game2.getRealPlayers()) {
                if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
                    if (!player2.getHoursThatPlayerIsAFK().isEmpty()) {
                        player.setHoursThatPlayerIsAFK(player2.getHoursThatPlayerIsAFK());
                    }
                    if (player2.doesPlayerPreferDistanceBasedTacticalActions()) {
                        player.setPreferenceForDistanceBasedTacticalActions(true);
                    }
                }
            }
        }

        if (!game.isFowMode()) {
            StringBuilder sb = TitlesHelper.getPlayerTitles(player.getUserID(), player.getUserName(), false);
            if (!sb.toString().contains("No titles yet")) {
                String msg = "In previous games, " + player.getUserName() + " has earned the titles of: \n" + sb;
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
            }
        }
        if (hsTile.equalsIgnoreCase("d11")) {
            AddToken.addToken(event, tile, Constants.FRONTIER, game);
        }
        if (game.getStoredValue("removeSupports").equalsIgnoreCase("true")) {
            Player p2 = player;
            p2.removeOwnedPromissoryNoteByID(p2.getColor() + "_sftt");
            p2.removePromissoryNote(p2.getColor() + "_sftt");
        }
    }

    private static void addUnits(FactionModel setupInfo, Tile tile, String color, GenericInteractionCreateEvent event) {
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
            if (unitID == null) {
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
