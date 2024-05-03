package ti4.commands.milty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;

public class StartMilty extends MiltySubcommandData {

    public static final int SLICE_GENERATION_CYCLES = 1000;

    public StartMilty() {
        super(Constants.START, "Start Milty Draft");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SLICE_COUNT, "Slice Count").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.FACTION_COUNT, "Faction Count").setRequired(false).setRequiredRange(1, 25));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ANOMALIES_CAN_TOUCH, "Anomalies can touch").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_DS_FACTIONS, "Include Discordant Stars Factions").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_DS_TILES, "Include Uncharted Space Tiles (ds)").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.USE_MAP_TEMPLATE, "Use map template").setAutoComplete(true).setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        // Map Template ---------------------------------------------------------------------------
        MapTemplateModel template = getMapTemplateFromOption(event, game);
        if (template == null) return; // we have already sent an error message

        // Sources --------------------------------------------------------------------------------
        List<ComponentSource> tileSources = new ArrayList<>();
        tileSources.add(ComponentSource.base);
        tileSources.add(ComponentSource.pok);
        tileSources.add(ComponentSource.codex1);
        tileSources.add(ComponentSource.codex2);
        tileSources.add(ComponentSource.codex3);
        List<ComponentSource> factionSources = new ArrayList<>(tileSources);
        OptionMapping includeDsTilesOption = event.getOption(Constants.INCLUDE_DS_TILES);
        if (includeDsTilesOption != null && includeDsTilesOption.getAsBoolean()) {
            tileSources.add(ComponentSource.ds);
        }
        OptionMapping includeDsFactionsOption = event.getOption(Constants.INCLUDE_DS_FACTIONS);
        if (includeDsFactionsOption != null && includeDsFactionsOption.getAsBoolean()) {
            factionSources.add(ComponentSource.ds);
        }

        // Faction count & setup ------------------------------------------------------------------
        int factionCount = game.getPlayerCountForMap() + 3;
        OptionMapping factionOption = event.getOption(Constants.FACTION_COUNT);
        if (factionOption != null) {
            factionCount = factionOption.getAsInt();
        }
        if (factionCount > 25) factionCount = 25;

        List<String> factions = new ArrayList<>(Mapper.getFactions().stream()
            .filter(f -> factionSources.contains(f.getSource()))
            .map(f -> f.getAlias()).toList());
        List<String> factionDraft = createFactionDraft(factionCount, factions);

        // Milty Draft Manager Setup --------------------------------------------------------------
        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        draftManager.init(tileSources);
        draftManager.setFactionDraft(factionDraft);
        draftManager.setMapTemplate(template.getAlias());
        game.setMapTemplateID(template.getAlias());
        initDraftOrder(draftManager, game);

        // Slice count ----------------------------------------------------------------------------
        OptionMapping sliceOption = event.getOption(Constants.SLICE_COUNT);
        int presliceCount = game.getPlayerCountForMap() + 1;
        if (sliceOption != null) presliceCount = sliceOption.getAsInt();
        int sliceCount = presliceCount;

        int redTiles = draftManager.getRed().size();
        int blueTiles = draftManager.getBlue().size();
        int maxSlices = Math.min(redTiles / 2, blueTiles / 3);
        if (sliceCount > maxSlices) {
            String msg = "Milty draft in this bot does not support " + sliceCount + " slices. You can enable DS to allow building additional slices";
            msg += "\n> The options you have selected enable a maximum of `" + maxSlices + "` slices. [" + blueTiles + "blue/" + redTiles + "red]";
            MessageHelper.sendMessageToChannel(event.getChannel(), msg);
            return;
        }

        // Slice generation -----------------------------------------------------------------------
        boolean anomaliesCanTouch = false;
        OptionMapping anomaliesCanTouchOption = event.getOption(Constants.ANOMALIES_CAN_TOUCH);
        if (anomaliesCanTouchOption != null) {
            anomaliesCanTouch = anomaliesCanTouchOption.getAsBoolean();
        }
        boolean anomalies = anomaliesCanTouch;

        String startMsg = "## Generating the milty draft!!";
        startMsg += "\n - Also clearing out any tiles that may have already been on the map so that the draft will fill in tiles properly.";
        if (sliceCount == maxSlices) {
            startMsg += "\n - *You asked for the max number of slices, so this may take several seconds*";
        }

        game.clearTileMap();
        try {
            MiltyDraftHelper.buildPartialMap(game, event);
        } catch (Exception e) {
            //asdf
        }

        event.getChannel().sendMessage(startMsg).queue((ignore) -> {
            boolean slicesCreated = generateSlices(event, sliceCount, draftManager, anomalies);
            if (!slicesCreated) {
                String msg = "Generating slices was too hard so I gave up.... Please try again.";
                if (sliceCount == maxSlices) {
                    msg += "\n*...and maybe consider asking for fewer slices*";
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), msg);
            } else {
                // Kick it off with a bang!
                draftManager.repostDraftInformation(game);
                GameSaveLoadManager.saveMap(game, event);
            }
        });
    }

    private static void initDraftOrder(MiltyDraftManager draftManager, Game game) {
        List<String> players = new ArrayList<>(game.getPlayers().values().stream().map(p -> p.getUserID()).toList());
        Collections.shuffle(players);

        List<String> playersReversed = new ArrayList<>(players);
        Collections.reverse(playersReversed);

        List<String> draftOrder = new ArrayList<>(players);
        draftOrder.addAll(playersReversed);
        draftOrder.addAll(players);

        draftManager.setDraftOrder(draftOrder);
        draftManager.setPlayers(players);
    }

    private static List<String> createFactionDraft(int factionCount, List<String> factions) {
        boolean hasKeleres = false;
        boolean hasMentak = false;
        boolean hasXxcha = false;
        boolean hasArgent = false;
        factions.remove("lazax");
        Collections.shuffle(factions);
        List<String> factionDraft = new ArrayList<>();
        int i = 0;
        while (factionDraft.size() < factionCount) {
            String f = factions.get(i);
            i++;
            if (List.of("keleresa", "keleresm", "keleresx").contains(f)) {
                if (hasKeleres) continue;
                hasKeleres = true;
            }
            if (List.of("keleresa", "argent").contains(f)) {
                if (hasArgent) continue;
                hasArgent = true;
            }
            if (List.of("keleresm", "mentak").contains(f)) {
                if (hasMentak) continue;
                hasMentak = true;
            }
            if (List.of("keleresx", "xxcha").contains(f)) {
                if (hasXxcha) continue;
                hasXxcha = true;
            }
            factionDraft.add(f);
        }
        return factionDraft;
    }

    private static boolean generateSlices(GenericInteractionCreateEvent event, int sliceCount, MiltyDraftManager draftManager, boolean anomaliesCanTouch) {
        long startTime = System.nanoTime();
        long quitDiff = 20l * 1000l * 1000l * 1000l;
        long attempts = 1000000l;

        boolean slicesCreated = false;
        int i = 0;
        Map<String, Integer> reasons = new HashMap<>();
        final String alpha = "alphas", beta = "betas", value = "value";
        reasons.put(alpha, 0);
        reasons.put(beta, 0);
        reasons.put(value, 0);

        while (!slicesCreated) {
            long elapTime = System.nanoTime() - startTime;
            if (elapTime > quitDiff && i > attempts) {
                break;
            }
            draftManager.clearSlices();

            List<MiltyDraftTile> blue = draftManager.getBlue();
            List<MiltyDraftTile> red = draftManager.getRed();

            Collections.shuffle(blue);
            Collections.shuffle(red);

            for (int sliceNum = 1; sliceNum <= sliceCount; sliceNum++) {
                MiltyDraftSlice miltyDraftSlice = new MiltyDraftSlice();
                List<MiltyDraftTile> tiles = new ArrayList<>();
                tiles.add(blue.remove(0));
                tiles.add(blue.remove(0));
                tiles.add(blue.remove(0));
                MiltyDraftTile red1 = red.remove(0);
                MiltyDraftTile red2 = red.remove(0);
                tiles.add(red1);
                tiles.add(red2);
                boolean needToCheckAnomalies = red1.getTierList() == TierList.anomaly && red2.getTierList() == TierList.anomaly;
                Collections.shuffle(tiles);

                if (!anomaliesCanTouch && needToCheckAnomalies) {
                    int turns = 0;
                    while (turns < 5) {
                        boolean left = tiles.get(0).getTile().isAnomaly();
                        boolean front = tiles.get(1).getTile().isAnomaly();
                        boolean right = tiles.get(2).getTile().isAnomaly();
                        boolean equi = tiles.get(3).getTile().isAnomaly();
                        boolean meca = tiles.get(4).getTile().isAnomaly();
                        if (!((front && (left || right || equi || meca)) || (equi && (meca || left)))) {
                            break;
                        }
                        //rotating the array will ALWAYS find an acceptable tile layout within 2 turns
                        Collections.rotate(tiles, 1);
                        turns++;
                    }
                }
                miltyDraftSlice.setLeft(tiles.get(0));
                miltyDraftSlice.setFront(tiles.get(1));
                miltyDraftSlice.setRight(tiles.get(2));
                miltyDraftSlice.setEquidistant(tiles.get(3));
                miltyDraftSlice.setFarFront(tiles.get(4));

                // CHECK IF SLICES ARE OK-ish HERE -------------------------------
                int optInf = miltyDraftSlice.getOptimalInf();
                int optRes = miltyDraftSlice.getOptimalRes();
                int totalOptimal = miltyDraftSlice.getOptimalTotalValue();
                if (optInf < 3 || optRes < 2 || totalOptimal < 9 || totalOptimal > 13) {
                    reasons.put(value, reasons.get(value) + 1);
                    break;
                }

                // if the slice has 2 alphas, or 2 betas, throw it out
                if (miltyDraftSlice.getTiles().stream().filter(t -> t.isHasAlphaWH()).count() > 1) {
                    reasons.put(alpha, reasons.get(alpha) + 1);
                    break;
                }
                if (miltyDraftSlice.getTiles().stream().filter(t -> t.isHasBetaWH()).count() > 1) {
                    reasons.put(beta, reasons.get(beta) + 1);
                    break;
                }

                String sliceName = Character.toString(sliceNum - 1 + 'A');
                miltyDraftSlice.setName(sliceName);
                draftManager.addSlice(miltyDraftSlice);
            }

            if (draftManager.getSlices().size() == sliceCount) {
                slicesCreated = true;
            }
            i++;
        }
        if (!slicesCreated) {
            draftManager.clear();
        }

        long elapsed = System.nanoTime() - startTime;
        if (!slicesCreated || elapsed >= 2000000000l) {
            StringBuilder sb = new StringBuilder();
            sb.append("Milty draft took a while ").append(Constants.jazzPing()).append(", take a look:\n");
            sb.append("`        Elapsed time:` " + Helper.getTimeRepresentationNanoSeconds(elapsed)).append("\n");
            sb.append("`           Quit time:` " + Helper.getTimeRepresentationNanoSeconds(quitDiff)).append("\n");
            sb.append("`    Number of cycles:` " + i);
            sb.append("`          alpha fail:` " + reasons.get(alpha));
            sb.append("`           beta fail:` " + reasons.get(beta));
            sb.append("`          value fail:` " + reasons.get(value));
            BotLogger.log(event, sb.toString());
        }
        return slicesCreated;
    }

    private static MapTemplateModel getMapTemplateFromOption(SlashCommandInteractionEvent event, Game game) {
        int players = game.getPlayers().values().size();
        List<MapTemplateModel> allTemplates = Mapper.getMapTemplates();
        List<MapTemplateModel> validTemplates = Mapper.getMapTemplatesForPlayerCount(players);
        MapTemplateModel defaultTemplate = Mapper.getDefaultMapTemplateForPlayerCount(players);

        if (validTemplates.size() == 0) {
            String msg = "Milty draft in this bot does not know about any map layouts that support " + players + " player(s) yet.";
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
}
