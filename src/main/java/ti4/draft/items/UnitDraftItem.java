package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.model.UnitModel;
import ti4.service.emoji.TI4Emoji;

public class UnitDraftItem extends DraftItem {

    public UnitDraftItem(String itemId) {
        super(Category.UNIT, itemId);
    }

    private UnitModel getUnit() {
        return Mapper.getUnit(ItemId);
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return "Unit - " + getUnit().getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        UnitModel unit = getUnit();
        StringBuilder sb = new StringBuilder();
        sb.append("Cost: ");
        float cost = unit.getCost();
        sb.append(cost == (int) cost ? "" + (int) cost : "" + cost); // type shenanigans
        sb.append(" Combat: ");
        sb.append(unit.getCombatHitsOn());
        if (unit.getCombatDieCount() > 1) {
            sb.append("x").append(unit.getCombatDieCount());
        }
        sb.append(" ");
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
        if (unit.getProductionValue() > 0) {
            sb.append("PRODUCTION ");
            sb.append(unit.getProductionValue());
            sb.append(" ");
        }
        if (unit.getAbility().isPresent()) sb.append(unit.getAbility().get());
        return sb.toString();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return getUnit().getUnitEmoji();
    }

    public static List<DraftItem> buildAllDraftableItems() {
        List<DraftItem> allItems = buildAllItems();
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.UNIT);
        return allItems;
    }

    public static List<DraftItem> buildAllItems() {
        List<DraftItem> allItems = new ArrayList<>();
        Map<String, UnitModel> allUnits = Mapper.getUnits();
        for (String unitID : allUnits.keySet()) {
            UnitModel mod = allUnits.get(unitID);
            if (mod.getFaction().isPresent() && mod.getSource() == ComponentSource.twilights_fall) {
                FactionModel faction = Mapper.getFaction(mod.getFaction().get());
                if (faction != null && faction.getSource() != ComponentSource.twilights_fall) {
                    allItems.add(generate(Category.UNIT, unitID));
                }
            }
        }
        return allItems;
    }
}
