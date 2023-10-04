package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Helper;
import ti4.model.TechnologyModel;

public class TechDraftItem extends DraftItem {
    public TechDraftItem(String itemId) {
        super(Category.TECH, itemId);
    }

    @Override
    public String getShortDescription() {
        return getTech().getName();
    }

    private TechnologyModel getTech() {
        return Mapper.getTech(ItemId);
    }

    @Override
    public String getLongDescription() {
        return getTech().getText() + "\n**Requirements:** " + getTech().getRequirementsEmoji();
    }

    @Override
    public String getItemEmoji() {
        TechnologyModel model = getTech();
        return Helper.getEmojiFromDiscord(model.getType().toString().toLowerCase() + "tech");
    }
}
