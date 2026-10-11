package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.model.UnitModel;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;

public class FlagshipDraftItem extends DraftItem {

    public FlagshipDraftItem(String itemId) {
        super(DraftCategory.FLAGSHIP, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getUnit().getNameRepresentation();
    }

    @JsonIgnore
    private UnitModel getUnit() {
        return Mapper.getUnit(getItemId());
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        UnitModel unit = getUnit();
        if (unit == null) {
            return getAlias();
        }
        return unit.getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        UnitModel unit = getUnit();
        if (unit == null) {
            return getItemId();
        }
        StringBuilder sb = new StringBuilder("Cost: ").append(UnitDescription.cost(unit));
        UnitDescription.appendCombat(sb, unit);
        sb.append(" Move: ");
        sb.append(unit.getMoveValue());
        sb.append(" Capacity: ");
        sb.append(unit.getCapacityValue());
        sb.append(' ');
        UnitDescription.appendSustainAndAfb(sb, unit);
        if (unit.getAbility().isPresent()) sb.append(unit.getAbility().get());
        return sb.toString();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return UnitEmojis.flagship;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        return draftable(DraftCategory.FLAGSHIP, buildAllItems(factions));
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        return buildFactionUnitItems(DraftCategory.FLAGSHIP, "flagship", factions);
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        return draftable(DraftCategory.FLAGSHIP, buildAllItems(withoutBannedFactions(factions, game, "bannedFSs")));
    }
}
