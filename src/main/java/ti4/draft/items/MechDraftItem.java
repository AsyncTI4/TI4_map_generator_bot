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

public class MechDraftItem extends DraftItem {

    public MechDraftItem(String itemId) {
        super(DraftCategory.MECH, itemId);
    }

    private UnitModel getUnit() {
        return Mapper.getUnit(getItemId());
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getUnit().getNameRepresentation();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getUnit().getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        UnitModel unit = getUnit();
        StringBuilder sb = new StringBuilder("Cost: ").append(UnitDescription.cost(unit));
        UnitDescription.appendCombat(sb, unit);
        sb.append(' ');
        UnitDescription.appendSustainAndAfb(sb, unit);
        UnitDescription.appendProduction(sb, unit);
        if (unit.getAbility().isPresent()) sb.append(unit.getAbility().get());
        return sb.toString();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return UnitEmojis.mech;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        return draftable(DraftCategory.MECH, buildAllItems(factions));
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        return buildFactionUnitItems(DraftCategory.MECH, "mech", factions);
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        return draftable(DraftCategory.MECH, buildAllItems(withoutBannedFactions(factions, game, "bannedMechs")));
    }
}
