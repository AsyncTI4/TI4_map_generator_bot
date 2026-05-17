package ti4.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ti4.draft.items.BlueTileDraftItem;
import ti4.draft.items.FactionDraftItem;
import ti4.draft.items.RedTileDraftItem;
import ti4.draft.items.SpeakerOrderDraftItem;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.PatternHelper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftManager;

public class FrankenDrazDraft extends FrankenDraft {
    private static final List<String> AUTO_BANNED_FACTIONS = List.of("obsidian", "firmament");
    public FrankenDrazDraft(Game owner) {
        super(owner);
    }

    @Override
    public int getItemLimitForCategory(DraftCategory category) {
        return switch (category) {
            case FACTION -> 6;
            case BLUETILE -> 3;
            case REDTILE -> 2;
            case DRAFTORDER -> 1;
            default -> 0;
        };
    }

    @Override
    public int getKeptItemLimitForCategory(DraftCategory category) {
        return switch (category) {
            case ABILITY -> 4;
            case TECH, BLUETILE -> 3;
            case REDTILE -> 2;
            case COMMODITIES, FLAGSHIP, MECH, PN -> 1;
            case HERO, COMMANDER, AGENT, BREAKTHROUGH -> 1;
            case DRAFTORDER, STARTINGFLEET, STARTINGTECH, HOMESYSTEM -> 1;
            case FACTION, UNIT, PLOT, MAHACTKING -> 0;
        };
    }

    @Override
    public int getPicksFromFirstBag() {
        return 2;
    }

    @Override
    public int getPicksFromNextBags() {
        return 1;
    }

    @Override
    public String getSaveString() {
        return "frankendraz";
    }

    @Override
    public List<DraftBag> generateBags(Game game) {
        Map<DraftCategory, List<DraftItem>> allDraftableItems = new HashMap<>();
        List<FactionModel> allDraftableFactions = getDraftableFactionsForGame(game);
        allDraftableItems.put(DraftCategory.FACTION, FactionDraftItem.buildAllDraftableItems(allDraftableFactions));
        allDraftableItems.put(DraftCategory.DRAFTORDER, SpeakerOrderDraftItem.buildAllDraftableItems(game));

        MiltyDraftManager draftManager = game.getMiltyDraftManager();
        draftManager.clear();
        MiltyDraftHelper.initDraftTiles(draftManager, game);
        allDraftableItems.put(DraftCategory.REDTILE, RedTileDraftItem.buildAllDraftableItems(draftManager, game));
        allDraftableItems.put(DraftCategory.BLUETILE, BlueTileDraftItem.buildAllDraftableItems(draftManager, game));

        List<DraftBag> bags = new ArrayList<>();
        Map<DraftCategory, Integer> missingItems = new HashMap<>();
        for (int i = 0; i < game.getRealPlayers().size(); i++) {
            DraftBag bag = new DraftBag();

            for (Map.Entry<DraftCategory, List<DraftItem>> draftableCollection : allDraftableItems.entrySet()) {
                DraftCategory category = draftableCollection.getKey();
                int categoryLimit = getItemLimitForCategory(category);
                for (int j = 0; j < categoryLimit; j++) {
                    if (!draftableCollection.getValue().isEmpty()) {
                        bag.Contents.add(draftableCollection.getValue().removeFirst());
                    } else {
                        missingItems.compute(category, (c, x) -> x == null ? 1 : x + 1);
                    }
                }
            }

            if (!missingItems.isEmpty()) {
                StringBuilder issue = new StringBuilder(
                        game.getPing() + " an issue was encountered while building the FrankenDraz draft.");
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
    public boolean isDraftStageComplete() {
        for (Player player : getOwnerPlayers()) {
            if (!hasExpandedFactionComponents(player.getDraftHand())) {
                return super.isDraftStageComplete();
            }
        }

        for (Player player : getOwnerPlayers()) {
            DraftBag hand = player.getDraftHand();
            if (hand.getCategoryCount(DraftCategory.FACTION) > 0
                    || hand.getCategoryCount(DraftCategory.BLUETILE) != getItemLimitForCategory(DraftCategory.BLUETILE)
                    || hand.getCategoryCount(DraftCategory.REDTILE) != getItemLimitForCategory(DraftCategory.REDTILE)
                    || hand.getCategoryCount(DraftCategory.DRAFTORDER)
                            != getItemLimitForCategory(DraftCategory.DRAFTORDER)) {
                return false;
            }
        }
        return true;
    }

    public void expandFactionPackages(Game game) {
        for (Player player : game.getRealPlayers()) {
            DraftBag hand = player.getDraftHand();
            Map<String, DraftItem> expanded = new LinkedHashMap<>();
            for (DraftItem item : hand.Contents) {
                if (item.getItemCategory() != DraftCategory.FACTION) {
                    expanded.putIfAbsent(item.getAlias(), item);
                }
            }
            for (DraftItem item : hand.Contents) {
                if (item instanceof FactionDraftItem factionItem) {
                    for (DraftItem component : factionItem.getComponents(game)) {
                        expanded.putIfAbsent(component.getAlias(), component);
                    }
                }
            }
            hand.Contents.clear();
            hand.Contents.addAll(expanded.values());
        }
    }

    @Override
    public int getBagSize() {
        return 12;
    }

    private List<Player> getOwnerPlayers() {
        return getOwner().getRealPlayers();
    }

    private static boolean hasExpandedFactionComponents(DraftBag hand) {
        return hand.getCategoryCount(DraftCategory.HOMESYSTEM) > 0
                || hand.getCategoryCount(DraftCategory.STARTINGFLEET) > 0
                || hand.getCategoryCount(DraftCategory.ABILITY) > 0
                || hand.getCategoryCount(DraftCategory.TECH) > 0
                || hand.getCategoryCount(DraftCategory.AGENT) > 0
                || hand.getCategoryCount(DraftCategory.COMMANDER) > 0
                || hand.getCategoryCount(DraftCategory.HERO) > 0
                || hand.getCategoryCount(DraftCategory.MECH) > 0
                || hand.getCategoryCount(DraftCategory.FLAGSHIP) > 0
                || hand.getCategoryCount(DraftCategory.PN) > 0
                || hand.getCategoryCount(DraftCategory.STARTINGTECH) > 0
                || hand.getCategoryCount(DraftCategory.BREAKTHROUGH) > 0;
    }

    private static List<FactionModel> getDraftableFactionsForGame(Game game) {
        Map<String, FactionModel> factions = new LinkedHashMap<>();
        for (FactionModel faction : FrankenDraft.getAllFrankenLegalFactions(game)) {
            factions.put(faction.getAlias(), faction);
        }
        for (FactionModel faction : FrankenDraft.getAllFrankenLegalFactions(null)) {
            if (faction.getSource().isDs()) {
                factions.put(faction.getAlias(), faction);
            }
        }

        String[] bannedFactions = PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue("bannedFactions"));
        for (String bannedFaction : bannedFactions) {
            factions.remove(bannedFaction);
        }
        for (String faction : AUTO_BANNED_FACTIONS) {
            factions.remove(faction);
        }
        return new ArrayList<>(factions.values());
    }
}
