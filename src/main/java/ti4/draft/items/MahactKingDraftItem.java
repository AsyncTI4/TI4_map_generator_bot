package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TI4Emoji;

public class MahactKingDraftItem extends DraftItem {

    public MahactKingDraftItem(String itemId) {
        super(DraftCategory.MAHACTKING, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        FactionModel faction = Mapper.getFaction(getItemId());
        if (faction == null) {
            return getAlias();
        }
        return getItemEmoji() + " " + faction.getFactionName().replace("\n", "");
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        FactionModel faction = Mapper.getFaction(getItemId());
        if (faction == null) {
            return getAlias();
        }
        return faction.getShortName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        FactionModel faction = Mapper.getFaction(getItemId());
        if (faction != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(faction.getFactionName())
                    .append("\n> Commodities: ")
                    .append(faction.getCommodities())
                    .append("\n> Flagship: ");
            UnitModel unit = Mapper.getUnit(getItemId() + "_flagship");
            sb.append(" Combat: ");
            sb.append(unit.getCombatHitsOn());
            if (unit.getCombatDieCount() > 1) {
                sb.append("x").append(unit.getCombatDieCount());
            }
            sb.append(' ');
            if (unit.getSustainDamage()) {
                sb.append("SUSTAIN DAMAGE ");
            }
            if (unit.getAfbDieCount() > 0) {
                sb.append("ANTI-FIGHTER BARRAGE ")
                        .append(unit.getAfbHitsOn())
                        .append("x")
                        .append(unit.getAfbDieCount())
                        .append(' ');
            }
            if (unit.getProductionValue() > 0) {
                sb.append("PRODUCTION ");
                sb.append(unit.getProductionValue());
                sb.append(' ');
            }
            if (unit.getCapacityValue() > 0) {
                sb.append("Capacity ");
                sb.append(unit.getCapacityValue());
                sb.append(' ');
            }
            if (unit.getMoveValue() > 0) {
                sb.append("Move ");
                sb.append(unit.getMoveValue());
                sb.append(' ');
            }
            if (unit.getAbility().isPresent()) sb.append(unit.getAbility().get());
            unit = Mapper.getUnit(getItemId() + "_mech");
            sb.append("\n> Mech: ");
            sb.append(" Combat: ");
            sb.append(unit.getCombatHitsOn());
            if (unit.getCombatDieCount() > 1) {
                sb.append("x").append(unit.getCombatDieCount());
            }
            sb.append(' ');
            if (unit.getSustainDamage()) {
                sb.append("SUSTAIN DAMAGE ");
            }
            if (unit.getAfbDieCount() > 0) {
                sb.append("ANTI-FIGHTER BARRAGE ")
                        .append(unit.getAfbHitsOn())
                        .append("x")
                        .append(unit.getAfbDieCount())
                        .append(' ');
            }
            if (unit.getProductionValue() > 0) {
                sb.append("PRODUCTION ");
                sb.append(unit.getProductionValue());
                sb.append(' ');
            }
            if (unit.getAbility().isPresent()) sb.append(unit.getAbility().get());
            return sb.toString();
        }
        return "";
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        FactionModel faction = Mapper.getFaction(getItemId());
        if (faction != null) {
            return FactionEmojis.getFactionIcon(getItemId());
        }
        return null;
    }

    public static List<DraftItem> buildAllDraftableItems() {
        List<DraftItem> allItems = buildAllItems();
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.MAHACTKING);
        return allItems;
    }

    public static List<DraftItem> buildAllItems() {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : Mapper.getFactions().values()) {
            if (faction.getSource() == ComponentSource.twilights_fall) {
                allItems.add(generate(DraftCategory.MAHACTKING, faction.getID()));
            }
        }
        return allItems;
    }
}
