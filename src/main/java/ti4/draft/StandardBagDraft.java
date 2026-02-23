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
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftManager;

public class StandardBagDraft extends BagDraft {
    public StandardBagDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getItemLimitForCategory(DraftCategory category) {
        return switch (category) {
            case BLUETILE -> 3;
            case REDTILE -> 2;
            case DRAFTORDER, HOMESYSTEM -> 1;
            default -> 0;
        };
    }

    @Override
    public int getKeptItemLimitForCategory(DraftCategory category) {
        return getItemLimitForCategory(category);
    }

    @Override
    public String getSaveString() {
        return "standard_bag_draft";
    }

    private static final String[] excludedFactions = {
        "lazax", "admins", "franken", "keleresm", "keleresx", "miltymod", "qulane", "neutral", "kaltrim"
    };

    private static List<FactionModel> getDraftableFactionsForGame(Game game) {
        List<FactionModel> factionSet = getAllLegalFactions();
        if (!game.isDiscordantStarsMode()) {
            factionSet.removeIf(factionModel ->
                    factionModel.getSource().isDs() && !factionModel.getSource().isPok());
        }
        return factionSet;
    }

    private static List<FactionModel> getAllLegalFactions() {
        List<FactionModel> factionSet = Mapper.getFactionsValues();
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
        Map<DraftCategory, List<DraftItem>> allDraftableItems = new HashMap<>();
        List<FactionModel> allDraftableFactions = getDraftableFactionsForGame(game);
        allDraftableItems.put(
                DraftCategory.HOMESYSTEM, HomeSystemDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftCategory.DRAFTORDER, SpeakerOrderDraftItem.buildAllDraftableItems(game));

        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        MiltyDraftHelper.initDraftTiles(draftManager, game);
        allDraftableItems.put(DraftCategory.REDTILE, RedTileDraftItem.buildAllDraftableItems(draftManager));
        allDraftableItems.put(DraftCategory.BLUETILE, BlueTileDraftItem.buildAllDraftableItems(draftManager));

        List<DraftBag> bags = new ArrayList<>();

        Map<DraftCategory, Integer> missingItems = new HashMap<>();
        for (int i = 0; i < game.getRealPlayers().size(); i++) {
            DraftBag bag = new DraftBag();

            // Walk through each type of draftable...
            for (Map.Entry<DraftCategory, List<DraftItem>> draftableCollection : allDraftableItems.entrySet()) {
                DraftCategory category = draftableCollection.getKey();
                int categoryLimit = getItemLimitForCategory(category);
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
                String issue = game.getPing() + " an issue was encountered while building the draft.";
                issue += "\nOne or more bags are missing components.";
                for (var e : missingItems.entrySet()) {
                    issue += "\n> " + e.getKey().toString() + " is missing " + e.getValue() + " components.";
                }
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), issue);
                return null;
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
