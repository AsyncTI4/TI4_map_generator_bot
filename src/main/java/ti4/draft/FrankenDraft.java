package ti4.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ti4.draft.items.AbilityDraftItem;
import ti4.draft.items.AgentDraftItem;
import ti4.draft.items.BlueTileDraftItem;
import ti4.draft.items.CommanderDraftItem;
import ti4.draft.items.CommoditiesDraftItem;
import ti4.draft.items.FlagshipDraftItem;
import ti4.draft.items.HeroDraftItem;
import ti4.draft.items.HomeSystemDraftItem;
import ti4.draft.items.MechDraftItem;
import ti4.draft.items.PNDraftItem;
import ti4.draft.items.RedTileDraftItem;
import ti4.draft.items.SpeakerOrderDraftItem;
import ti4.draft.items.StartingFleetDraftItem;
import ti4.draft.items.StartingTechDraftItem;
import ti4.draft.items.TechDraftItem;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.BotLogger;
import ti4.model.FactionModel;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftManager;

public class FrankenDraft extends BagDraft {
    public FrankenDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getItemLimitForCategory(DraftItem.Category category) {
        int limit = 0;
        switch (category) {
            case ABILITY, BLUETILE -> limit = 3;
            case TECH, REDTILE, STARTINGFLEET, STARTINGTECH, HOMESYSTEM, PN, COMMODITIES, FLAGSHIP, MECH, HERO, COMMANDER, AGENT -> limit = 2;
            case DRAFTORDER -> limit = 1;
        }
        return limit;
    }

    @Override
    public String getSaveString() {
        return "franken";
    }

    private static final String[] excludedFactions = { "lazax", "admins", "franken", "keleresm", "keleresx", "miltymod", "qulane", "neutral", "pharadn" };

    public static List<FactionModel> getDraftableFactionsForGame(Game game) {
        List<FactionModel> factionSet = getAllFrankenLegalFactions();
        String[] results = game.getStoredValue("bannedFactions").split("finSep");
        if (!game.isDiscordantStarsMode()) {
            factionSet.removeIf(factionModel -> factionModel.getSource().isDs() && !factionModel.getSource().isPok());
        }
        factionSet.removeIf(factionModel -> contains(results, factionModel.getAlias()));

        return factionSet;
    }

    public static boolean contains(String[] array, String target) {
        for (String str : array) {
            if (str.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    public static List<FactionModel> getAllFrankenLegalFactions() {
        List<FactionModel> factionSet = Mapper.getFactions();
        factionSet.removeIf((FactionModel model) -> {
            if (model.getSource().isPok() || model.getSource().isDs()) {
                for (String excludedFaction : excludedFactions) {
                    if (model.getAlias().contains(excludedFaction)) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        });
        return factionSet;
    }

    @Override
    public List<DraftBag> generateBags(Game game) {
        Map<DraftItem.Category, List<DraftItem>> allDraftableItems = new HashMap<>();
        List<FactionModel> allDraftableFactions = getDraftableFactionsForGame(game);

        allDraftableItems.put(DraftItem.Category.ABILITY, AbilityDraftItem.buildAllDraftableItems(allDraftableFactions, game));
        allDraftableItems.put(DraftItem.Category.TECH, TechDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftItem.Category.AGENT, AgentDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftItem.Category.COMMANDER, CommanderDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftItem.Category.HERO, HeroDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftItem.Category.COMMODITIES, CommoditiesDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftItem.Category.FLAGSHIP, FlagshipDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftItem.Category.MECH, MechDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftItem.Category.HOMESYSTEM, HomeSystemDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftItem.Category.PN, PNDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftItem.Category.STARTINGFLEET, StartingFleetDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftItem.Category.STARTINGTECH, StartingTechDraftItem.buildAllDraftableItems(allDraftableFactions));

        allDraftableItems.put(DraftItem.Category.DRAFTORDER, SpeakerOrderDraftItem.buildAllDraftableItems(game));

        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        draftManager.clear();
        MiltyDraftHelper.initDraftTiles(draftManager, game);
        allDraftableItems.put(DraftItem.Category.REDTILE, RedTileDraftItem.buildAllDraftableItems(draftManager));
        allDraftableItems.put(DraftItem.Category.BLUETILE, BlueTileDraftItem.buildAllDraftableItems(draftManager));

        List<DraftBag> bags = new ArrayList<>();

        for (int i = 0; i < game.getRealPlayers().size(); i++) {
            DraftBag bag = new DraftBag();

            // Walk through each type of draftable...
            for (Map.Entry<DraftItem.Category, List<DraftItem>> draftableCollection : allDraftableItems.entrySet()) {
                DraftItem.Category category = draftableCollection.getKey();
                int categoryLimit = getItemLimitForCategory(category);
                // ... and pull out the appropriate number of items from its collection...
                for (int j = 0; j < categoryLimit; j++) {
                    // ... and add it to the player's bag.
                    if (!draftableCollection.getValue().isEmpty()) {
                        bag.Contents.add(draftableCollection.getValue().removeFirst());
                    } else {
                        BotLogger.warning(new BotLogger.LogMessageOrigin(game), "Game: `" + game.getName() + "` error - empty franken draftableCollection: " + category.name());
                    }
                }
            }

            bags.add(bag);
        }

        return bags;
    }

    @Override
    public int getBagSize() {
        return 31;
    }
}
