package ti4.draft;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.separator.Separator.Spacing;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.draft.items.FactionDraftItem;
import ti4.draft.items.MonumentDraftItem;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.message.componentsV2.MessageV2Editor;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.service.franken.FrankenDraftBagService;
import ti4.service.milty.MiltyService;

public class FrankenDrazDraft extends FrankenDraft {
    public static final String UNLIMITED_KEPT_COMPONENTS_KEY = "frankenDrazUnlimitedKeptComponents";
    public static final String PRIORITY_FACTIONS_KEY = "frankenDrazPriorityFactions";
    public static final String DISCORDANT_STARS_FACTION_LIMITS_KEY = "frankenDrazDiscordantStarsFactionLimits";
    public static final String BLUE_REVERIE_FACTION_LIMITS_KEY = "frankenDrazBlueReverieFactionLimits";
    public static final String LOST_LEGACIES_FACTION_LIMITS_KEY = "frankenDrazLostLegaciesFactionLimits";
    private static final int DEFAULT_FACTION_LIMIT = 6;
    private static final List<DraftCategory> MAP_CATEGORIES =
            List.of(DraftCategory.BLUETILE, DraftCategory.REDTILE, DraftCategory.DRAFTORDER);
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
            DraftCategory.STARTINGFLEET,
            DraftCategory.MONUMENT);

    public FrankenDrazDraft(Game owner) {
        super(owner, 2, 1);
    }

    @Override
    public int getItemLimitForCategory(DraftCategory category) {
        if (isFowExcludedCategory(category)) return 0;
        return switch (category) {
            case FACTION -> getFactionDraftLimit();
            case BLUETILE -> 3;
            case REDTILE -> 2;
            case DRAFTORDER -> 1;
            default -> 0;
        };
    }

    private int getFactionDraftLimit() {
        return getConfiguredLimit(DraftCategory.FACTION, DEFAULT_FACTION_LIMIT);
    }

    @Override
    public int getKeptItemLimitForCategory(DraftCategory category) {
        if (isFowExcludedCategory(category)) return 0;
        int limit =
                switch (category) {
                    case ABILITY -> 4;
                    case TECH, BLUETILE -> 3;
                    case REDTILE -> 2;
                    case COMMODITIES, FLAGSHIP, MECH, PN -> 1;
                    case HERO, COMMANDER, AGENT, BREAKTHROUGH -> 1;
                    case MONUMENT -> getConfiguredMonumentLimit();
                    case DRAFTORDER, STARTINGFLEET, STARTINGTECH, HOMESYSTEM -> 1;
                    case FACTION, UNIT, PLOT, MAHACTKING -> 0;
                };
        if (limit > 0 && hasUnlimitedKeptComponents(getOwner())) {
            return Integer.MAX_VALUE;
        }
        return limit;
    }

    public static boolean hasUnlimitedKeptComponents(Game game) {
        return "true".equals(game.getStoredValue(UNLIMITED_KEPT_COMPONENTS_KEY));
    }

    @Override
    public String getSaveString() {
        return "frankendraz";
    }

    @Override
    public List<DraftBag> generateBags(Game game) {
        Map<DraftCategory, List<DraftItem>> allDraftableItems = new HashMap<>();
        List<FactionModel> allDraftableFactions = getDraftableFactionsForGame(game);
        List<DraftItem> factionItems = FactionDraftItem.buildAllDraftableItems(allDraftableFactions);
        int factionPoolSize = game.getRealPlayers().size() * getFactionDraftLimit();
        Map<ComponentSource, int[]> sourceLimits = new HashMap<>();
        for (Map.Entry<ComponentSource, String> entry : Map.of(
                        ComponentSource.ds, DISCORDANT_STARS_FACTION_LIMITS_KEY,
                        ComponentSource.blue_reverie, BLUE_REVERIE_FACTION_LIMITS_KEY,
                        ComponentSource.theodisi, LOST_LEGACIES_FACTION_LIMITS_KEY)
                .entrySet()) {
            String[] limits = game.getStoredValue(entry.getValue()).split("\\|", 2);
            if (limits.length == 2) {
                sourceLimits.put(entry.getKey(), new int[] {Integer.parseInt(limits[0]), Integer.parseInt(limits[1])});
            }
        }
        List<String> factionAliases =
                factionItems.stream().map(DraftItem::getItemId).toList();
        List<String> priorityFactions =
                List.of(PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue(PRIORITY_FACTIONS_KEY)));
        List<FactionModel> selectedFactions = MiltyService.createFactionDraft(
                        factionPoolSize, new ArrayList<>(factionAliases), priorityFactions, sourceLimits)
                .stream()
                .map(Mapper::getFaction)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<DraftItem> selectedFactionItems = FactionDraftItem.buildAllItems(selectedFactions);
        Collections.shuffle(selectedFactionItems);
        allDraftableItems.put(DraftCategory.FACTION, selectedFactionItems);
        putSharedDraftableItems(allDraftableItems, game);
        return fillBags(game, allDraftableItems, "FrankenDraz");
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
                    || MAP_CATEGORIES.stream()
                            .anyMatch(cat -> hand.getCategoryCount(cat) != getItemLimitForCategory(cat, getOwner()))) {
                return false;
            }
        }
        return true;
    }

    public static void expandFactionPackages(Game game) {
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
            if (game.isMonumentsMode()) {
                List<String> draftedFactions = hand.Contents.stream()
                        .filter(FactionDraftItem.class::isInstance)
                        .map(DraftItem::getItemId)
                        .toList();
                for (DraftItem monument : MonumentDraftItem.buildAllItems(game)) {
                    if (Boolean.TRUE.equals(monument.getErrata().getUndraftable())) {
                        continue;
                    }
                    if (draftedFactions.contains(
                            Mapper.getUnit(monument.getItemId()).getFaction().orElse(null))) {
                        expanded.putIfAbsent(monument.getAlias(), monument);
                    }
                }
            }
            hand.Contents.clear();
            hand.Contents.addAll(expanded.values());
        }
    }

    public void sendPostDraftComponentButtons(Player player) {
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                "Choose a drafted component category to view. Additional components are added automatically. Optional Swaps will appear in their respective categories when expanded.\n\nHome systems and starting fleet adds/swaps must be handled manually, however the mantis/random map build will give you buttons to select your hs and starting fleet. Category buttons are present for your convenience.",
                getPostDraftCategoryButtons(player));
    }

    @ButtonHandler(value = "frankenFactionComponents", save = false)
    private static void showFactionComponents(ButtonInteractionEvent event, Player player, String buttonID) {
        if (player.getGame().getActiveBagDraft() instanceof FrankenDrazDraft) {
            sendFactionComponentCards(event, player, buttonID.split(";")[1]);
        }
    }

    public static void sendFactionComponentCards(ButtonInteractionEvent event, Player player, String faction) {
        FactionDraftItem item = new FactionDraftItem(faction);
        List<DraftItem> components = item.getComponents(player.getGame());
        if (components.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "## " + item.getTitle(player.getGame()) + " Components\nNone.");
        } else {
            List<Color> accents = FrankenDraftBagService.getAccents();
            MessageV2Builder builder = new MessageV2Builder(player.getCardsInfoThread());
            builder.append("## " + item.getTitle(player.getGame()) + " Components");
            for (DraftItem component : components) {
                List<ContainerChildComponent> cardComponents =
                        new ArrayList<>(component.getTextDisplays(player.getGame(), player, true));
                builder.append(Container.of(cardComponents).withAccentColor(accents.getFirst()));
                Collections.rotate(accents, -1);
            }
            builder.send();
        }
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Sent " + item.getShortDescription() + " components to your cards info thread.");
    }

    @ButtonHandler(value = "frankenDrazCategory", save = false)
    private static void showFrankenDrazCategory(ButtonInteractionEvent event, Player player, String buttonID) {
        if (player.getGame().getActiveBagDraft() instanceof FrankenDrazDraft frankenDrazDraft) {
            DraftCategory category = DraftCategory.valueOf(buttonID.split(";")[1]);
            frankenDrazDraft.sendPostDraftCategory(player, category);
        }
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

    public void refreshPostDraftCategory(ButtonInteractionEvent event, Player player, DraftCategory category) {
        if (!POST_DRAFT_COMPONENT_CATEGORIES.contains(category)) {
            return;
        }

        List<Container> containers = buildPostDraftCategoryContainers(player, category);
        if (containers.isEmpty()) {
            return;
        }

        MessageV2Editor editor = new MessageV2Editor();
        for (Container container : containers) {
            Container replacement = container.withAccentColor(
                    FrankenDraftBagService.getAccents().getFirst());
            editor.replace(matchesContainerTitle(replacement), replacement);
        }
        editor.applyAroundMessage(event.getMessage(), containers.size() * 2 + 2, changed -> {
            if (!changed) sendPostDraftCategory(player, category);
        });
    }

    @ButtonHandler(value = "frankenDrazCloseCategory", save = false)
    private static void closeFrankenDrazCategory(ButtonInteractionEvent event, Player player, String buttonID) {
        if (player.getGame().getActiveBagDraft() instanceof FrankenDrazDraft frankenDrazDraft) {
            DraftCategory category = DraftCategory.valueOf(buttonID.split(";")[1]);
            frankenDrazDraft.closePostDraftCategory(event, player, category);
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Closed " + category + ".");
        }
    }

    public void closePostDraftCategory(ButtonInteractionEvent event, Player player, DraftCategory category) {
        if (!POST_DRAFT_COMPONENT_CATEGORIES.contains(category)) {
            return;
        }

        int lookback = buildPostDraftCategoryContainers(player, category).size() * 2 + 4;
        event.getMessage()
                .getChannel()
                .getHistoryAround(event.getMessage().getIdLong(), lookback)
                .queue(
                        messageHistory -> {
                            if (isPostDraftCategoryMessage(event.getMessage(), player.getGame(), category)) {
                                event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
                            }
                            for (Message message : messageHistory.getRetrievedHistory()) {
                                if (message.getIdLong() == event.getMessage().getIdLong()
                                        || !message.getAuthor().isBot()) {
                                    continue;
                                }
                                if (isPostDraftCategoryMessage(message, player.getGame(), category)) {
                                    message.delete().queue(Consumers.nop(), BotLogger::catchRestError);
                                }
                            }
                        },
                        BotLogger::catchRestError);
    }

    @Override
    public int getBagSize() {
        return getItemLimitForCategory(DraftCategory.FACTION, getOwner())
                + MAP_CATEGORIES.stream()
                        .mapToInt(cat -> getItemLimitForCategory(cat, getOwner()))
                        .sum();
    }

    private List<Player> getOwnerPlayers() {
        return getOwner().getRealPlayers();
    }

    private static List<Button> getPostDraftCategoryButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        for (DraftCategory category : POST_DRAFT_COMPONENT_CATEGORIES) {
            if (category == DraftCategory.MONUMENT && !player.getGame().isMonumentsMode()) {
                continue;
            }
            String buttonID = player.factionButtonChecker() + "frankenDrazCategory;" + category.name();
            buttons.add(Buttons.gray(buttonID, categoryLabel(category), category.emoji(player.getGame())));
        }
        return buttons;
    }

    private List<Container> buildPostDraftCategoryContainers(Player player, DraftCategory category) {
        List<DraftItem> all = player.getDraftHand().getDistinctCategory(category);
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

        if (!isManualSetupCategory(category)) {
            components.addAll(ActionRow.partitionOf(getApplyButtons(player, category, items)));
        }
        components.add(ActionRow.of(Buttons.red(
                player.factionButtonChecker() + "frankenDrazCloseCategory;" + category.name(), "Close Category")));
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

    private static boolean isManualSetupCategory(DraftCategory category) {
        return category == DraftCategory.HOMESYSTEM || category == DraftCategory.STARTINGFLEET;
    }

    private static boolean isPostDraftCategoryMessage(Message message, Game game, DraftCategory category) {
        String categoryTitle = category.title(game);
        return message.getComponentTree().getComponents().stream()
                .filter(Container.class::isInstance)
                .map(Container.class::cast)
                .map(FrankenDrazDraft::getContainerTitle)
                .anyMatch(title ->
                        categoryTitle.equals(title) || (title != null && title.startsWith(categoryTitle + " (")));
    }

    private static Predicate<Component> matchesContainerTitle(Container replacement) {
        String replacementTitle = getContainerTitle(replacement);
        return component -> component instanceof Container container
                && replacementTitle != null
                && replacementTitle.equals(getContainerTitle(container));
    }

    private static String getContainerTitle(Container container) {
        if (container.getComponents().isEmpty()) {
            return null;
        }
        ContainerChildComponent child = container.getComponents().getFirst();
        if (child instanceof TextDisplay textDisplay) {
            return textDisplay.getContent();
        }
        return null;
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
        return POST_DRAFT_COMPONENT_CATEGORIES.stream()
                .filter(category -> category != DraftCategory.COMMODITIES)
                .anyMatch(category -> hand.getCategoryCount(category) > 0);
    }

    public static List<FactionModel> getDraftableFactionsForGame(Game game) {
        Map<String, FactionModel> factions = new LinkedHashMap<>();
        for (FactionModel faction : getAllFrankenLegalFactions(game)) {
            factions.put(faction.getAlias(), faction);
        }
        // Frankendraz includes DS and BR by default; the Frankendraft menu excludes either
        // source by adding its factions to bannedFactions when its disable toggle is selected.
        for (FactionModel faction : getAllFrankenLegalFactions(null)) {
            if (faction.getSource() == ComponentSource.ds || faction.getSource() == ComponentSource.blue_reverie) {
                factions.put(faction.getAlias(), faction);
            }
        }
        String[] bannedFactions = PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue("bannedFactions"));
        for (String bannedFaction : bannedFactions) {
            factions.remove(bannedFaction);
        }
        return new ArrayList<>(factions.values());
    }
}
