package ti4.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ti4.draft.items.BlueTileDraftItem;
import ti4.draft.items.HomeSystemDraftItem;
import ti4.draft.items.RedTileDraftItem;
import ti4.draft.items.SpeakerOrderDraftItem;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.BotLogger;
import ti4.model.FactionModel;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftManager;

public class StandardBagDraft extends BagDraft {
    public StandardBagDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getItemLimitForCategory(DraftItem.Category category) {
        return switch (category) {
            case BLUETILE -> 3;
            case REDTILE -> 2;
            case DRAFTORDER, HOMESYSTEM -> 1;
            default -> 0;
        };
    }

    @Override
    public String getSaveString() {
        return "standard_bag_draft";
    }

    private static final String[] excludedFactions = { "lazax", "admins", "franken", "keleresm", "keleresx", "miltymod", "qulane", "neutral" };

    public static List<FactionModel> getDraftableFactionsForGame(Game game) {
        List<FactionModel> factionSet = getAllLegalFactions();
        if (!game.isDiscordantStarsMode()) {
            factionSet.removeIf(factionModel -> factionModel.getSource().isDs() && !factionModel.getSource().isPok());
        }
        return factionSet;
    }

    public static List<FactionModel> getAllLegalFactions() {
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
        allDraftableItems.put(DraftItem.Category.HOMESYSTEM, HomeSystemDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftItem.Category.DRAFTORDER, SpeakerOrderDraftItem.buildAllDraftableItems(game));

        MiltyDraftManager draftManager = game.getMiltyDraftManager();
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
        return 7;
    }
}
