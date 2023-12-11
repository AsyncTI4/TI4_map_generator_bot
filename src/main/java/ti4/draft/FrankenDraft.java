package ti4.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.milty.MiltyDraftTile;
import ti4.commands.milty.StartMilty;
import ti4.draft.items.*;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;

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

    private static void filterUndraftablesAndShuffle(List<DraftItem> items, DraftItem.Category listCategory) {
        Map<String, DraftErrataModel> frankenErrata = Mapper.getFrankenErrata();
        items.removeIf((DraftItem item) -> frankenErrata.containsKey(item.getAlias()) && frankenErrata.get(item.getAlias()).Undraftable);
        items.addAll(DraftItem.GetAlwaysIncludeItems(listCategory));
        Collections.shuffle(items);
    }


    private static final String[] excludedFactions = { "lazax", "admins", "franken", "keleresm", "keleresx", "miltymod", "qulane" };

    public static List<String> getAllFactionIds(Game activeGame) {
        List<FactionModel> factionSet = Mapper.getFactions();
        List<String> factionIds = new ArrayList<>();
        factionSet.forEach((FactionModel model) -> {
            if (model.getSource().isPok() || (model.getSource().isDs() && activeGame.isDiscordantStarsMode())) {
                for (String excludedFaction : excludedFactions) {
                    if (model.getAlias().contains(excludedFaction)) {
                        return;
                    }
                }
                factionIds.add(model.getAlias());
            }
        });
        return factionIds;
    }

    public static List<FactionModel> getAllDraftableFactions(Game activeGame) {
        List<FactionModel> factionSet = Mapper.getFactions();
        factionSet.removeIf((FactionModel model) -> {
            if (model.getSource().isPok() || (model.getSource().isDs() && activeGame.isDiscordantStarsMode())){
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


    public static List<DraftItem> buildTileSet(MiltyDraftManager draftManager, boolean blue) {
        List<DraftItem> allItems = new ArrayList<>();
        List<MiltyDraftTile> allTiles;
        if (blue) {
            allTiles = draftManager.getHigh();
            allTiles.addAll(draftManager.getMid());
            allTiles.addAll(draftManager.getLow());
        } else {
            allTiles = draftManager.getRed();
        }
        DraftItem.Category category = blue ? DraftItem.Category.BLUETILE : DraftItem.Category.REDTILE;
        for (MiltyDraftTile tile : allTiles) {
            allItems.add(DraftItem.Generate(category,
                tile.getTile().getTileID()));
        }
        filterUndraftablesAndShuffle(allItems, category);
        return allItems;
    }


    public static List<DraftItem> buildGenericFactionItemSet(DraftItem.Category category, Game activeGame) {
        List<String> factionIds = getAllFactionIds(activeGame);
        List<DraftItem> allItems = new ArrayList<>();
        for (String factionId : factionIds) {
            allItems.add(DraftItem.Generate(category, factionId));
        }
        filterUndraftablesAndShuffle(allItems, category);
        return allItems;
    }

    @Override
    public List<DraftBag> generateBags(Game activeGame) {
        Map<DraftItem.Category, List<DraftItem>> allDraftableItems = new HashMap<>();
        List<FactionModel> allDraftableFactions = getAllDraftableFactions(activeGame);

        allDraftableItems.put(DraftItem.Category.ABILITY, AbilityDraftItem.buildAllDraftableItems(allDraftableFactions));
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

        allDraftableItems.put(DraftItem.Category.DRAFTORDER, SpeakerOrderDraftItem.buildAllDraftableItems(activeGame));

        MiltyDraftManager draftManager = activeGame.getMiltyDraftManager();
        new StartMilty().initDraftTiles(draftManager);
        allDraftableItems.put(DraftItem.Category.REDTILE, RedTileDraftItem.buildAllDraftableItems(draftManager));
        allDraftableItems.put(DraftItem.Category.BLUETILE, BlueTileDraftItem.buildAllDraftableItems(draftManager));


        List<DraftBag> bags = new ArrayList<>();

        for (int i = 0; i < activeGame.getRealPlayers().size(); i++) {
            DraftBag bag = new DraftBag();

            // Walk through each type of draftable...
            for (Map.Entry<DraftItem.Category, List<DraftItem>> draftableCollection : allDraftableItems.entrySet()) {
                DraftItem.Category category = draftableCollection.getKey();
                int categoryLimit = getItemLimitForCategory(category);
                // ... and pull out the appropriate number of items from its collection...
                for (int j = 0; j < categoryLimit; j++) {
                    // ... and add it to the player's bag.
                    bag.Contents.add(draftableCollection.getValue().remove(0));
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
