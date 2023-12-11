package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.UnitModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FlagshipDraftItem extends DraftItem {
    public FlagshipDraftItem(String itemId) {
        super(Category.FLAGSHIP, itemId);
    }

    private UnitModel getUnit() {
        return Mapper.getUnit(ItemId);
    }

    @Override
    public String getShortDescription() {
        UnitModel unit = getUnit();
        if (unit == null) {
            return getAlias();
        }
        return "Flagship - " + unit.getName();
    }

    @Override
    public String getLongDescriptionImpl() {
        UnitModel unit = getUnit();
        if (unit == null) {
            return ItemId;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Cost: ");
        sb.append(unit.getCost());
        sb.append(" Combat: ");
        sb.append(unit.getCombatHitsOn());
        if (unit.getCombatDieCount() > 1) {
            sb.append("x").append(unit.getCombatDieCount());
        }
        sb.append(" Move: ");
        sb.append(unit.getMoveValue());
        sb.append(" Capacity: ");
        sb.append(unit.getCapacityValue());
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
        if (unit.getAbility().isPresent()) sb.append(unit.getAbility().get());
        return sb.toString();
    }

    @Override
    public String getItemEmoji() {
        return Emojis.flagship;
    }


    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        Map<String, UnitModel> allUnits = Mapper.getUnits();
        for (FactionModel faction : factions) {
            var units = faction.getUnits();
            units.removeIf((String unit) -> !"flagship".equals(allUnits.get(unit).getBaseType()));
            allItems.add(DraftItem.Generate(Category.FLAGSHIP, units.get(0)));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.FLAGSHIP);
        return allItems;
    }
}
