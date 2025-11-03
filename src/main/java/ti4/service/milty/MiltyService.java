package ti4.service.milty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.commands.tokens.AddTokenCommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.ColorChangeHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.TIGLHelper;
import ti4.helpers.ThreadArchiveHelper;
import ti4.helpers.TitlesHelper;
import ti4.helpers.Units;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Source;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel;
import ti4.service.PlanetService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.info.AbilityInfoService;
import ti4.service.info.CardsInfoService;
import ti4.service.info.LeaderInfoService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.info.TechInfoService;
import ti4.service.info.UnitInfoService;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.planet.AddPlanetService;
import ti4.service.rules.ThundersEdgeRulesService;
import ti4.service.tech.ListTechService;
import ti4.service.unit.AddUnitService;

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

        String message = player.getPing()
                + " Pre-select which flavor of Keleres to play in this game by clicking one of these buttons!";
        message += " You can change your decision later by clicking a different button.";
        if (warn)
            message +=
                    "\n- 🛑 Some of these factions are in the draft! 🛑 If you preset them and they get chosen, then the preset will be cancelled.";
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(player.getCardsInfoThread(), message, keleresPresets);
    }

    public static String startFromSettings(GenericInteractionCreateEvent event, MiltySettings settings) {
        Game game = settings.getGame();

        // Load the general game settings
        boolean success = game.loadGameSettingsFromSettings(event, settings);
        if (!success) return "Fix the game settings before continuing";
        if (game.isCompetitiveTIGLGame()) {
            TIGLHelper.sendTIGLSetupText(game);
        }

        MiltyDraftSpec specs = MiltyDraftSpec.fromSettings(settings);

        return startFromSpecs(event, specs);
    }

    public static String startFromSpecs(GenericInteractionCreateEvent event, MiltyDraftSpec specs) {
        Game game = specs.game;

        if (specs.presetSlices != null) {
            if (specs.presetSlices.size() < specs.playerIDs.size())
                return "Not enough slices for the number of players. Please remove the preset slice string or include enough slices";
        }

        // Milty Draft Manager Setup --------------------------------------------------------------
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        List<ComponentSource> sources = new ArrayList<>(specs.tileSources);
        if (game.isDiscordantStarsMode() || game.isUnchartedSpaceStuff()) {
            sources.add(ComponentSource.ds);
            sources.add(ComponentSource.uncharted_space);
        }
        if ((!game.isBaseGameMode() && game.getStoredValue("useOldPok").isEmpty()) || game.isTwilightsFallMode()) {
            sources.add(ComponentSource.thunders_edge);
        }

        draftManager.init(sources);
        draftManager.setMapTemplate(specs.template.getAlias());
        game.setMapTemplateID(specs.template.getAlias());
        List<String> players = new ArrayList<>(specs.playerIDs);
        boolean staticOrder = specs.playerDraftOrder != null && !specs.playerDraftOrder.isEmpty();
        if (staticOrder) {
            players = new ArrayList<>(specs.playerDraftOrder)
                    .stream().filter(p -> specs.playerIDs.contains(p)).toList();
        }
        initDraftOrder(draftManager, players, staticOrder);

        // initialize factions
        List<String> unbannedFactions = new ArrayList<>(Mapper.getFactionsValues().stream()
                .filter(f -> specs.factionSources.contains(f.getSource()))
                .filter(f -> !specs.bannedFactions.contains(f.getAlias()))
                .filter(f -> !f.getAlias().contains("obsidian"))
                .filter(f -> !f.getAlias().contains("keleres")
                        || "keleresm".equals(f.getAlias())) // Limit the pool to only 1 keleres flavor
                .map(FactionModel::getAlias)
                .toList());
        List<String> factionDraft = createFactionDraft(specs.numFactions, unbannedFactions, specs.priorityFactions);
        draftManager.setFactionDraft(factionDraft);

        // validate slice count + sources
        int redTiles = draftManager.getRed().size();
        int blueTiles = draftManager.getBlue().size();
        int maxSlices = Math.min(redTiles / 2, blueTiles / 3);
        if (specs.numSlices > maxSlices) {
            String msg = "Milty draft in this bot does not support " + specs.numSlices
                    + " slices. You can enable DS to allow building additional slices";
            msg += "\n> The options you have selected enable a maximum of `" + maxSlices + "` slices. [" + blueTiles
                    + "blue/" + redTiles + "red]";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            return "Could not start milty draft, fix the error and try again";
        }

        String startMsg = "## Generating the milty draft!!";
        startMsg +=
                "\n - Also clearing out any tiles that may have already been on the map so that the draft will fill in tiles properly.";
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
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "### You are using preset slices!! Starting the draft right away!");
            specs.presetSlices.forEach(draftManager::addSlice);
            MiltyDraftDisplayService.repostDraftInformation(draftManager, game);
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
                    MiltyDraftDisplayService.repostDraftInformation(draftManager, game);
                    game.setPhaseOfGame("miltydraft");
                    GameManager.save(game, "Milty"); // TODO: We should be locking since we're saving
                    if (game.isThundersEdge()) {
                        ThundersEdgeRulesService.alertTabletalkWithRulesAtStartOfDraft(game);
                    }
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

    private static List<String> createFactionDraft(
            int factionCount, List<String> factions, List<String> firstFactions) {
        List<String> randomOrder = new ArrayList<>(firstFactions);
        Collections.shuffle(randomOrder);
        Collections.shuffle(factions);
        randomOrder.addAll(factions);

        int i = 0;
        List<String> output = new ArrayList<>();
        while (output.size() < factionCount) {
            if (i >= randomOrder.size()) return output;
            String f = randomOrder.get(i);
            i++;
            if (output.contains(f)) continue;
            output.add(f);
        }
        return output;
    }

    public static void miltySetup(GenericInteractionCreateEvent event, Game game) {
        MiltySettings menu = game.initializeMiltySettings();
        // TODO: Settings should use a flag for nucleus generation.
        // But for now, trying to keep our settings changes minimal.
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            if (buttonEvent.getButton().getCustomId().endsWith("_nucleus")) {
                menu.getDraftMode().setChosenKey("nucleus");
            }
        }
        menu.postMessageAndButtons(event);
        ButtonHelper.deleteMessage(event);
    }

    public static void secondHalfOfPlayerSetup(
            Player player,
            Game game,
            String color,
            String faction,
            String positionHS,
            GenericInteractionCreateEvent event,
            boolean setSpeaker) {
        Map<String, Player> players = game.getPlayers();
        ThreadArchiveHelper.checkThreadLimitAndArchive(event.getGuild());
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (color.equals(playerInfo.getColor())) {
                    String newColor = player.getNextAvailableColour();
                    String message = "Player:" + playerInfo.getUserName() + " already uses color:" + color
                            + " - changing color to " + newColor;
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    break;
                } else if (faction.equals(playerInfo.getFaction())) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            "Setup Failed - Player:" + playerInfo.getUserName() + " already uses faction:" + faction);
                    return;
                }
                if ("franken1".equalsIgnoreCase(faction) || "franken2".equalsIgnoreCase(faction)) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            "Setup Failed - Franken1 and Franken2 have issues and should not be used by anyone going forward. Try a different franken number");
                    return;
                }
            }
        }

        if (ColorChangeHelper.colorIsExclusive(color, player)) {
            color = player.getNextAvailableColorIgnoreCurrent();
        }

        if (player.isRealPlayer() && player.getSo() > 0) {
            String message = player.getRepresentationNoPing()
                    + "has secret objectives that would get lost to the void if they were setup again."
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

        if (factionModel.getSource() == Source.ComponentSource.miltymod && !game.isMiltyModMode()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "MiltyMod factions are a Homebrew Faction. Please enable the MiltyMod Game Mode first if you wish to use MiltyMod factions");
            return;
        }

        String breakthrough = factionModel.getBreakthrough();
        // BREAKTHROUGH
        if (Mapper.getBreakthrough(breakthrough) != null) {
            player.setBreakthroughID(breakthrough);
            player.setBreakthroughUnlocked(false);
            player.setBreakthroughExhausted(false);
            player.setBreakthroughActive(false);
            player.setBreakthroughTGs(0);
        }

        // HOME SYSTEM
        if (!PositionMapper.isTilePositionValid(positionHS)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Tile position: `" + positionHS + "` is not valid. Stopping Setup.");
            return;
        }

        String hsTile = AliasHandler.resolveTile(factionModel.getHomeSystem());
        Tile tile = new Tile(hsTile, positionHS);
        if (!StringUtils.isBlank(hsTile)) {
            game.setTile(tile);
        }

        // String statsAnchor = PositionMapper.getEquivalentPositionAtRing(game.getRingCount(), positionHS);
        player.setPlayerStatsAnchorPosition(positionHS);

        // GHOST AND CRIMSON EXTRA HS TILES
        if ("ghost".equals(faction) || "miltymod_ghost".equals(faction)) {
            tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
            String pos = "tr";
            if ("307".equalsIgnoreCase(positionHS) || "310".equalsIgnoreCase(positionHS)) {
                pos = "br";
            }
            if ("313".equalsIgnoreCase(positionHS) || "316".equalsIgnoreCase(positionHS)) {
                pos = "bl";
            }
            if (game.getTileByPosition(pos) != null) {
                if (pos.equalsIgnoreCase("tr")) {
                    pos = "br";
                } else {
                    pos = "tr";
                }
            }
            tile = new Tile("51", pos);
            game.setTile(tile);
            player.setHomeSystemPosition(pos);
        }

        // HANDLE Crimson' HOME SYSTEM LOCATION
        if ("crimson".equals(faction)) {
            tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
            tile.addToken(Constants.TOKEN_BREACH_INACTIVE, Constants.SPACE);
            String pos = "tr";
            if ("307".equalsIgnoreCase(positionHS) || "310".equalsIgnoreCase(positionHS)) {
                pos = "br";
            }
            if ("313".equalsIgnoreCase(positionHS) || "316".equalsIgnoreCase(positionHS)) {
                pos = "bl";
            }
            if (game.getTileByPosition(pos) != null) {
                if (pos.equalsIgnoreCase("tr")) {
                    pos = "br";
                } else {
                    pos = "tr";
                }
            }
            tile = new Tile("118", pos);
            game.setTile(tile);
            player.setHomeSystemPosition(pos);
        }

        // STARTING COMMODITIES
        player.setCommoditiesBase(factionModel.getCommodities());

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

        if (!"techs_tf".equals(game.getTechnologyDeckID())) {

            Map<String, TechnologyModel> techReplacements =
                    Mapper.getHomebrewTechReplaceMap(game.getTechnologyDeckID());
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
                if (tech.equalsIgnoreCase("iihq") && game.isThundersEdge()) {
                    tech = "executiveorder";
                }
                TechnologyModel factionTech = techReplacements.getOrDefault(tech, Mapper.getTech(tech));

                player.addFactionTech(factionTech.getAlias());
            }
        }

        if (setSpeaker) {
            game.setSpeakerUserID(player.getUserID());
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    MiscEmojis.SpeakerToken + " Speaker assigned to: " + player.getRepresentation());
        }
        if (!game.isTwilightsFallMode()) {
            // STARTING PNs
            player.initPNs();
            Set<String> playerPNs = new HashSet<>(player.getPromissoryNotes().keySet());
            playerPNs.addAll(factionModel.getPromissoryNotes());
            player.setPromissoryNotesOwned(playerPNs);
            if (game.isBaseGameMode()) {
                Set<String> pnsOwned = new HashSet<>(player.getPromissoryNotesOwned());
                for (String pnID : pnsOwned) {
                    if (pnID.endsWith("_an")
                            && "Alliance".equals(Mapper.getPromissoryNote(pnID).getName())) {
                        player.removeOwnedPromissoryNoteByID(pnID);
                    }
                }
            }
            if (game.isAbsolMode()) {
                Set<String> pnsOwned = new HashSet<>(player.getPromissoryNotesOwned());
                for (String pnID : pnsOwned) {
                    if (pnID.endsWith("_ps")
                            && "Political Secret"
                                    .equals(Mapper.getPromissoryNote(pnID).getName())) {
                        player.removeOwnedPromissoryNoteByID(pnID);
                        player.addOwnedPromissoryNoteByID("absol_" + pnID);
                    }
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
        AbilityInfoService.sendAbilityInfo(player, event);
        TechInfoService.sendTechInfo(game, player, event);
        if (!game.getStoredValue("useOldPok").isEmpty() && player.hasLeader("naaluagent-te")) {
            player.removeLeader("naaluagent-te");
            player.addLeader("naaluagent");
            player.removeOwnedUnitByID("naalu_mech_te");
            player.addOwnedUnitByID("naalu_mech");
        }
        if (game.isThundersEdge() && player.hasLeader("xxchahero")) {
            player.removeLeader("xxchahero");
            player.addLeader("xxchahero-te");
        }
        LeaderInfoService.sendLeadersInfo(game, player, event);
        UnitInfoService.sendUnitInfo(game, player, event, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false, event);

        if (player.getTechs().isEmpty() && !player.getFaction().contains("sardakk")) {
            if (player.getFaction().contains("keleres")) {
                Button getTech = Buttons.green("getKeleresTechOptions", "Get Keleres Technology Options");
                String msg = player.getRepresentationUnfogged()
                        + " after every other faction gets their starting technologies,"
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
                            boolean homebrewReplacesAnOption = model.getHomebrewReplacesID()
                                    .map(startingTechOptions::contains)
                                    .orElse(false);
                            if (startingTechOptions.contains(model.getAlias()) || homebrewReplacesAnOption) {
                                techs.add(model);
                            }
                        }
                    }

                    List<Button> buttons = ListTechService.getTechButtons(techs, player, "free");
                    String msg =
                            player.getRepresentationUnfogged() + " use the buttons to choose your starting technology:";
                    if (techs.isEmpty()) {
                        buttons = List.of(Buttons.GET_A_FREE_TECH, Buttons.DONE_DELETE_BUTTONS);
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                        if (factionModel.getStartingTechAmount() > 1) {
                            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                        }
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
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Set up **Free People** ability markers. " + player.getRepresentationUnfogged()
                            + " any planet with a **Free People** token on it will show up as spendable in your various spends. Once spent, the token will be removed.");
        }
        if (player.hasAbility("ancient_empire")) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("startAncientEmpire", "Place a tomb token"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", please place up to 14 Tomb tokens for **Ancient Empire**.",
                    buttons);
        }

        if (player.hasAbility("private_fleet")) {
            String unitID = AliasHandler.resolveUnit("destroyer");
            player.setUnitCap(unitID, 12);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Set destroyer max to 12 for " + player.getRepresentation()
                            + ", due to the **Private Fleet** ability,");
        }
        if (player.hasAbility("industrialists")) {
            String unitID = AliasHandler.resolveUnit("spacedock");
            player.setUnitCap(unitID, 4);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Set space dock max to 4 for " + player.getRepresentation()
                            + ", due to the **Industrialists** ability,");
        }
        if (player.hasAbility("teeming")) {
            String unitID = AliasHandler.resolveUnit("dreadnought");
            player.setUnitCap(unitID, 7);
            unitID = AliasHandler.resolveUnit("mech");
            player.setUnitCap(unitID, 5);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Set dreadnought unit max to 7 and mech unit max to 5 for " + player.getRepresentation()
                            + ", due to the **Teeming** ability.");
        }
        if (player.hasAbility("machine_cult")) {
            String unitID = AliasHandler.resolveUnit("mech");
            player.setUnitCap(unitID, 6);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Set mech unit maximum to 6 for " + player.getRepresentation()
                            + ", due to their **Machine Cult** ability.");
        }
        if (game.isAgeOfFightersMode()) {
            String tech = "ff2";
            for (String factionTech : player.getNotResearchedFactionTechs()) {
                TechnologyModel fTech = Mapper.getTech(factionTech);
                if (fTech != null
                        && !fTech.getAlias()
                                .equalsIgnoreCase(Mapper.getTech(tech).getAlias())
                        && fTech.isUnitUpgrade()
                        && fTech.getBaseUpgrade()
                                .orElse("bleh")
                                .equalsIgnoreCase(Mapper.getTech(tech).getAlias())) {
                    tech = fTech.getAlias();
                    break;
                }
            }
            player.addTech(tech);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " gained the "
                            + Mapper.getTech(tech).getNameRepresentation()
                            + " technology due to the _Age of Fighters_ galactic event.");
        }
        if (game.isAdventOfTheWarsunMode()) {
            if (player.getFaction().equalsIgnoreCase("muaat")) {
                player.removeOwnedPromissoryNoteByID("fires");
                UnlockLeaderService.unlockLeader("muaatcommander", game, player);
                AddUnitService.addUnits(event, tile, game, color, "ws");
            } else {
                String tech = "ws";
                for (String factionTech : player.getNotResearchedFactionTechs()) {
                    TechnologyModel fTech = Mapper.getTech(factionTech);
                    if (fTech != null
                            && !fTech.getAlias()
                                    .equalsIgnoreCase(Mapper.getTech(tech).getAlias())
                            && fTech.isUnitUpgrade()
                            && fTech.getBaseUpgrade()
                                    .orElse("bleh")
                                    .equalsIgnoreCase(Mapper.getTech(tech).getAlias())) {
                        tech = fTech.getAlias();
                        break;
                    }
                }
                player.addTech(tech);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation() + " gained the "
                                + Mapper.getTech(tech).getNameRepresentation()
                                + " technology due to the _Advent of the Warsun_ galactic event.");
            }
        }
        if (game.isStellarAtomicsMode()) {
            if (game.getRevealedPublicObjectives().get("Stellar Atomics") != null) {
                int stellarID = game.getRevealedPublicObjectives().get("Stellar Atomics");
                game.scorePublicObjective(player.getUserID(), stellarID);
            } else {
                int poIndex = game.addCustomPO("Stellar Atomics", 0);
                for (Player playerWL : game.getRealPlayers()) {
                    game.scorePublicObjective(playerWL.getUserID(), poIndex);
                }
            }
        }
        if (player.hasAbility("policies")) {
            player.removeAbility("policies");
            player.addAbility("policy_the_people_connect");
            player.addAbility("policy_the_environment_preserve");
            player.addAbility("policy_the_economy_empower");
            player.removeOwnedUnitByID("olradin_mech");
            player.addOwnedUnitByID("olradin_mech_positive");
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", I have automatically set all of your Policies to the positive side, but you can flip any of them now with these buttons.");
            ButtonHelperHeroes.offerOlradinHeroFlips(game, player);
            ButtonHelperHeroes.offerOlradinHeroFlips(game, player);
            ButtonHelperHeroes.offerOlradinHeroFlips(game, player);
        }
        if (player.hasAbility("oracle_ai")) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " you may peek at the next objective in your `#cards-info` thread (by your promissory note). "
                            + "This holds true for anyone with _Read the Fates_. Don't do this until after secret objectives are dealt and discarded.");
        }
        CardsInfoService.sendVariousAdditionalButtons(game, player);

        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "Player: " + player.getRepresentation() + " has been set up");
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
        if ("d11".equalsIgnoreCase(hsTile)) {
            AddTokenCommand.addToken(event, tile, Constants.FRONTIER, game);
        }
        if ("true".equalsIgnoreCase(game.getStoredValue("removeSupports"))) {
            player.removeOwnedPromissoryNoteByID(player.getColor() + "_sftt");
            player.removePromissoryNote(player.getColor() + "_sftt");
        }

        if (game.isThundersEdge() && player.getFaction().equalsIgnoreCase("crimson")) {
            BreakthroughCommandHelper.unlockBreakthrough(game, player);
        }

        if (game.isRapidMobilizationMode()) {
            Tile tile2 = player.getHomeSystemTile();
            if (tile2 != null
                    && !FoWHelper.getAdjacentTilesAndNotThisTile(game, tile2.getPosition(), player, false)
                            .isEmpty()) {
                ButtonHelper.rapidMobilization(game, null, "rapid_" + player.getFaction());
            } else {
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.green(
                        "rapidMobilization_" + player.getFaction(),
                        "Do Rapid Mobilization For " + player.getFaction()));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        "You cannot do Rapid Mobilization now, but once the map is setup, you can click this button to do so.",
                        buttons);
            }
        }
    }

    public static void setupExtraFactionTiles(Game game, Player player, String faction, String positionHS, Tile tile) {
        // HANDLE GHOSTS' HOME SYSTEM LOCATION
        if ("ghost".equals(faction) || "miltymod_ghost".equals(faction)) {
            tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
            String pos = "tr";
            if ("307".equalsIgnoreCase(positionHS) || "310".equalsIgnoreCase(positionHS)) {
                pos = "br";
            }
            if ("313".equalsIgnoreCase(positionHS) || "316".equalsIgnoreCase(positionHS)) {
                pos = "bl";
            }
            tile = new Tile("51", pos);
            game.setTile(tile);
            player.setHomeSystemPosition(pos);
            if (game.isTwilightsFallMode()) {
                player.addAbility("song_of_something");
            }
        }

        // HANDLE Crimson' HOME SYSTEM LOCATION
        if ("crimson".equals(faction)) {
            tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
            if (!game.isTwilightsFallMode()) {
                tile.addToken(Constants.TOKEN_BREACH_INACTIVE, Constants.SPACE);
            } else {
                player.addAbility("song_of_something");
            }
            String pos = "tr";
            if ("307".equalsIgnoreCase(positionHS) || "310".equalsIgnoreCase(positionHS)) {
                pos = "br";
            }
            if ("313".equalsIgnoreCase(positionHS) || "316".equalsIgnoreCase(positionHS)) {
                pos = "bl";
            }
            if (game.getTileByPosition(pos) != null) {
                if (pos.equalsIgnoreCase("tr")) {
                    pos = "br";
                } else {
                    pos = "tr";
                }
            }
            tile = new Tile("118", pos);
            game.setTile(tile);
            player.setHomeSystemPosition(pos);
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
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), "Unit: " + unit + " is not valid and not supported.");
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
