package ti4.model.Franken;

import java.util.ArrayList;
import java.util.List;

public class FrankenBag {
    public FrankenBag()
    {}

    public List<FrankenItem> Contents = new ArrayList<>();


    public String toStoreString()
    {
        StringBuilder sb = new StringBuilder();
        for (FrankenItem item: Contents) {
            sb.append(item.getAlias());
            sb.append(",");
        }

        return sb.toString();
    }

    public int getCategoryCount(FrankenItem.Category cat) {
        int count = 0;
        for (FrankenItem item: Contents) {
            if (item.ItemCategory == cat) {
                count++;
            }
        }
        return count;
    }
}
