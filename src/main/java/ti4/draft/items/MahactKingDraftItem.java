package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.model.UnitModel;
import ti4.service.draft.draftables.MahactKingDraftable;
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
        if (faction == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(faction.getFactionName()).append("\n> Commodities: ").append(faction.getCommodities());
        for (String unitId : faction.getUnits()) {
            UnitModel unit = Mapper.getUnit(unitId);
            if (unit == null) {
                continue;
            }
            Units.UnitType type = unit.getUnitType();
            if (type != Units.UnitType.Flagship && type != Units.UnitType.Mech) {
                continue;
            }

            sb.append("\n> ").append(type.humanReadableName()).append(": ");
            UnitDescription.appendCombat(sb, unit);
            sb.append(' ');
            UnitDescription.appendSustainAndAfb(sb, unit);
            UnitDescription.appendProduction(sb, unit);
            if (unit.getAbility().isPresent()) {
                sb.append(unit.getAbility().get());
            }
        }
        return sb.toString();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        FactionModel faction = Mapper.getFaction(getItemId());
        if (faction != null) {
            return FactionEmojis.getFactionIcon(faction.getHomebrewReplacesID().orElse(faction.getAlias()));
        }
        return FactionEmojis.getFactionIcon(getItemId());
    }

    public static List<DraftItem> buildAllDraftableItems() {
        return draftable(DraftCategory.MAHACTKING, buildAllItems());
    }

    public static List<DraftItem> buildAllDraftableItems(Game game) {
        List<DraftItem> allItems = draftable(DraftCategory.MAHACTKING, buildAllItems(game));
        if (game.isTkNovaCup() && "onePerColor".equals(game.getStoredValue(Constants.TK_NOVA_CUP + "_setup_option"))) {
            List<DraftItem> output = new ArrayList<>();
            for (DraftItem item : allItems) {
                String id = MahactKingDraftable.switchFactionSet(item.getItemId());
                if (output.stream().map(DraftItem::getItemId).noneMatch(id::equals)) {
                    output.add(item);
                }
            }
            return output;
        }
        return allItems;
    }

    public static List<DraftItem> buildAllItems() {
        return generateAll(
                DraftCategory.MAHACTKING,
                Mapper.getFactions().values().stream()
                        .filter(faction -> faction.getSource() == ComponentSource.twilights_fall)
                        .map(FactionModel::getID));
    }

    public static List<DraftItem> buildAllItems(Game game) {
        return generateAll(DraftCategory.MAHACTKING, getAllFactions(game).map(FactionModel::getAlias));
    }

    public static Stream<FactionModel> getAllFactions(Game game) {
        Set<ComponentSource> sources = new HashSet<>(Set.of(ComponentSource.twilights_fall));

        // Homebrew:
        if (game.isTkNovaCup()) {
            switch (game.getStoredValue(Constants.TK_NOVA_CUP + "_setup_option")) {
                case "onePerColor", "unrestricted" -> sources.add(ComponentSource.tk_nova_cup);
                case "onlyNova" -> {
                    sources.remove(ComponentSource.twilights_fall);
                    sources.add(ComponentSource.tk_nova_cup);
                }
            }
        }
        if (game.isTfBr()) {
            sources.add(ComponentSource.tf_br);
        }

        return Mapper.getFactions().values().stream().filter(f -> sources.contains(f.getSource()));
    }
}
