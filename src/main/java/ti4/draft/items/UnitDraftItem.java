package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.DraftErrataModel;
import ti4.model.UnitModel;
import ti4.service.emoji.TI4Emoji;

public class UnitDraftItem extends DraftItem {

    public UnitDraftItem(String itemId) {
        super(DraftCategory.UNIT, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getUnit().getNameRepresentation();
    }

    private UnitModel getUnit() {
        return Mapper.getUnit(getItemId());
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getUnit().getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        UnitModel unit = getUnit();
        StringBuilder sb = new StringBuilder();
        if (unit.getCost() > 0) {
            sb.append("Cost: ");
            float cost = unit.getCost();
            sb.append(cost == (int) cost ? "" + (int) cost : "" + cost); // type shenanigans
        }
        if (unit.getCombatHitsOn() > 0) {
            sb.append(" Combat: ");
            sb.append(unit.getCombatHitsOn());
            if (unit.getCombatDieCount() > 1) {
                sb.append("x").append(unit.getCombatDieCount());
            }
            sb.append(" ");
        }

        if (unit.getSustainDamage()) {
            sb.append("SUSTAIN DAMAGE ");
        }
        if (unit.getAfbDieCount() > 0) {
            sb.append("ANTI-FIGHTER BARRAGE ")
                    .append(unit.getAfbHitsOn())
                    .append("x")
                    .append(unit.getAfbDieCount())
                    .append(" ");
        }
        if (unit.getSpaceCannonDieCount() > 0) {
            sb.append("SPACE CANNON ")
                    .append(unit.getSpaceCannonHitsOn())
                    .append("x")
                    .append(unit.getSpaceCannonDieCount())
                    .append(" ");
        }
        if (unit.getProductionValue() > 0) {
            sb.append("PRODUCTION ");
            sb.append(unit.getProductionValue());
            sb.append(" ");
        }
        if (unit.getCapacityValue() > 0) {
            sb.append("Capacity ");
            sb.append(unit.getCapacityValue());
            sb.append(" ");
        }
        if (unit.getMoveValue() > 0) {
            sb.append("Move ");
            sb.append(unit.getMoveValue());
            sb.append(" ");
        }
        if (unit.getAbility().isPresent()) sb.append(unit.getAbility().get()).append(" ");
        if (unit.getFaction().isPresent()) {
            sb.append("Faction: ").append(unit.getFaction().get());
        }
        return sb + "\n";
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return getUnit().getUnitEmoji();
    }

    public static List<DraftItem> buildAllDraftableItems(Game game) {
        List<DraftItem> allItems = buildAllItems(game);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.UNIT);
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems() {
        List<DraftItem> allItems = buildAllItems();
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.UNIT);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(Game game) {
        return buildAllItems(game.getUnitSpliceDeckID());
    }

    public static List<DraftItem> buildAllItems() {
        return buildAllItems("tf_units");
    }

    public static List<DraftItem> buildAllItems(String deck) {
        List<DraftItem> allItems = new ArrayList<>();
        for (String id : Mapper.getDeck(deck).getCardIDs()) {
            allItems.add(generate(DraftCategory.UNIT, id));
        }
        return allItems;
    }
}
