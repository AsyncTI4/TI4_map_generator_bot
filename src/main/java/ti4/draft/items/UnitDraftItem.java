package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.image.Mapper;
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
            sb.append("Cost: ").append(UnitDescription.cost(unit));
        }
        if (unit.getCombatHitsOn() > 0) {
            UnitDescription.appendCombat(sb, unit);
            sb.append(' ');
        }
        UnitDescription.appendSustainAndAfb(sb, unit);
        UnitDescription.appendDice(sb, "SPACE CANNON", unit.getSpaceCannonHitsOn(), unit.getSpaceCannonDieCount());
        UnitDescription.appendDice(sb, "BOMBARDMENT", unit.getBombardHitsOn(), unit.getBombardDieCount());
        UnitDescription.appendProduction(sb, unit);
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
        if (unit.getAbility().isPresent()) sb.append(unit.getAbility().get()).append(' ');
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
        return draftable(DraftCategory.UNIT, buildAllItems(game), game.isTwilightsFallMode());
    }

    public static List<DraftItem> buildAllDraftableItems() {
        return draftable(DraftCategory.UNIT, buildAllItems());
    }

    public static List<DraftItem> buildAllItems(Game game) {
        return buildAllItems(game.getUnitSpliceDeckID());
    }

    public static List<DraftItem> buildAllItems() {
        return buildAllItems("tf_units");
    }

    public static List<DraftItem> buildAllItems(String deck) {
        return generateAll(DraftCategory.UNIT, Mapper.getDeck(deck).getCardIDs().stream());
    }
}
