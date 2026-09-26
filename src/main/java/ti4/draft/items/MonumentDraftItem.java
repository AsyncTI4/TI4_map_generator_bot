package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.draft.FrankenDraft;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.model.UnitModel;
import ti4.service.emoji.TI4Emoji;

public class MonumentDraftItem extends DraftItem {

    public MonumentDraftItem(String itemId) {
        super(DraftCategory.MONUMENT, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getMonument().getNameRepresentation();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getMonument().getName();
    }

    @JsonIgnore
    @Override
    protected String getLongDescriptionImpl() {
        UnitModel monument = getMonument();
        String ability = monument.getAbility().orElse("");
        return ability.isBlank() ? "" : ability + "\n";
    }

    @JsonIgnore
    @Override
    protected String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return getMonument().getUnitEmoji();
    }

    public static List<DraftItem> buildAllDraftableItems(Game game) {
        List<DraftItem> monuments = new ArrayList<>(buildAllItems(game));
        DraftErrataModel.filterUndraftablesAndShuffle(monuments, DraftCategory.MONUMENT);
        return monuments;
    }

    public static List<DraftItem> buildAllDraftableItems() {
        List<DraftItem> monuments = new ArrayList<>(buildAllItems());
        DraftErrataModel.filterUndraftablesAndShuffle(monuments, DraftCategory.MONUMENT);
        return monuments;
    }

    public static List<DraftItem> buildAllItems(Game game) {
        if (game == null || !game.isMonumentsMode()) {
            return List.of();
        }
        return Mapper.getUnits().values().stream()
                .filter(unit -> isAvailable(game, unit.getId()))
                .map(unit -> new MonumentDraftItem(unit.getId()))
                .map(DraftItem.class::cast)
                .toList();
    }

    public static List<DraftItem> buildAllItems() {
        return Mapper.getUnits().values().stream()
                .filter(unit -> isMonument(unit) && !"rhodun_monumentback".equals(unit.getId()))
                .map(unit -> new MonumentDraftItem(unit.getId()))
                .map(DraftItem.class::cast)
                .toList();
    }

    public static boolean isAvailable(Game game, String monumentId) {
        UnitModel monument = Mapper.getUnit(monumentId);
        if (game == null
                || !game.isMonumentsMode()
                || !isMonument(monument)
                || "rhodun_monumentback".equals(monumentId)) {
            return false;
        }
        String factionId = monument.getFaction().orElse(null);
        FactionModel faction = factionId == null ? null : Mapper.getFaction(factionId);
        if (faction == null || faction.getSource().isTwilightFallish()) {
            return false;
        }
        return FrankenDraft.getDraftableFactionsForGame(game).stream()
                .map(FactionModel::getAlias)
                .anyMatch(factionId::equals);
    }

    private UnitModel getMonument() {
        return Mapper.getUnit(getItemId());
    }

    private static boolean isMonument(UnitModel unit) {
        return unit != null && unit.getSource() == ComponentSource.monuments && unit.getIsMonument();
    }
}
