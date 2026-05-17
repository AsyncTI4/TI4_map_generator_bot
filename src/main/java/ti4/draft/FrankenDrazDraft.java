package ti4.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.separator.Separator.Spacing;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import ti4.discord.interactions.buttons.Buttons;
import ti4.draft.items.BlueTileDraftItem;
import ti4.draft.items.FactionDraftItem;
import ti4.draft.items.RedTileDraftItem;
import ti4.draft.items.SpeakerOrderDraftItem;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.PatternHelper;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.model.FactionModel;
import ti4.service.franken.FrankenDraftBagService;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftManager;

public class FrankenDrazDraft extends FrankenDraft {
    private static final List<String> AUTO_BANNED_FACTIONS = List.of("obsidian", "firmament");
    private static final List<DraftCategory> POST_DRAFT_COMPONENT_CATEGORIES = List.of(
            DraftCategory.ABILITY,
            DraftCategory.TECH,
            DraftCategory.BREAKTHROUGH,
            DraftCategory.AGENT,
            DraftCategory.COMMANDER,
            DraftCategory.HERO,
            DraftCategory.MECH,
            DraftCategory.FLAGSHIP,
            DraftCategory.COMMODITIES,
            DraftCategory.PN,
            DraftCategory.HOMESYSTEM,
            DraftCategory.STARTINGTECH,
            DraftCategory.STARTINGFLEET);

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

    public List<DraftCategory> getPostDraftComponentCategories() {
        return POST_DRAFT_COMPONENT_CATEGORIES;
    }

    public void sendPostDraftComponentButtons(Player player) {
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                "Choose a drafted component category to view. Additional components are added automatically. Optional Swaps will appear in their respective categories when expanded. Home systems and starting fleet must be added manually, as well as any additional or optional components added via these components. Category buttons are present for your convenience.",
                getPostDraftCategoryButtons(player));
    }

    public void sendPostDraftCategory(Player player, DraftCategory category) {
        if (!POST_DRAFT_COMPONENT_CATEGORIES.contains(category)) {
            return;
        }

        ThreadChannel cardsInfoThread = player.getCardsInfoThread();
        List<Container> containers = buildPostDraftCategoryContainers(player, category);
        if (containers.isEmpty()) {
            MessageHelper.sendMessageToChannel(cardsInfoThread, "You have no drafted " + categoryLabel(category) + ".");
            return;
        }

        for (Container container : containers) {
            MessageV2Builder builder = new MessageV2Builder(cardsInfoThread);
            builder.append(container.withAccentColor(
                    FrankenDraftBagService.getAccents().getFirst()));
            builder.send();
        }
    }

    @Override
    public int getBagSize() {
        return 12;
    }

    private List<Player> getOwnerPlayers() {
        return getOwner().getRealPlayers();
    }

    private List<Button> getPostDraftCategoryButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        for (DraftCategory category : POST_DRAFT_COMPONENT_CATEGORIES) {
            String buttonID = player.factionButtonChecker() + "frankenDrazCategory;" + category.name();
            buttons.add(Buttons.gray(buttonID, categoryLabel(category), category.emoji(player.getGame())));
        }
        return buttons;
    }

    private List<Container> buildPostDraftCategoryContainers(Player player, DraftCategory category) {
        List<DraftItem> all = player.getDraftHand().getCategory(category);
        if (all.isEmpty()) {
            return List.of();
        }

        List<List<DraftItem>> groups = new ArrayList<>();
        List<DraftItem> current = new ArrayList<>();
        for (DraftItem item : all) {
            current.add(item);
            Container candidate =
                    buildPostDraftCategoryContainer(player, category, current, category.title(player.getGame()));
            if (isOversized(candidate) && current.size() > 1) {
                current.removeLast();
                groups.add(current);
                current = new ArrayList<>(List.of(item));
            }
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }

        List<Container> containers = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            String title = category.title(player.getGame());
            if (groups.size() > 1) {
                title += " (" + (i + 1) + "/" + groups.size() + ")";
            }
            containers.add(buildPostDraftCategoryContainer(player, category, groups.get(i), title));
        }
        return containers;
    }

    private Container buildPostDraftCategoryContainer(
            Player player, DraftCategory category, List<DraftItem> items, String title) {
        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(TextDisplay.of(title));

        for (DraftItem item : items) {
            if (components.size() > 1) components.add(Separator.createDivider(Spacing.LARGE));
            components.addAll(item.getTextDisplays(player.getGame(), player, true));
        }

        components.addAll(ActionRow.partitionOf(getApplyButtons(player, category, items)));
        return Container.of(components);
    }

    private List<Button> getApplyButtons(Player player, DraftCategory category, List<DraftItem> items) {
        List<Button> buttons = new ArrayList<>();
        List<String> appliedItems = player.getStoredList("appliedFrankenItems");
        int limit = getKeptItemLimitForCategory(category);
        int taken = player.getDraftHand().getCategoryAppliedCount(appliedItems, category);
        boolean atLimit = taken >= limit;

        for (DraftItem item : items) {
            boolean alreadyHas = appliedItems.contains(item.getAlias());
            Button button = item.getAddButton().withDisabled(atLimit);
            if (alreadyHas) button = item.getRemoveButton();
            buttons.add(button);
        }
        return buttons;
    }

    private static boolean isOversized(Container container) {
        return MessageV2Builder.CountComponents(container) > Message.MAX_COMPONENT_COUNT_IN_COMPONENT_TREE
                || MessageV2Builder.CountCharacters(container) > Message.MAX_CONTENT_LENGTH_COMPONENT_V2;
    }

    private static String categoryLabel(DraftCategory category) {
        return switch (category) {
            case ABILITY -> "Abilities";
            case TECH -> "Faction Techs";
            case BREAKTHROUGH -> "Breakthroughs";
            case AGENT -> "Agents";
            case COMMANDER -> "Commanders";
            case HERO -> "Heroes";
            case MECH -> "Mechs";
            case FLAGSHIP -> "Flagships";
            case COMMODITIES -> "Commodities";
            case PN -> "Promissory Notes";
            case HOMESYSTEM -> "Home Systems";
            case STARTINGTECH -> "Starting Techs";
            case STARTINGFLEET -> "Starting Fleets";
            default -> category.toString();
        };
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
