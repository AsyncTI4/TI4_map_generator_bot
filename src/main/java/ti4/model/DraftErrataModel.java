package ti4.model;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.model.Source.ComponentSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DraftErrataModel implements ModelInterface {
    @Override
    public boolean isValid() {
        return ItemCategory != null
            && ItemId != null
            && source != null;
    }

    @Override
    public String getAlias() {
        return ItemCategory.toString() + ":" + ItemId;
    }

    // The type of item to be drafted
    public DraftItem.Category ItemCategory;

    // The system ID of the item. Only convert this to player-readable text when necessary
    public String ItemId;

    public DraftErrataModel[] AdditionalComponents;
    public DraftErrataModel[] OptionalSwaps;
    public boolean Undraftable;

    public boolean AlwaysAddToPool;

    private ComponentSource source;

    public DraftErrataModel(String alias) {
        String[] split = alias.split(":");
        ItemCategory = DraftItem.Category.valueOf(split[0]);
        ItemId = split[1];
    }

    public DraftErrataModel() {

    }

    public static void filterUndraftablesAndShuffle(List<DraftItem> items, DraftItem.Category listCategory) {
        Map<String, DraftErrataModel> frankenErrata = Mapper.getFrankenErrata();
        items.removeIf((DraftItem item) -> frankenErrata.containsKey(item.getAlias()) && frankenErrata.get(item.getAlias()).Undraftable);
        items.addAll(DraftItem.GetAlwaysIncludeItems(listCategory));
        Collections.shuffle(items);
    }

    public List<DraftErrataModel> getAdditionalComponents() {
        return List.of(AdditionalComponents);
    }

    public List<DraftErrataModel> getOptionalSwaps() {
        return List.of(OptionalSwaps);
    }

    public ComponentSource getSource() {
        return source;
    }
}
