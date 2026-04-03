package ti4.draft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import ti4.draft.items.AbilityDraftItem;
import ti4.draft.items.AgentDraftItem;
import ti4.draft.items.BlueTileDraftItem;
import ti4.draft.items.BreakthroughDraftItem;
import ti4.draft.items.CommanderDraftItem;
import ti4.draft.items.CommoditiesDraftItem;
import ti4.draft.items.FlagshipDraftItem;
import ti4.draft.items.HeroDraftItem;
import ti4.draft.items.HomeSystemDraftItem;
import ti4.draft.items.MahactKingDraftItem;
import ti4.draft.items.MechDraftItem;
import ti4.draft.items.PNDraftItem;
import ti4.draft.items.RedTileDraftItem;
import ti4.draft.items.SpeakerOrderDraftItem;
import ti4.draft.items.StartingFleetDraftItem;
import ti4.draft.items.StartingTechDraftItem;
import ti4.draft.items.TechDraftItem;
import ti4.draft.items.UnitDraftItem;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftManager;

public class FrankenDraft extends BagDraft {

    public FrankenDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getItemLimitForCategory(DraftCategory category) {
        return switch (category) {
            case ABILITY, BLUETILE -> 3;
            case TECH, REDTILE, STARTINGFLEET -> 2;
            case STARTINGTECH, HOMESYSTEM, PN -> 2;
            case COMMODITIES, FLAGSHIP, MECH -> 2;
            case HERO, COMMANDER, AGENT, BREAKTHROUGH -> 2;
            case DRAFTORDER -> 1;
            case UNIT, PLOT, MAHACTKING -> 0;
        };
    }

    @Override
    public int getKeptItemLimitForCategory(DraftCategory category) {
        return switch (category) {
            case ABILITY, BLUETILE -> 3;
            case TECH, REDTILE -> 2;
            case STARTINGTECH, HOMESYSTEM, PN -> 1;
            case COMMODITIES, FLAGSHIP, MECH -> 1;
            case HERO, COMMANDER, AGENT, BREAKTHROUGH -> 1;
            case DRAFTORDER, STARTINGFLEET -> 1;
            case UNIT, PLOT, MAHACTKING -> 0;
        };
    }

    public static int getItemLimitForCategory(DraftCategory category, Game game) {
        if (!game.getStoredValue("frankenLimit" + category).isEmpty()) {
            return Integer.parseInt(game.getStoredValue("frankenLimit" + category));
        } else {
            return game.getActiveBagDraft().getItemLimitForCategory(category);
        }
    }

    @Override
    public String getSaveString() {
        return "franken";
    }

    private static final String[] excludedFactions = {
        "lazax",
        "admins",
        "franken",
        "keleresm",
        "keleresx",
        "miltymod",
        "qulane",
        "neutral",
        "kaltrim",
        "xin",
        "sarcosa"
    };

    private static List<FactionModel> getDraftableFactionsForGame(Game game) {
        List<FactionModel> factionSet = getAllFrankenLegalFactions(game);
        String[] results = PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue("bannedFactions"));
        if (!game.isDiscordantStarsMode()) {
            factionSet.removeIf(factionModel ->
                    factionModel.getSource().isDs() && !factionModel.getSource().isTe());
        }
        factionSet.removeIf(factionModel -> contains(results, factionModel.getAlias()));

        return factionSet;
    }

    private static boolean contains(String[] array, String target) {
        for (String str : array) {
            if (str.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    public static List<FactionModel> getAllFrankenLegalFactions(@Nullable Game game) {
        List<FactionModel> factionSet = Mapper.getFactionsValues();
        List<String> excludedFactions = Arrays.asList(FrankenDraft.excludedFactions);

        factionSet.removeIf((FactionModel model) -> {
            if (excludedFactions.contains(model.getAlias())) {
                return true;
            }
            if ((game == null || game.isThundersEdge()) && model.getSource().isTe()) {
                return false;
            }
            if ((game == null || game.isDiscordantStarsMode())
                    && model.getSource().isDs()) {
                return false;
            }
            return !model.getSource().isPok();
        });
        return factionSet;
    }

    @Override
    public List<DraftBag> generateBags(Game game) {
        Map<DraftCategory, List<DraftItem>> allDraftableItems = new HashMap<>();
        List<FactionModel> allDraftableFactions = getDraftableFactionsForGame(game);

        var abilities = AbilityDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.ABILITY, abilities);

        var techs = TechDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.TECH, techs);

        var bts = BreakthroughDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.BREAKTHROUGH, bts);

        var agents = AgentDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.AGENT, agents);

        var commanders = CommanderDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.COMMANDER, commanders);

        var heroes = HeroDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.HERO, heroes);

        var comms = CommoditiesDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.COMMODITIES, comms);

        var flags = FlagshipDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.FLAGSHIP, flags);

        var mechs = MechDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.MECH, mechs);

        var homes = HomeSystemDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.HOMESYSTEM, homes);

        var pns = PNDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.PN, pns);

        var fleet = StartingFleetDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.STARTINGFLEET, fleet);

        var stTechs = StartingTechDraftItem.buildAllDraftableItems(allDraftableFactions, game);
        allDraftableItems.put(DraftCategory.STARTINGTECH, stTechs);

        var units = UnitDraftItem.buildAllDraftableItems();
        allDraftableItems.put(DraftCategory.UNIT, units);

        var kings = MahactKingDraftItem.buildAllDraftableItems();
        allDraftableItems.put(DraftCategory.MAHACTKING, kings);

        var positions = SpeakerOrderDraftItem.buildAllDraftableItems(game);
        allDraftableItems.put(DraftCategory.DRAFTORDER, positions);

        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        draftManager.clear();
        MiltyDraftHelper.initDraftTiles(draftManager, game);
        allDraftableItems.put(DraftCategory.REDTILE, RedTileDraftItem.buildAllDraftableItems(draftManager, game));
        allDraftableItems.put(DraftCategory.BLUETILE, BlueTileDraftItem.buildAllDraftableItems(draftManager, game));

        List<DraftBag> bags = new ArrayList<>();

        Map<DraftCategory, Integer> missingItems = new HashMap<>();
        for (int i = 0; i < game.getRealPlayers().size(); i++) {
            DraftBag bag = new DraftBag();

            // Walk through each type of draftable...
            for (Map.Entry<DraftCategory, List<DraftItem>> draftableCollection : allDraftableItems.entrySet()) {
                DraftCategory category = draftableCollection.getKey();
                int categoryLimit = getItemLimitForCategory(category, game);
                // ... and pull out the appropriate number of items from its collection...
                for (int j = 0; j < categoryLimit; j++) {
                    // ... and add it to the player's bag.
                    if (!draftableCollection.getValue().isEmpty()) {
                        bag.Contents.add(draftableCollection.getValue().removeFirst());
                    } else {
                        missingItems.compute(category, (c, x) -> x == null ? 1 : x + 1);
                    }
                }
            }

            if (!missingItems.isEmpty()) {
                StringBuilder issue = new StringBuilder(
                        game.getPing() + " an issue was encountered while building the franken draft.");
                issue.append("\nOne or more bags are missing components.");
                for (var e : missingItems.entrySet()) {
                    issue.append("\n> ")
                            .append(e.getKey().toString())
                            .append(" is missing ")
                            .append(e.getValue())
                            .append(" components.");
                }
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), issue.toString());
                return null;
            }
            bags.add(bag);
        }

        return bags;
    }

    @Override
    public int getBagSize() {
        return 33;
    }
}
