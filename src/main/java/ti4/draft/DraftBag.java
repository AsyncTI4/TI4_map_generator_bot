package ti4.draft;

import java.util.ArrayList;
import java.util.List;

public class DraftBag {

    public List<DraftItem> Contents = new ArrayList<>();


    public String toStoreString()
    {
        StringBuilder sb = new StringBuilder();
        for (DraftItem item: Contents) {
            sb.append(item.getAlias());
            sb.append(",");
        }

        return sb.toString();
    }

    public static DraftBag fromStoreString(String storeString) {
        DraftBag bag = new DraftBag();
        for (String item : storeString.split(",")) {
            bag.Contents.add(DraftItem.generateFromAlias(item));
        }
        return bag;
    }

    public int getCategoryCount(DraftItem.Category cat) {
        int count = 0;
        for (DraftItem item: Contents) {
            if (item.ItemCategory == cat) {
                count++;
            }
        }
        return count;
    }
}
