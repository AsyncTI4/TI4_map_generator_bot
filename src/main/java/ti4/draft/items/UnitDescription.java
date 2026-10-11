package ti4.draft.items;

import ti4.model.UnitModel;

final class UnitDescription {

    private UnitDescription() {}

    static String cost(UnitModel unit) {
        float cost = unit.getCost();
        return cost == (int) cost ? "" + (int) cost : "" + cost;
    }

    static void appendCombat(StringBuilder sb, UnitModel unit) {
        sb.append(" Combat: ").append(unit.getCombatHitsOn());
        if (unit.getCombatDieCount() > 1) {
            sb.append("x").append(unit.getCombatDieCount());
        }
    }

    static void appendSustainAndAfb(StringBuilder sb, UnitModel unit) {
        if (unit.getSustainDamage()) {
            sb.append("SUSTAIN DAMAGE ");
        }
        appendDice(sb, "ANTI-FIGHTER BARRAGE", unit.getAfbHitsOn(), unit.getAfbDieCount());
    }

    static void appendDice(StringBuilder sb, String label, int hitsOn, int dieCount) {
        if (dieCount > 0) {
            sb.append(label)
                    .append(' ')
                    .append(hitsOn)
                    .append("x")
                    .append(dieCount)
                    .append(' ');
        }
    }

    static void appendProduction(StringBuilder sb, UnitModel unit) {
        if (unit.getProductionValue() > 0) {
            sb.append("PRODUCTION ").append(unit.getProductionValue()).append(' ');
        }
    }
}
