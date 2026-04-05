package ti4.draft;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import ti4.draft.items.SpeakerOrderDraftItem;

public class DraftBag {

    public List<DraftItem> Contents = new ArrayList<>();

    public String toStoreString() {
        StringBuilder sb = new StringBuilder();
        for (DraftItem item : Contents) {
            sb.append(item.getAlias());
            sb.append(",");
        }

        return sb.toString();
    }

    public List<DraftItem> getCategory(DraftCategory cat) {
        return Contents.stream()
                .filter(i -> i.getItemCategory() == cat)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public int getCategoryCount(DraftCategory cat) {
        return getCategory(cat).size();
    }

    public Integer getDraftedSpeakerOrder() {
        Optional<DraftItem> order =
                getCategory(DraftCategory.DRAFTORDER).stream().findFirst();
        return order.map(i -> i instanceof SpeakerOrderDraftItem s ? s.getSpeakerOrder() : null)
                .orElse(null);
    }

    public int getCategoryAppliedCount(List<String> appliedIDs, DraftCategory cat) {
        int count = 0;
        for (DraftItem item : getCategory(cat)) {
            if (appliedIDs.contains(item.getAlias())) {
                count++;
            }
        }
        return count;
    }
}
