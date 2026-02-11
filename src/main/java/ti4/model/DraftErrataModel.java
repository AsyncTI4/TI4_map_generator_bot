package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;

public class DraftErrataModel implements ModelInterface {

    // The type of item to be drafted
    public DraftItem.Category itemCategory;

    // The system ID of the item. Only convert this to player-readable text when necessary
    public String itemId;

    public DraftErrataModel[] additionalComponents;
    public DraftErrataModel[] optionalSwaps;
    public boolean undraftable;
    public String alternateText;

    public boolean alwaysAddToPool;

    @Getter
    private ComponentSource source;

    public DraftErrataModel(String alias) {
        if (alias == null) return;
        String[] split = alias.split(":");
        itemCategory = DraftItem.Category.valueOf(split[0]);
        itemId = split[1];
    }

    public DraftErrataModel() {}

    public static void filterUndraftablesAndShuffle(List<DraftItem> items, DraftItem.Category listCategory) {
        Map<String, DraftErrataModel> frankenErrata = Mapper.getFrankenErrata();
        items.removeIf((DraftItem item) ->
                frankenErrata.containsKey(item.getAlias()) && frankenErrata.get(item.getAlias()).undraftable);
        items.addAll(DraftItem.getAlwaysIncludeItems(listCategory));
        Set<DraftItem> itemsSet = Set.copyOf(items); // Remove duplicates
        items.clear();
        items.addAll(itemsSet);
        Collections.shuffle(items);
    }

    @JsonIgnore
    public String getAlternateText() {
        return Optional.ofNullable(alternateText).orElse("");
    }

    @JsonIgnore
    public List<DraftErrataModel> getAdditionalComponents() {
        return List.of(additionalComponents);
    }

    @JsonIgnore
    public List<DraftErrataModel> getOptionalSwaps() {
        return List.of(optionalSwaps);
    }

    public boolean searchSource(ComponentSource searchSource) {
        return (searchSource == null || (source != null && source == searchSource));
    }

    @Override
    public boolean isValid() {
        return itemCategory != null && itemId != null && source != null;
    }

    @Override
    public String getAlias() {
        return itemCategory.toString() + ":" + itemId;
    }
}
