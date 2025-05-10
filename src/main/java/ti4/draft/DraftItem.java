package ti4.draft;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
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
import ti4.map.Player;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.ModelInterface;
import ti4.service.emoji.TI4Emoji;

public abstract class DraftItem implements ModelInterface {
    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String getAlias() {
        return ItemCategory.toString() + ":" + ItemId;
    }

    public enum Category {
        ABILITY, TECH, AGENT, COMMANDER, HERO, MECH, FLAGSHIP, COMMODITIES, PN, HOMESYSTEM, STARTINGTECH, STARTINGFLEET, BLUETILE, REDTILE, DRAFTORDER
    }

    public final Category ItemCategory;

    // The system ID of the item. Only convert this to player-readable text when necessary
    public final String ItemId;

    public DraftErrataModel Errata;

    public static DraftItem generate(Category category, String itemId) {
        DraftItem item = switch (category) {
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
        };

        item.Errata = Mapper.getFrankenErrata().get(item.getAlias());
        return item;
    }

    public static DraftItem generateFromAlias(String alias) {
        String[] split = alias.split(":");
        return generate(Category.valueOf(split[0]), split[1]);
    }

    public static List<DraftItem> generateAllDraftableCards() {
        List<FactionModel> factions = FrankenDraft.getAllFrankenLegalFactions();
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
        return items;
    }

    public static List<DraftItem> generateAllCards() {
        List<FactionModel> factions = Mapper.getFactions();
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
        return items;
    }

    public static List<DraftItem> getAlwaysIncludeItems(Category type) {
        List<DraftItem> alwaysInclude = new ArrayList<>();
        var frankenErrata = Mapper.getFrankenErrata().values();
        for (DraftErrataModel errataItem : frankenErrata) {
            if (errataItem.ItemCategory == type && errataItem.AlwaysAddToPool) {
                alwaysInclude.add(generateFromAlias(errataItem.getAlias()));
            }
        }

        return alwaysInclude;
    }

    protected DraftItem(Category category, String itemId) {
        ItemCategory = category;
        ItemId = itemId;
    }

    @JsonIgnore
    public abstract String getShortDescription();

    @JsonIgnore
    public String getLongDescription() {
        StringBuilder sb = new StringBuilder(getLongDescriptionImpl());
        if (Errata != null) {
            if (Errata.AdditionalComponents != null) {
                sb.append("\n>  - *Also adds: ");
                for (DraftErrataModel i : Errata.AdditionalComponents) {
                    DraftItem item = generate(i.ItemCategory, i.ItemId);
                    sb.append(item.getItemEmoji()).append(" ").append(item.getShortDescription());
                    sb.append(", ");
                }
                sb.append("*");
            }
            if (Errata.OptionalSwaps != null) {
                sb.append("\n>  - *Includes optional swaps: ");
                for (DraftErrataModel i : Errata.OptionalSwaps) {
                    DraftItem item = generate(i.ItemCategory, i.ItemId);
                    sb.append(item.getItemEmoji()).append(" ").append(item.getShortDescription());
                    sb.append(", ");
                }
                sb.append("*");
            }
        }
        return sb.toString();
    }

    @JsonIgnore
    protected abstract String getLongDescriptionImpl();

    @JsonIgnore
    public abstract TI4Emoji getItemEmoji();

    public boolean isDraftable(Player player) {
        BagDraft draftRules = player.getGame().getActiveBagDraft();
        DraftBag draftHand = player.getDraftHand();
        boolean isAtHandLimit = draftHand.getCategoryCount(ItemCategory) >= draftRules.getItemLimitForCategory(ItemCategory);
        if (isAtHandLimit) {
            return false;
        }
        boolean hasDraftedThisBag = player.getDraftQueue().getCategoryCount(ItemCategory) > 0;

        boolean allOtherCategoriesAtHandLimit = true;
        for (Category cat : Category.values()) {
            if (ItemCategory == cat) {
                continue;
            }
            allOtherCategoriesAtHandLimit &= draftHand.getCategoryCount(cat) >= draftRules.getItemLimitForCategory(cat);
        }

        if (hasDraftedThisBag) {
            return allOtherCategoriesAtHandLimit;
        }
        return true;
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
