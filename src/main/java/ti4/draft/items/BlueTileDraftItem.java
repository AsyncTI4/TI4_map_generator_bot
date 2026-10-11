package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.draft.DraftCategory;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;

public class BlueTileDraftItem extends TileDraftItem {

    public BlueTileDraftItem(String itemId) {
        super(DraftCategory.BLUETILE, itemId);
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return PlanetEmojis.SemLor;
    }
}
