package ti4.service.milty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.tokens.AddTokenCommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.ColorChangeHelper;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.TIGLHelper;
import ti4.helpers.TitlesHelper;
import ti4.helpers.Units;
import ti4.helpers.settingsFramework.menus.GameSettings;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.helpers.settingsFramework.menus.PlayerFactionSettings;
import ti4.helpers.settingsFramework.menus.SliceGenerationSettings;
import ti4.helpers.settingsFramework.menus.SourceSettings;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.manage.GameManager;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.model.Source;
import ti4.model.TechnologyModel;
import ti4.service.PlanetService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.info.AbilityInfoService;
import ti4.service.info.CardsInfoService;
import ti4.service.info.LeaderInfoService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.info.TechInfoService;
import ti4.service.info.UnitInfoService;
import ti4.service.planet.AddPlanetService;
import ti4.service.tech.ListTechService;

@UtilityClass
public class MiltyService {

    public static void offerKeleresSetupButtons(MiltyDraftManager manager, Player player) {
        List<String> flavors = List.of("mentak", "xxcha", "argent");
        List<Button> keleresPresets = new ArrayList<>();
        boolean warn = false;
        for (String f : flavors) {
            if (manager.isFactionTaken(f)) continue;

            FactionModel model = Mapper.getFaction(f);
            String id = "draftPresetKeleres_" + f;
            String label = StringUtils.capitalize(f);
            if (manager.getFactionDraft().contains(f)) {
                keleresPresets.add(Buttons.gray(id, label + " 🛑", model.getFactionEmoji()));
                warn = true;
            } else {
                keleresPresets.add(Buttons.green(id, label, model.getFactionEmoji()));
            }
        }

        String message = player.getPing() + " Pre-select which flavor of Keleres to play in this game by clicking one of these buttons!";
        message += " You can change your decision later by clicking a different button.";
        if (warn) message += "\n- 🛑 Some of these factions are in the draft! 🛑 If you preset them and they get chosen, then the preset will be cancelled.";
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(player.getCardsInfoThread(), message, keleresPresets);
    }

    public static String startFromSettings(GenericInteractionCreateEvent event, MiltySettings settings) {
        Game game = settings.getGame();
        DraftSpec specs = new DraftSpec(game);

        // Load the general game settings
        boolean success = game.loadGameSettingsFromSettings(event, settings);
        if (!success) return "Fix the game settings before continuing";
        if (game.isCompetitiveTIGLGame()) {
            TIGLHelper.sendTIGLSetupText(game);
        }

        // Load Game Specifications
        GameSettings gameSettings = settings.getGameSettings();
        specs.setTemplate(gameSettings.getMapTemplate().getValue());

        // Load Slice Generation Specifications
        SliceGenerationSettings sliceSettings = settings.getSliceSettings();
        specs.numFactions = sliceSettings.getNumFactions().getVal();
        specs.numSlices = sliceSettings.getNumSlices().getVal();
        specs.anomaliesCanTouch = false;
        specs.extraWHs = sliceSettings.getExtraWorms().isVal();
        specs.minLegend = sliceSettings.getNumLegends().getValLow();
        specs.maxLegend = sliceSettings.getNumLegends().getValHigh();
        specs.minTot = sliceSettings.getTotalValue().getValLow();
        specs.maxTot = sliceSettings.getTotalValue().getValHigh();

        // Load Player & Faction Ban Specifications
        PlayerFactionSettings pfSettings = settings.getPlayerSettings();
        specs.bannedFactions.addAll(pfSettings.getBanFactions().getKeys());
        specs.priorityFactions.addAll(pfSettings.getPriFactions().getKeys());
        specs.setPlayerIDs(new ArrayList<>(pfSettings.getGamePlayers().getKeys()));
        if (pfSettings.getPresetDraftOrder().isVal()) {
            specs.playerDraftOrder = new ArrayList<>(game.getPlayers().keySet());
        }

        // Load Sources Specifications
        SourceSettings sources = settings.getSourceSettings();
        specs.setTileSources(sources.getTileSources());
        specs.setFactionSources(sources.getFactionSources());

        if (sliceSettings.getParsedSlices() != null) {
            if (sliceSettings.getParsedSlices().size() < specs.playerIDs.size())
                return "Not enough slices for the number of players. Please remove the preset slice string or include enough slices";
            specs.presetSlices = sliceSettings.getParsedSlices();
        }

        return startFromSpecs(event, specs);
    }

    public static String startFromSpecs(GenericInteractionCreateEvent event, DraftSpec specs) {
        Game game = specs.game;

        // Milty Draft Manager Setup --------------------------------------------------------------
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        draftManager.init(specs.tileSources);
        draftManager.setMapTemplate(specs.template.getAlias());
        game.setMapTemplateID(specs.template.getAlias());
        List<String> players = new ArrayList<>(specs.playerIDs);
        boolean staticOrder = specs.playerDraftOrder != null && !specs.playerDraftOrder.isEmpty();
        if (staticOrder) {
            players = new ArrayList<>(specs.playerDraftOrder).stream()
                .filter(p -> specs.playerIDs.contains(p)).toList();
        }
        initDraftOrder(draftManager, players, staticOrder);

        // initialize factions
        List<String> unbannedFactions = new ArrayList<>(Mapper.getFactions().stream()
            .filter(f -> specs.factionSources.contains(f.getSource()))
            .filter(f -> !specs.bannedFactions.contains(f.getAlias()))
            .filter(f -> !f.getAlias().contains("keleres") || f.getAlias().equals("keleresm")) // Limit the pool to only 1 keleres flavor
            .map(FactionModel::getAlias).toList());
        List<String> factionDraft = createFactionDraft(specs.numFactions, unbannedFactions, specs.priorityFactions);
        draftManager.setFactionDraft(factionDraft);

        // validate slice count + sources
        int redTiles = draftManager.getRed().size();
        int blueTiles = draftManager.getBlue().size();
        int maxSlices = Math.min(redTiles / 2, blueTiles / 3);
        if (specs.numSlices > maxSlices) {
            String msg = "Milty draft in this bot does not support " + specs.numSlices + " slices. You can enable DS to allow building additional slices";
            msg += "\n> The options you have selected enable a maximum of `" + maxSlices + "` slices. [" + blueTiles + "blue/" + redTiles + "red]";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            return "Could not start milty draft, fix the error and try again";
        }

        String startMsg = "## Generating the milty draft!!";
        startMsg += "\n - Also clearing out any tiles that may have already been on the map so that the draft will fill in tiles properly.";
        if (specs.numSlices == maxSlices) {
            startMsg += "\n - *You asked for the max number of slices, so this may take several seconds*";
        }

        game.clearTileMap();
        try {
            MiltyDraftHelper.buildPartialMap(game, event);
        } catch (Exception e) {
            // Ignore
        }

        if (specs.presetSlices != null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "### You are using preset slices!! Starting the draft right away!");
            specs.presetSlices.forEach(draftManager::addSlice);
            DraftDisplayService.repostDraftInformation(event, draftManager, game);
        } else {
            event.getMessageChannel().sendMessage(startMsg).queue((ignore) -> {
                boolean slicesCreated = GenerateSlicesService.generateSlices(event, draftManager, specs);
                if (!slicesCreated) {
                    String msg = "Generating slices was too hard so I gave up.... Please try again.";
                    if (specs.numSlices == maxSlices) {
                        msg += "\n*...and maybe consider asking for fewer slices*";
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                } else {
                    DraftDisplayService.repostDraftInformation(event, draftManager, game);
                    game.setPhaseOfGame("miltydraft");
                    GameManager.save(game, "Milty");
                }
            });
        }
        return null;
    }

    private static void initDraftOrder(MiltyDraftManager draftManager, List<String> playerIDs, boolean staticOrder) {
        List<String> players = new ArrayList<>(playerIDs);
        if (!staticOrder) {
            Collections.shuffle(players);
        }

        List<String> playersReversed = new ArrayList<>(players);
        Collections.reverse(playersReversed);

        List<String> draftOrder = new ArrayList<>(players);
        draftOrder.addAll(playersReversed);
        draftOrder.addAll(players);

        draftManager.setDraftOrder(draftOrder);
        draftManager.setPlayers(players);
    }

    private static List<String> createFactionDraft(int factionCount, List<String> factions, List<String> firstFactions) {
        List<String> randomOrder = new ArrayList<>(firstFactions);
        Collections.shuffle(randomOrder);
        Collections.shuffle(factions);
        randomOrder.addAll(factions);

        int i = 0;
        List<String> output = new ArrayList<>();
        while (output.size() < factionCount) {
            if (i > randomOrder.size()) return output;
            String f = randomOrder.get(i);
            i++;
            if (output.contains(f)) continue;
            output.add(f);
        }
        return output;
    }

    public static void miltySetup(GenericInteractionCreateEvent event, Game game) {
        MiltySettings menu = game.initializeMiltySettings();
        menu.postMessageAndButtons(event);
        ButtonHelper.deleteMessage(event);
    }

    @Data
    public static class DraftSpec {
        Game game;
        List<String> playerIDs, bannedFactions, priorityFactions, playerDraftOrder;
        MapTemplateModel template;
        List<Source.ComponentSource> tileSources, factionSources;
        Integer numSlices, numFactions;

        // slice generation settings
        Boolean anomaliesCanTouch = false, extraWHs = true;
        Double minRes = 2.0, minInf = 3.0;
        Integer minTot = 9, maxTot = 13;
        Integer minLegend = 1, maxLegend = 2;

        //other
        List<MiltyDraftSlice> presetSlices = null;

        public DraftSpec(Game game) {
            this.game = game;
            playerIDs = new ArrayList<>(game.getPlayerIDs());
            bannedFactions = new ArrayList<>();
            priorityFactions = new ArrayList<>();

            tileSources = new ArrayList<>();
            tileSources.add(Source.ComponentSource.base);
            tileSources.add(Source.ComponentSource.pok);
            tileSources.add(Source.ComponentSource.codex1);
            tileSources.add(Source.ComponentSource.codex2);
            tileSources.add(Source.ComponentSource.codex3);
            factionSources = new ArrayList<>(tileSources);
        }
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
            String message = player.getRepresentationNoPing() + "has secret objectives that would get lost to the void if they were setup again."
                + " If they wish to change color, use `/player change_color`. If they wish to setup as another faction, they must discard their secret objective first.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
            return;
        }

        player.setColor(color);
        player.setFaction(game, faction);
        player.setFactionEmoji(null);
        player.getPlanets().clear();
        player.getTechs().clear();
        player.getFactionTechs().clear();

        FactionModel factionModel = player.getFactionSetupInfo();

        if (game.isBaseGameMode()) {
            player.setLeaders(new ArrayList<>());
        }

        if (Source.ComponentSource.miltymod.equals(factionModel.getSource()) && !game.isMiltyModMode()) {
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
            AddPlanetService.addPlanet(player, planetResolved, game, event, true);
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

        Map<String, TechnologyModel> techReplacements = Mapper.getHomebrewTechReplaceMap(game.getTechnologyDeckID());
        List<String> playerTechs = new ArrayList<>(player.getTechs());
        for (String tech : playerTechs) {
            TechnologyModel model = techReplacements.getOrDefault(tech, Mapper.getTech(tech));
            if (!playerTechs.contains(model.getAlias())) {
                player.addTech(model.getAlias());
                player.removeTech(tech);
            }
        }

        for (String tech : factionModel.getFactionTech()) {
            if (tech.trim().isEmpty()) continue;

            TechnologyModel factionTech = techReplacements.getOrDefault(tech, Mapper.getTech(tech));
            player.addFactionTech(factionTech.getAlias());
        }

        if (setSpeaker) {
            game.setSpeakerUserID(player.getUserID());
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), MiscEmojis.SpeakerToken + " Speaker assigned to: " + player.getRepresentation());
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
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, factionModel.getFactionSheetMessage());
        AbilityInfoService.sendAbilityInfo(game, player, event);
        TechInfoService.sendTechInfo(game, player, event);
        LeaderInfoService.sendLeadersInfo(game, player, event);
        UnitInfoService.sendUnitInfo(game, player, event, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false, event);

        if (player.getTechs().isEmpty() && !player.getFaction().contains("sardakk")) {
            if (player.getFaction().contains("keleres")) {
                Button getTech = Buttons.green("getKeleresTechOptions", "Get Keleres Technology Options");
                String msg = player.getRepresentationUnfogged() + " after every other faction gets their starting technologies,"
                    + " press this button to for Keleres to get their starting technologies.";
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

                    List<Button> buttons = ListTechService.getTechButtons(techs, player, "free");
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
                "Set up **Free People** ability markers. " + player.getRepresentationUnfogged()
                    + " any planet with a **Free People** token on it will show up as spendable in your various spends. Once spent, the token will be removed.");
        }
        if (player.hasAbility("ancient_empire")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("startAncientEmpire", "Place a tomb token"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation() + " You can use this button to place 14 tomb tokens.", buttons);
        }

        if (player.hasAbility("private_fleet")) {
            String unitID = AliasHandler.resolveUnit("destroyer");
            player.setUnitCap(unitID, 12);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Set destroyer max to 12 for " + player.getRepresentation() + " due to the **Private Fleet** ability,");
        }
        if (player.hasAbility("industrialists")) {
            String unitID = AliasHandler.resolveUnit("spacedock");
            player.setUnitCap(unitID, 4);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Set space dock max to 4 for " + player.getRepresentation() + " due to the **Industrialists** ability,");
        }
        if (player.hasAbility("teeming")) {
            String unitID = AliasHandler.resolveUnit("dreadnought");
            player.setUnitCap(unitID, 7);
            unitID = AliasHandler.resolveUnit("mech");
            player.setUnitCap(unitID, 5);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "Set dreadnought unit max to 7 and mech unit max to 5 for " + player.getRepresentation()
                    + " due to the **Teeming** ability.");
        }
        if (player.hasAbility("policies")) {
            player.removeAbility("policies");
            player.addAbility("policy_the_people_connect");
            player.addAbility("policy_the_environment_preserve");
            player.addAbility("policy_the_economy_empower");
            player.removeOwnedUnitByID("olradin_mech");
            player.addOwnedUnitByID("olradin_mech_positive");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged() + " automatically set all of your policies to the positive side, but you can flip any of them now with these buttons");
            ButtonHelperHeroes.offerOlradinHeroFlips(game, player);
            ButtonHelperHeroes.offerOlradinHeroFlips(game, player);
            ButtonHelperHeroes.offerOlradinHeroFlips(game, player);
        }
        if (player.hasAbility("necrophage") && player.getCommoditiesTotal() < 5 && !player.getFaction().contains("franken")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(game,
                Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        if (player.hasAbility("oracle_ai")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                    + " you may peek at the next objective in your `#cards-info` thread (by your promissory note). "
                    + "This holds true for anyone with _Read the Fates_. Don't do this until after secret objectives are dealt and discarded.");
        }
        CardsInfoService.sendVariousAdditionalButtons(game, player);

        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                "Player: " + player.getRepresentation() + " has been set up");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player was set up.");
        }

        if (!game.isFowMode()) {
            StringBuilder sb = TitlesHelper.getPlayerTitles(player.getUserID(), player.getUserName(), false);
            if (!sb.toString().contains("No titles yet")) {
                String msg = "In previous games, " + player.getUserName() + " has earned the titles of: \n" + sb;
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
            }
        }
        if (hsTile.equalsIgnoreCase("d11")) {
            AddTokenCommand.addToken(event, tile, Constants.FRONTIER, game);
        }
        if (game.getStoredValue("removeSupports").equalsIgnoreCase("true")) {
            player.removeOwnedPromissoryNoteByID(player.getColor() + "_sftt");
            player.removePromissoryNote(player.getColor() + "_sftt");
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
            Units.UnitKey unitID = Mapper.getUnitKey(unit, color);
            if (unitID == null) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unit: " + unit + " is not valid and not supported.");
                continue;
            }
            if (unitInfoTokenizer.hasMoreTokens()) {
                planetName = AliasHandler.resolvePlanet(unitInfoTokenizer.nextToken());
            }
            planetName = PlanetService.getPlanet(tile, planetName);
            tile.addUnit(planetName, unitID, count);
        }
    }
}
