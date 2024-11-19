package ti4.commands2.milty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import lombok.Data;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.helpers.Constants;
import ti4.helpers.GlobalSettings;
import ti4.helpers.Helper;
import ti4.helpers.TIGLHelper;
import ti4.helpers.settingsFramework.menus.GameSettings;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.helpers.settingsFramework.menus.PlayerFactionSettings;
import ti4.helpers.settingsFramework.menus.SliceGenerationSettings;
import ti4.helpers.settingsFramework.menus.SourceSettings;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;

public class StartMilty extends GameStateSubcommand {

    public StartMilty() {
        super(Constants.QUICKSTART, "Start Milty Draft with default settings", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SLICE_COUNT, "Slice Count (default = players + 1)").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.FACTION_COUNT, "Faction Count (default = players + 1)").setRequired(false).setRequiredRange(1, 25));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_DS_FACTIONS, "Include Discordant Stars Factions").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_DS_TILES, "Include Uncharted Space Tiles (ds)").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftSpec specs = new DraftSpec(game);

        // Map Template ---------------------------------------------------------------------------
        MapTemplateModel template = getMapTemplateFromOption(event, game);
        if (template == null) return; // we have already sent an error message
        specs.template = template;

        // Sources (defaults already accounted for) -----------------------------------------------
        OptionMapping includeDsTilesOption = event.getOption(Constants.INCLUDE_DS_TILES);
        if (includeDsTilesOption != null && includeDsTilesOption.getAsBoolean())
            specs.tileSources.add(ComponentSource.ds);
        OptionMapping includeDsFactionsOption = event.getOption(Constants.INCLUDE_DS_FACTIONS);
        if (includeDsFactionsOption != null && includeDsFactionsOption.getAsBoolean())
            specs.factionSources.add(ComponentSource.ds);

        // Faction count & setup ------------------------------------------------------------------
        int factionCount = game.getPlayerCountForMap() + 1;
        OptionMapping factionOption = event.getOption(Constants.FACTION_COUNT);
        if (factionOption != null) {
            factionCount = factionOption.getAsInt();
        }
        if (factionCount > 25) factionCount = 25;
        specs.numFactions = factionCount;

        // Slice count ----------------------------------------------------------------------------
        OptionMapping sliceOption = event.getOption(Constants.SLICE_COUNT);
        int presliceCount = game.getPlayerCountForMap() + 1;
        if (sliceOption != null) presliceCount = sliceOption.getAsInt();
        specs.numSlices = presliceCount;

        boolean anomaliesCanTouch = false;
        OptionMapping anomaliesCanTouchOption = event.getOption(Constants.ANOMALIES_CAN_TOUCH);
        if (anomaliesCanTouchOption != null) {
            anomaliesCanTouch = anomaliesCanTouchOption.getAsBoolean();
        }
        specs.anomaliesCanTouch = anomaliesCanTouch;

        // Players ---
        specs.playerIDs = new ArrayList<>(game.getPlayerIDs());
        startFromSpecs(event, specs);
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

    private static String startFromSpecs(GenericInteractionCreateEvent event, DraftSpec specs) {
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
            //asdf
        }

        if (specs.presetSlices != null) {
            startMsg = "### You are using preset slices!!";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "### You are using preset slices!! Starting the draft right away!");
            specs.presetSlices.forEach(draftManager::addSlice);
            // Kick it off with a bang!
            draftManager.repostDraftInformation(game);
            GameSaveLoadManager.saveGame(game, event);
        } else {
            event.getMessageChannel().sendMessage(startMsg).queue((ignore) -> {
                boolean slicesCreated = generateSlices(event, draftManager, specs);
                if (!slicesCreated) {
                    String msg = "Generating slices was too hard so I gave up.... Please try again.";
                    if (specs.numSlices == maxSlices) {
                        msg += "\n*...and maybe consider asking for fewer slices*";
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                } else {
                    // Kick it off with a bang!
                    draftManager.repostDraftInformation(game);
                    GameSaveLoadManager.saveGame(game, event);
                    game.setPhaseOfGame("miltydraft");
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

    private static boolean generateSlices(GenericInteractionCreateEvent event, MiltyDraftManager draftManager, DraftSpec specs) {
        int sliceCount = specs.numSlices;
        boolean anomaliesCanTouch = specs.anomaliesCanTouch;

        MapTemplateModel mapTemplate = specs.template;
        int bluePerPlayer = mapTemplate.bluePerPlayer();
        int redPerPlayer = mapTemplate.redPerPlayer();

        List<List<Boolean>> adjMatrix = new ArrayList<>();
        List<String> tilePositions = mapTemplate.emulatedTiles();
        for (String pos1 : tilePositions) {
            List<Boolean> row = new ArrayList<>();
            List<String> adj = PositionMapper.getAdjacentTilePositions(pos1);
            for (String pos2 : tilePositions)
                row.add(adj.contains(pos2));
            adjMatrix.add(row);
        }

        boolean slicesCreated = false;
        int i = 0;
        Map<String, Integer> reasons = new HashMap<>();
        Function<String, Integer> addReason = reason -> reasons.put(reason, reasons.getOrDefault(reason, 0) + 1);

        List<MiltyDraftTile> allTiles = draftManager.getBlue();
        allTiles.addAll(draftManager.getRed());
        int totalWHs = allTiles.stream().filter(tile -> tile.isHasAlphaWH() || tile.isHasBetaWH() || tile.isHasOtherWH()).toList().size();
        int extraWHs = Math.min(totalWHs, sliceCount * 2);
        if (specs.playerIDs.size() == 1) extraWHs = 0; //disable the behavior if there's only 1 player
        if (specs.playerIDs.size() == 2) extraWHs = 4; //lessen the behavior if there's 2 players
        if (!specs.extraWHs) extraWHs = 0;

        List<MiltyDraftTile> blue = draftManager.getBlue();
        List<MiltyDraftTile> red = draftManager.getRed();

        long quitDiff = 60L * 1000L * 1000L * 1000L;
        long minAttempts = 1000000L;
        long startTime = System.nanoTime();
        while (!slicesCreated) {
            long elapTime = System.nanoTime() - startTime;
            if (elapTime > quitDiff && i > minAttempts) {
                break;
            }
            draftManager.clearSlices();
            Collections.shuffle(blue);
            Collections.shuffle(red);

            int legends = 0, whs = 0, blueIndex = 0, redIndex = 0;
            for (int sliceNum = 1; sliceNum <= sliceCount; sliceNum++) {
                MiltyDraftSlice miltyDraftSlice = new MiltyDraftSlice();
                List<MiltyDraftTile> tiles = new ArrayList<>();

                for (int blues = 0; blues < bluePerPlayer; blues++)
                    tiles.add(blue.get(blueIndex++));
                for (int reds = 0; reds < redPerPlayer; reds++)
                    tiles.add(red.get(redIndex++));

                Collections.shuffle(tiles);
                List<Integer> ints = new ArrayList<>();
                for (int k = 0; k < tiles.size(); k++)
                    if (tiles.get(k).getTierList() == TierList.anomaly)
                        ints.add(k + 1);
                if (!anomaliesCanTouch && ints.size() == 2) { // just skip this if there's more than 2 anomalies tbh
                    int turns = -4;
                    boolean tryagain = true;
                    while (tryagain && turns < mapTemplate.tilesPerPlayer()) {
                        tryagain = false;
                        for (int x : ints)
                            for (int y : ints)
                                if (x != y && adjMatrix.get(x).get(y)) {
                                    tryagain = true;
                                    break;
                                }
                        if (tryagain) {
                            Collections.rotate(tiles, 1);
                            if (turns == 0) Collections.shuffle(tiles);
                            ints.clear();
                            for (int k = 0; k < tiles.size(); k++)
                                if (tiles.get(k).getTierList() == TierList.anomaly)
                                    ints.add(k + 1);
                        }
                        turns++;
                    }
                }
                miltyDraftSlice.setTiles(tiles);

                // CHECK IF SLICES ARE OK-ish HERE -------------------------------
                int optInf = miltyDraftSlice.getOptimalInf();
                int optRes = miltyDraftSlice.getOptimalRes();
                int totalOptimal = miltyDraftSlice.getOptimalTotalValue();
                if (optInf < specs.getMinInf() || optRes < specs.getMinRes() || totalOptimal < specs.getMinTot() || totalOptimal > specs.getMaxTot()) {
                    addReason.apply("value");
                    break;
                }

                // if the slice has 2 alphas, or 2 betas, throw it out
                int alphaWHs = (int) miltyDraftSlice.getTiles().stream().filter(MiltyDraftTile::isHasAlphaWH).count();
                int betaWHs = (int) miltyDraftSlice.getTiles().stream().filter(MiltyDraftTile::isHasBetaWH).count();
                int otherWHs = (int) miltyDraftSlice.getTiles().stream().filter(MiltyDraftTile::isHasOtherWH).count();
                if (alphaWHs > 1) {
                    addReason.apply("alpha");
                    break;
                }
                if (betaWHs > 1) {
                    addReason.apply("beta");
                    break;
                }
                whs += alphaWHs + betaWHs + otherWHs;

                int sliceLegends = (int) miltyDraftSlice.getTiles().stream().filter(MiltyDraftTile::isLegendary).count();
                if (sliceLegends > 1) {
                    addReason.apply("legend");
                    break;
                }
                legends += sliceLegends;

                String sliceName = Character.toString('A' + sliceNum - 1);
                miltyDraftSlice.setName(sliceName);
                draftManager.addSlice(miltyDraftSlice);
            }

            if (draftManager.getSlices().size() == sliceCount) {
                if (legends > specs.maxLegend || legends < specs.minLegend) {
                    addReason.apply("legendTot");
                } else if (whs < extraWHs) {
                    addReason.apply("extrawh");
                } else {
                    slicesCreated = true;
                }
            }
            i++;
        }
        if (!slicesCreated) {
            draftManager.clear();
        }

        long elapsed = System.nanoTime() - startTime;
        boolean debug = GlobalSettings.getSetting(GlobalSettings.ImplementedSettings.DEBUG.toString(), Boolean.class, false);
        if (!slicesCreated || elapsed >= 10000000000L || debug) {
            StringBuilder sb = new StringBuilder();
            sb.append("Milty draft took a while... jazz, take a look:\n");
            sb.append("`        Elapsed time:` ").append(Helper.getTimeRepresentationNanoSeconds(elapsed)).append("\n");
            sb.append("`           Quit time:` ").append(Helper.getTimeRepresentationNanoSeconds(quitDiff)).append("\n");
            sb.append("`    Number of cycles:` ").append(i).append("\n");
            for (Entry<String, Integer> reason : reasons.entrySet()) {
                sb.append("`").append(Helper.leftpad(reason.getKey(), 15)).append(" fail:` ").append(reason.getValue()).append("\n");
            }
            BotLogger.log(event, sb.toString());
        }
        return slicesCreated;
    }

    private static MapTemplateModel getMapTemplateFromOption(SlashCommandInteractionEvent event, Game game) {
        int players = game.getPlayers().values().size();
        List<MapTemplateModel> allTemplates = Mapper.getMapTemplates();
        List<MapTemplateModel> validTemplates = Mapper.getMapTemplatesForPlayerCount(players);
        MapTemplateModel defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(players);

        if (validTemplates.isEmpty()) {
            String msg = "Milty draft in this bot does not know about any map layouts that support " + players + " player" + (players == 1 ? "" : "s") + " yet.";
            MessageHelper.sendMessageToChannel(event.getChannel(), msg);
            return null;
        }

        MapTemplateModel useTemplate = null;
        String templateName = null;
        OptionMapping templateOption = event.getOption(Constants.USE_MAP_TEMPLATE);
        if (templateOption != null) {
            templateName = templateOption.getAsString();
        }
        if (templateName != null) {
            for (MapTemplateModel model : allTemplates) {
                if (model.getAlias().equals(templateName)) {
                    useTemplate = model;
                }
            }
        } else {
            useTemplate = defaultTemplate;
        }

        if (useTemplate == null) {
            String msg = "There is not a default map layout defined for this player count. Specify map template in options.";
            MessageHelper.sendMessageToChannel(event.getChannel(), msg);
            return null;
        }
        return useTemplate;
    }

    @Data
    private static class DraftSpec {
        Game game;
        List<String> playerIDs, bannedFactions, priorityFactions, playerDraftOrder;
        MapTemplateModel template;
        List<ComponentSource> tileSources, factionSources;
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
            tileSources.add(ComponentSource.base);
            tileSources.add(ComponentSource.pok);
            tileSources.add(ComponentSource.codex1);
            tileSources.add(ComponentSource.codex2);
            tileSources.add(ComponentSource.codex3);
            factionSources = new ArrayList<>(tileSources);
        }
    }
}
