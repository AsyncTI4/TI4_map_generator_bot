package ti4.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeckModel implements ModelInterface {
    private String alias;
    private String name;
    private String type;
    private String description;
    private List<String> cardIDs;

    public DeckModel() {}

    public boolean isValid() {
        return alias != null
            && name != null
            && type != null
            && description != null
            && cardIDs != null;
    }

    public String getAlias() {
        return alias;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getCardIDs() {
        return cardIDs;
    }

    public List<String> getShuffledCardList() {
        List<String> cardList = new ArrayList<>(cardIDs);
        Collections.shuffle(cardList);
        return cardList;
    }

    public int getCardCount() {
        return cardIDs.size();
    }
}
