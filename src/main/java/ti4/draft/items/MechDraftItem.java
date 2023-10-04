package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.UnitModel;

public class MechDraftItem extends DraftItem {
    public MechDraftItem(String itemId) {
        super(Category.MECH, itemId);
    }

    private UnitModel getUnit() {
        if (ItemId.contains("mech")) {
            return Mapper.getUnit(ItemId);
        }
        return Mapper.getUnit(ItemId + "_mech");
    }

    @Override
    public String getShortDescription() {
        return "Mech - " + getUnit().getName();
    }

    @Override
    public String getLongDescription() {
        UnitModel unit = getUnit();
        StringBuilder sb = new StringBuilder();
        sb.append("Cost: ");
        sb.append(unit.getCost());
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
        sb.append(unit.getAbility());
        return sb.toString();
    }

    @Override
    public String getItemEmoji() {
        return Emojis.mech;
    }
}
