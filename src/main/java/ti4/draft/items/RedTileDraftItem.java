package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.draft.DraftCategory;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;

public class RedTileDraftItem extends TileDraftItem {

    public RedTileDraftItem(String itemId) {
        super(DraftCategory.REDTILE, itemId);
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return MiscEmojis.Supernova;
    }
}
