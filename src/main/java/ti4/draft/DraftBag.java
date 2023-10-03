package ti4.draft;

import java.util.ArrayList;
import java.util.List;

public class DraftBag {
    public DraftBag()
    {}

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
}
