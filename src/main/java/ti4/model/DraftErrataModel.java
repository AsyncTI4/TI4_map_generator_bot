package ti4.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DraftErrataModel implements ModelInterface {
    private DraftCategory itemCategory;
    private String itemId;
    private List<String> additionalComponents;
    private List<String> optionalSwaps;
    private Boolean undraftable;
    private String alternateText;
    private Boolean alwaysAddToPool;
    private ComponentSource source;

    public static DraftErrataModel blank() {
        return new DraftErrataModel(null, "", List.of(), List.of(), false, null, false, null);
    }

    @Override
    public boolean isValid() {
        return itemCategory != null && itemId != null && source != null;
    }

    @Override
    public String getAlias() {
        return itemCategory.toString() + ":" + itemId;
    }

    public String getAlternateText() {
        return Optional.ofNullable(alternateText).orElse("");
    }

    public List<DraftErrataModel> getAdditionalComponents() {
        if (additionalComponents == null) return List.of();
        return additionalComponents.stream()
                .map(Mapper::getFrankenErrata)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<DraftErrataModel> getOptionalSwaps() {
        if (optionalSwaps == null) {
            return List.of();
        }
        return optionalSwaps.stream()
                .map(Mapper::getFrankenErrata)
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean isAlwaysAddToPool() {
        return alwaysAddToPool != null && alwaysAddToPool;
    }

    private boolean isUndraftable() {
        return undraftable != null && undraftable;
    }

    public static void filterUndraftablesAndShuffle(List<DraftItem> items, DraftCategory listCategory) {
        Map<String, DraftErrataModel> frankenErrata = Mapper.getFrankenErrata();
        items.removeIf((DraftItem item) -> frankenErrata.containsKey(item.getAlias())
                && frankenErrata.get(item.getAlias()).isUndraftable());
        items.addAll(DraftItem.getAlwaysIncludeItems(listCategory));
        Set<DraftItem> itemsSet = Set.copyOf(items); // Remove duplicates
        items.clear();
        items.addAll(itemsSet);
        Collections.shuffle(items);
    }
}
