package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.stream.IntStream;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.service.emoji.MiltyDraftEmojis;
import ti4.service.emoji.TI4Emoji;

public class SpeakerOrderDraftItem extends DraftItem {
    public SpeakerOrderDraftItem(String itemId) {
        super(DraftCategory.DRAFTORDER, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getItemEmoji() + " " + getShortDescription();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return "Table Position " + getItemId();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        if ("1".equals(getItemId())) {
            return "Speaker Token + Table Position 1";
        }
        return "Table Position " + getItemId();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        try {
            return MiltyDraftEmojis.getSpeakerPickEmoji(getSpeakerOrder());
        } catch (Exception e) {
            return null;
        }
    }

    @JsonIgnore
    public int getSpeakerOrder() {
        return Integer.parseInt(getItemId());
    }

    public static List<DraftItem> buildAllDraftableItems(Game game) {
        return draftable(
                DraftCategory.DRAFTORDER,
                generateAll(
                        DraftCategory.DRAFTORDER,
                        IntStream.rangeClosed(1, game.getRealPlayers().size()).mapToObj(Integer::toString)));
    }
}
