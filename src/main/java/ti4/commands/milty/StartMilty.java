package ti4.commands.milty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class StartMilty extends MiltySubcommandData {

    public static final int SLICE_GENERATION_CYCLES = 100;

    private boolean anomalies_can_touch;

    public StartMilty() {
        super(Constants.START, "Start Milty Draft");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SLICE_COUNT, "Slice Count").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.FACTION_COUNT, "Faction Count").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ANOMALIES_CAN_TOUCH, "Anomalies can touch").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        //if (!game.isTestBetaFeaturesMode()) {
        //    MessageHelper.sendMessageToChannel(event.getChannel(), "Milty Draft in this bot is incomplete.\nEnable access by running `/game setup beta_test_mode: true`\nMost folks use [this website](https://milty.shenanigans.be/) to do the Milty Draft and import the TTPG string with `/map add_tile_list`");
        //    return;
        //}

        OptionMapping sliceOption = event.getOption(Constants.SLICE_COUNT);
        int sliceCount = game.getPlayerCountForMap() + 2;
        if (sliceOption != null) {
            sliceCount = sliceOption.getAsInt();
        }
        if (sliceCount > 9) {
            String limit9slice = "Milty draft in this bot does not support more than 9 slices yet.";
            MessageHelper.sendMessageToChannel(event.getChannel(), limit9slice);
            return;
        }
        if (game.getPlayers().values().size() > 8) {
            String limit8p = "Milty draft in this bot does not support more than 8 players yet.";
            MessageHelper.sendMessageToChannel(event.getChannel(), limit8p);
            return;
        }

        int factionCount = game.getPlayerCountForMap() + 2;
        OptionMapping factionOption = event.getOption(Constants.FACTION_COUNT);
        if (factionOption != null) {
            factionCount = factionOption.getAsInt();
        }
        if (factionCount > 25) {
            factionCount = 25;
        }

        List<String> factions = new ArrayList<>(Mapper.getFactions().stream()
                .filter(f -> f.getSource().isPok())
                .map(f -> f.getAlias()).toList());
        List<String> factionDraft = createFactionDraft(factionCount, factions);

        anomalies_can_touch = false;
        OptionMapping anomaliesCanTouchOption = event.getOption(Constants.ANOMALIES_CAN_TOUCH);
        if (anomaliesCanTouchOption != null) {
            anomalies_can_touch = anomaliesCanTouchOption.getAsBoolean();
        }

        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        draftManager.init();
        draftManager.setFactionDraft(factionDraft);
        initDraftOrder(draftManager, game);

        boolean slicesCreated = generateSlices(sliceCount, draftManager);
        if (!slicesCreated) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Did not find correct slices, check settings");
        } else {
            // Kick it off with a bang!
            MiltyDraftHelper.generateAndPostSlices(game);
            draftManager.serveCurrentPlayer(game);
        }
    }

    private static void initDraftOrder(MiltyDraftManager draftManager, Game activeGame) {
        List<String> players = new ArrayList<>(activeGame.getPlayers().values().stream().map(p -> p.getUserID()).toList());
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
        factions.remove("lazax");
        Collections.shuffle(factions);
        List<String> factionDraft = new ArrayList<>();
        for (int i = 0; i < factionCount; i++) {
            factionDraft.add(factions.get(i));
        }
        return factionDraft;
    }

    private boolean generateSlices(int sliceCount, MiltyDraftManager draftManager) {
        boolean slicesCreated = false;
        int i = 0;
        while (!slicesCreated && i < 1000) {
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

                if (!anomalies_can_touch && needToCheckAnomalies) {
                    int emergencyIndex = 0;
                    while (emergencyIndex < 100) {

                        boolean left = tiles.get(0).getTile().isAnomaly();
                        boolean front = tiles.get(1).getTile().isAnomaly();
                        boolean right = tiles.get(2).getTile().isAnomaly();
                        boolean equi = tiles.get(3).getTile().isAnomaly();
                        boolean meca = tiles.get(4).getTile().isAnomaly();
                        if (!((front && (left || right || equi || meca)) || (equi && (meca || left)))) {
                            break;
                        }
                        Collections.shuffle(tiles);
                        emergencyIndex++;
                    }
                }
                miltyDraftSlice.setLeft(tiles.get(0));
                miltyDraftSlice.setFront(tiles.get(1));
                miltyDraftSlice.setRight(tiles.get(2));
                miltyDraftSlice.setEquidistant(tiles.get(3));
                miltyDraftSlice.setFarFront(tiles.get(4));

                // CHECK IF SLICES ARE OK HERE -------------------------------
                double shenInf = miltyDraftSlice.getOptimalInf() * 1.0 + miltyDraftSlice.getOptimalFlex() / 2.0;
                double shenRes = miltyDraftSlice.getOptimalRes() * 1.0 + miltyDraftSlice.getOptimalFlex() / 2.0;
                int totalOptimal = miltyDraftSlice.getOptimalTotalValue();
                if (shenInf < 4.0 || shenRes < 2.5 || totalOptimal < 9 || totalOptimal > 13) {
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
        return slicesCreated;
    }

}
