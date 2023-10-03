package ti4.draft;

import ti4.commands.milty.MiltyDraftManager;
import ti4.commands.milty.MiltyDraftTile;
import ti4.commands.milty.StartMilty;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

import java.util.*;

public class FrankenDraft extends BagDraft {
    @Override
    public int GetItemLimitForCategory(DraftItem.Category category) {
        int limit = 0;
        switch (category) {
            case ABILITY, BLUETILE -> {
                limit = 3;
            }
            case TECH, REDTILE, STARTINGFLEET, STARTINGTECH, HOMESYSTEM, PN, COMMODITIES, FLAGSHIP, MECH, HERO, COMMANDER, AGENT -> {
                limit = 2;
            }
            case DRAFTORDER -> {
                limit = 1;
            }
        }
        return limit;
    }

    @Override
    public String getSaveString() {
        return "franken";
    }

    private static void filterUndraftablesAndShuffle(List<DraftItem> items, DraftItem.Category listCategory) {
        var bal = Mapper.getFrankenErrata();
        items.removeIf((DraftItem item) -> Mapper.getFrankenErrata().get(item.getAlias()).Undraftable);
        items.addAll(DraftItem.GetAlwaysIncludeItems(listCategory));
        Collections.shuffle(items);
    }

    // All the generic types of draftable items (i.e. things like "Argent Starting Tech"
    private static final DraftItem.Category[] genericDraftableTypes = {
            DraftItem.Category.AGENT,
            DraftItem.Category.COMMANDER,
            DraftItem.Category.HERO,
            DraftItem.Category.COMMODITIES,
            DraftItem.Category.PN,
            DraftItem.Category.MECH,
            DraftItem.Category.FLAGSHIP,
            DraftItem.Category.HOMESYSTEM,
            DraftItem.Category.STARTINGFLEET,
            DraftItem.Category.STARTINGTECH
    };

    private static final String[] excludedFactions = {"lazax", "admins", "franken", "keleresm", "keleresx"};
    private static List<String> getAllFactionIds(Game activeGame) {
        Map<String, String> factionSet = Mapper.getFactionRepresentations();
        List<String> factionIds = new ArrayList<String>();
        factionSet.forEach((String id, String name) -> {
            if (name.contains("(DS)") && !activeGame.isDiscordantStarsMode()) {
                return;
            } else {
                for (String excludedFaction : excludedFactions) {
                    if (id.contains(excludedFaction)) {
                        return;
                    }
                }
            }
            factionIds.add(id);
        });
        return factionIds;
    }

    public static List<DraftItem> buildDraftOrderSet(Game activeGame) {
        List<DraftItem> allItems = new ArrayList<>();
        for(int i = 0; i < activeGame.getRealPlayers().size(); i++){
            allItems.add(DraftItem.Generate(DraftItem.Category.DRAFTORDER, Integer.toString(i+1)));
        }
        filterUndraftablesAndShuffle(allItems, DraftItem.Category.DRAFTORDER);
        return allItems;
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
        for(MiltyDraftTile tile : allTiles) {
            allItems.add(DraftItem.Generate(category,
                    tile.getTile().getTileID()));
        }
        filterUndraftablesAndShuffle(allItems, category);
        return allItems;
    }

    public static List<DraftItem> buildAbilitySet(Game activeGame) {
        List<String> allFactions = getAllFactionIds(activeGame);
        List<DraftItem> allAbilityItems = new ArrayList<>();
        for (var factionId : allFactions) {
            FactionModel faction  = Mapper.getFactionSetup(factionId);
            for (var ability : faction.getAbilities()) {
                allAbilityItems.add(DraftItem.Generate(DraftItem.Category.ABILITY,ability));
            }
        }

        filterUndraftablesAndShuffle(allAbilityItems, DraftItem.Category.ABILITY);
        return allAbilityItems;
    }

    public static List<DraftItem> buildFactionTechSet(Game activeGame) {
        List<String> allFactions = getAllFactionIds(activeGame);
        List<DraftItem> allDraftableTechs = new ArrayList<>();
        for (var factionId : allFactions) {
            FactionModel faction = Mapper.getFactionSetup(factionId);
            for(var tech : faction.getFactionTech()) {
                allDraftableTechs.add(DraftItem.Generate(DraftItem.Category.TECH, tech));
            }
        }
        filterUndraftablesAndShuffle(allDraftableTechs, DraftItem.Category.TECH);
        return allDraftableTechs;
    }

    public static List<DraftItem> buildGenericFactionItemSet(DraftItem.Category category, Game activeGame) {
        List<String> factionIds = getAllFactionIds(activeGame);
        List<DraftItem> allItems = new ArrayList<DraftItem>();
        for (String factionId: factionIds) {
            allItems.add(DraftItem.Generate(category, factionId));
        }
        filterUndraftablesAndShuffle(allItems, category);
        return allItems;
    }

    @Override
    public List<DraftBag> generateBags(Game activeGame) {
        Map<DraftItem.Category, List<DraftItem>> allDraftableItems = new HashMap<DraftItem.Category, List<DraftItem>>();
        for (DraftItem.Category category: genericDraftableTypes) {
            allDraftableItems.put(category, buildGenericFactionItemSet(category, activeGame));
        }

        allDraftableItems.put(DraftItem.Category.DRAFTORDER, buildDraftOrderSet(activeGame));

        MiltyDraftManager draftManager = activeGame.getMiltyDraftManager();
        new StartMilty().initDraftTiles(draftManager);
        allDraftableItems.put(DraftItem.Category.REDTILE, buildTileSet(draftManager, false));
        allDraftableItems.put(DraftItem.Category.BLUETILE, buildTileSet(draftManager, true));

        allDraftableItems.put(DraftItem.Category.ABILITY, buildAbilitySet(activeGame));
        allDraftableItems.put(DraftItem.Category.TECH, buildFactionTechSet(activeGame));

        List<DraftBag> bags = new ArrayList<>();

        for (int i = 0; i < activeGame.getRealPlayers().size(); i++) {
            DraftBag bag = new DraftBag();

            // Walk through each type of draftable...
            for (Map.Entry<DraftItem.Category, List<DraftItem>> draftableCollection:allDraftableItems.entrySet()) {
                DraftItem.Category category = draftableCollection.getKey();
                int categoryLimit = GetItemLimitForCategory(category);
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
}
