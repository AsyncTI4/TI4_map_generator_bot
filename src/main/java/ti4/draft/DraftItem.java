package ti4.draft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import ti4.buttons.Buttons;
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
import ti4.draft.items.PlotDraftItem;
import ti4.draft.items.RedTileDraftItem;
import ti4.draft.items.SpeakerOrderDraftItem;
import ti4.draft.items.StartingFleetDraftItem;
import ti4.draft.items.StartingTechDraftItem;
import ti4.draft.items.TechDraftItem;
import ti4.draft.items.UnitDraftItem;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.service.emoji.TI4Emoji;

@Getter
public abstract class DraftItem {

    private final DraftCategory ItemCategory;
    private final String ItemId;
    private final DraftErrataModel Errata;

    @Override
    public boolean equals(Object other) {
        if (other instanceof DraftItem otherDraftItem)
            return otherDraftItem.getAlias().equals(getAlias());
        return false;
    }

    @Override
    public int hashCode() {
        return getAlias().hashCode();
    }

    protected DraftItem(DraftCategory category, String itemId) {
        ItemCategory = category;
        ItemId = itemId;
        Errata = Optional.ofNullable(Mapper.getFrankenErrata(getAlias())).orElse(DraftErrataModel.blank());
    }

    public String getAlias() {
        return ItemCategory.toString() + ":" + ItemId;
    }

    public abstract String getTitle(Game game);

    public abstract String getShortDescription();

    protected abstract String getLongDescriptionImpl();

    protected abstract String getLongDescriptionImpl(Game game);

    public abstract TI4Emoji getItemEmoji();

    public boolean hasOptionalSwaps() {
        return Errata != null && !Errata.getOptionalSwaps().isEmpty();
    }

    public boolean hasAdditionalComponents() {
        return Errata != null && !Errata.getAdditionalComponents().isEmpty();
    }

    public static DraftItem generate(DraftCategory category, String itemId) {
        DraftItem item =
                switch (category) {
                    case ABILITY -> item = new AbilityDraftItem(itemId);
                    case TECH -> item = new TechDraftItem(itemId);
                    case AGENT -> item = new AgentDraftItem(itemId);
                    case COMMANDER -> item = new CommanderDraftItem(itemId);
                    case HERO -> item = new HeroDraftItem(itemId);
                    case MECH -> item = new MechDraftItem(itemId);
                    case FLAGSHIP -> item = new FlagshipDraftItem(itemId);
                    case COMMODITIES -> item = new CommoditiesDraftItem(itemId);
                    case PN -> item = new PNDraftItem(itemId);
                    case HOMESYSTEM -> item = new HomeSystemDraftItem(itemId);
                    case STARTINGTECH -> item = new StartingTechDraftItem(itemId);
                    case STARTINGFLEET -> item = new StartingFleetDraftItem(itemId);
                    case BLUETILE -> item = new BlueTileDraftItem(itemId);
                    case REDTILE -> item = new RedTileDraftItem(itemId);
                    case DRAFTORDER -> item = new SpeakerOrderDraftItem(itemId);
                    case MAHACTKING -> item = new MahactKingDraftItem(itemId);
                    case UNIT -> item = new UnitDraftItem(itemId);
                    case BREAKTHROUGH -> item = new BreakthroughDraftItem(itemId);
                    case PLOT -> item = new PlotDraftItem(itemId);
                };
        return item;
    }

    public static DraftItem generateFromAlias(String alias) {
        String[] split = alias.split(":");
        return generate(DraftCategory.valueOf(split[0]), split[1]);
    }

    public static List<DraftItem> generateAllDraftableCards() {
        List<FactionModel> factions = FrankenDraft.getAllFrankenLegalFactions(null);
        List<DraftItem> items = new ArrayList<>();
        items.addAll(AbilityDraftItem.buildAllDraftableItems(factions));
        items.addAll(TechDraftItem.buildAllDraftableItems(factions));
        items.addAll(AgentDraftItem.buildAllDraftableItems(factions));
        items.addAll(CommanderDraftItem.buildAllDraftableItems(factions));
        items.addAll(HeroDraftItem.buildAllDraftableItems(factions));
        items.addAll(HomeSystemDraftItem.buildAllDraftableItems(factions));
        items.addAll(PNDraftItem.buildAllDraftableItems(factions));
        items.addAll(CommoditiesDraftItem.buildAllDraftableItems(factions));
        items.addAll(StartingTechDraftItem.buildAllDraftableItems(factions));
        items.addAll(StartingFleetDraftItem.buildAllDraftableItems(factions));
        items.addAll(FlagshipDraftItem.buildAllDraftableItems(factions));
        items.addAll(MechDraftItem.buildAllDraftableItems(factions));
        items.addAll(UnitDraftItem.buildAllDraftableItems());
        items.addAll(MahactKingDraftItem.buildAllDraftableItems());
        items.addAll(BreakthroughDraftItem.buildAllDraftableItems(factions));
        return items;
    }

    public static List<DraftItem> generateAllCards() {
        List<FactionModel> factions = Mapper.getFactionsValues();
        List<DraftItem> items = new ArrayList<>();
        items.addAll(AbilityDraftItem.buildAllItems(factions));
        items.addAll(TechDraftItem.buildAllItems(factions));
        items.addAll(AgentDraftItem.buildAllItems(factions));
        items.addAll(CommanderDraftItem.buildAllItems(factions));
        items.addAll(HeroDraftItem.buildAllItems(factions));
        items.addAll(HomeSystemDraftItem.buildAllItems(factions));
        items.addAll(PNDraftItem.buildAllItems(factions));
        items.addAll(CommoditiesDraftItem.buildAllItems(factions));
        items.addAll(StartingTechDraftItem.buildAllItems(factions));
        items.addAll(StartingFleetDraftItem.buildAllItems(factions));
        items.addAll(FlagshipDraftItem.buildAllItems(factions));
        items.addAll(MechDraftItem.buildAllItems(factions));
        items.addAll(UnitDraftItem.buildAllItems());
        items.addAll(MahactKingDraftItem.buildAllItems());
        items.addAll(BreakthroughDraftItem.buildAllItems(factions));
        items.addAll(PlotDraftItem.buildAllItems());
        return items;
    }

    public static List<DraftItem> getAlwaysIncludeItems(DraftCategory type) {
        List<DraftItem> alwaysInclude = new ArrayList<>();
        var frankenErrata = Mapper.getFrankenErrata().values();
        for (DraftErrataModel errataItem : frankenErrata) {
            if (errataItem.getItemCategory() == type && errataItem.isAlwaysAddToPool()) {
                alwaysInclude.add(generateFromAlias(errataItem.getAlias()));
            }
        }

        return alwaysInclude;
    }

    /**
     * &ltemojis&gt Item Name &ltemojis&gt
     * <br>&gt description
     * <br>&gt description
     * <br>&gt - ➕ Added Components
     * <br>&gt - ♻️ Optional Swap
     */
    @JsonIgnore
    public List<TextDisplay> getTextDisplays(Game game, Player player, boolean showDescr) {
        List<TextDisplay> textFields = new ArrayList<>();

        String details = getTitle(game);
        if (showDescr || ItemCategory.showDescrByDefault()) {
            String descr = getLongDescriptionImpl(game);
            descr = descr.trim().replaceAll("\n> ", "\n").replaceAll("\n", "\n> ");
            details += System.lineSeparator() + "> " + descr;
        }
        textFields.add(TextDisplay.of(details));

        if (hasAdditionalComponents() && !game.isTwilightsFallMode()) {
            List<String> adds = new ArrayList<>();
            adds.add("**__Additional Added Components:__**");
            for (DraftErrataModel i2 : Errata.getAdditionalComponents()) {
                DraftItem item2 = generate(i2.getItemCategory(), i2.getItemId());
                adds.add("> + " + item2.getTitle(game));
            }
            textFields.add(TextDisplay.of(String.join(System.lineSeparator(), adds)));
        }

        if (hasOptionalSwaps() && !game.isTwilightsFallMode()) {
            List<String> swaps = new ArrayList<>();
            swaps.add("**__Optional Component Swaps:__**");
            for (DraftErrataModel i2 : Errata.getOptionalSwaps()) {
                DraftItem item2 = generate(i2.getItemCategory(), i2.getItemId());
                swaps.add("> ♻️ " + item2.getTitle(game));
            }
            textFields.add(TextDisplay.of(String.join(System.lineSeparator(), swaps)));
        }
        return textFields;
    }

    @JsonIgnore
    public String getLongDescription() {
        StringBuilder sb = new StringBuilder(getLongDescriptionImpl());
        if (hasAdditionalComponents()) {
            sb.append("\n>  - *Also adds: ");
            for (DraftErrataModel i : Errata.getAdditionalComponents()) {
                DraftItem item = generate(i.getItemCategory(), i.getItemId());
                sb.append(item.getItemEmoji()).append(' ').append(item.getShortDescription());
                sb.append(", ");
            }
            sb.append("*");
        }
        if (hasOptionalSwaps()) {
            sb.append("\n>  - *Includes optional swaps: ");
            for (DraftErrataModel i : Errata.getOptionalSwaps()) {
                DraftItem item = generate(i.getItemCategory(), i.getItemId());
                sb.append(item.getItemEmoji()).append(' ').append(item.getShortDescription());
                sb.append(", ");
            }
            sb.append("*");
        }
        return sb.toString();
    }

    @JsonIgnore
    public String getLongDescription(Game game) {
        StringBuilder sb = new StringBuilder(getLongDescriptionImpl(game));
        if (hasAdditionalComponents()) {
            sb.append("\n>  - *Also adds: ");
            for (DraftErrataModel i : Errata.getAdditionalComponents()) {
                DraftItem item = generate(i.getItemCategory(), i.getItemId());
                sb.append(item.getItemEmoji()).append(' ').append(item.getShortDescription());
                sb.append(", ");
            }
            sb.append("*");
        }
        if (hasOptionalSwaps()) {
            sb.append("\n>  - *Includes optional swaps: ");
            for (DraftErrataModel i : Errata.getOptionalSwaps()) {
                DraftItem item = generate(i.getItemCategory(), i.getItemId());
                sb.append(item.getItemEmoji()).append(' ').append(item.getShortDescription());
                sb.append(", ");
            }
            sb.append("*");
        }
        return sb.toString();
    }

    public boolean isDraftable(Player player) {
        if (player.getDraftHand().Contents.contains(this) && ItemCategory != DraftCategory.COMMODITIES) {
            return false;
        }
        return isDraftable(player, ItemCategory);
    }

    public static boolean isDraftable(Player player, DraftCategory category) {
        if (isFinishedWithBag(player)) {
            return false;
        }
        if (isAtHandLimit(player, category)) {
            return false;
        }
        if (alreadyDraftedThisCategory(player, category)) {
            return !otherCategoriesAvailable(player, category);
        }
        return true;
    }

    public static String undraftableReason(Player player, DraftCategory category) {
        if (isDraftable(player, category)) return null;
        if (isFinishedWithBag(player)) return "⚠️ Cannot draft more components from this bag. ⚠️";
        if (isAtHandLimit(player, category)) return "⚠️ Already at hand limit for this component. ⚠️";
        if (alreadyDraftedThisCategory(player, category)) return "⚠️ Already drafted a component of this type. ⚠️";
        return null;
    }

    private static boolean isFinishedWithBag(Player player) {
        return player.getGame().getActiveBagDraft().playerQueueIsFull(player);
    }

    private static boolean isAtHandLimit(Player player, DraftCategory cat) {
        BagDraft draftRules = player.getGame().getActiveBagDraft();
        DraftBag draftHand = player.getDraftHand();
        boolean isAtHandLimit =
                draftHand.getCategoryCount(cat) + player.getDraftQueue().getCategoryCount(cat)
                        >= draftRules.getItemLimitForCategory(cat);
        if (draftRules instanceof FrankenDraft) {
            isAtHandLimit =
                    draftHand.getCategoryCount(cat) + player.getDraftQueue().getCategoryCount(cat)
                            >= FrankenDraft.getItemLimitForCategory(cat, player.getGame());
        }
        return isAtHandLimit;
    }

    private static boolean alreadyDraftedThisCategory(Player player, DraftCategory category) {
        return player.getDraftQueue().getCategoryCount(category) > 0;
    }

    private static boolean otherCategoriesAvailable(Player player, DraftCategory category) {
        BagDraft draftRules = player.getGame().getActiveBagDraft();
        DraftBag draftHand = player.getDraftHand();

        for (DraftCategory cat : DraftCategory.values()) {
            if (category == cat) continue;
            int catLimit = draftRules.getItemLimitForCategory(cat);

            if (draftRules instanceof FrankenDraft) {
                catLimit = FrankenDraft.getItemLimitForCategory(cat, player.getGame());
            }
            if (draftHand.getCategoryCount(cat) >= catLimit) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public Button getAddButton() {
        return Buttons.green("frankenItemAdd" + getAlias(), "Add " + getShortDescription(), getItemEmoji());
    }

    @JsonIgnore
    public Button getRemoveButton() {
        return Buttons.red("frankenItemRemove" + getAlias(), "Remove " + getShortDescription(), getItemEmoji());
    }
}
