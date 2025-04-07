package ti4.draft.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.UnitModel;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;

public class MechDraftItem extends DraftItem {
    public MechDraftItem(String itemId) {
        super(Category.MECH, itemId);
    }

    private UnitModel getUnit() {
        return Mapper.getUnit(ItemId);
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return "Mech - " + getUnit().getName();
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
                .append("x").append(unit.getAfbDieCount())
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
        return UnitEmojis.mech;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.MECH);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        Map<String, UnitModel> allUnits = Mapper.getUnits();
        for (FactionModel faction : factions) {
            var units = faction.getUnits();
            units.removeIf((String unit) -> !"mech".equals(allUnits.get(unit).getBaseType()));
            allItems.add(DraftItem.generate(Category.MECH, units.getFirst()));
        }
        return allItems;
    }
}
