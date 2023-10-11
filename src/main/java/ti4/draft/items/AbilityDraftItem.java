package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Helper;

public class AbilityDraftItem extends DraftItem {
    public AbilityDraftItem(String itemId) {
        super(Category.ABILITY, itemId);
    }

    @Override
    public String getShortDescription() {
        String[] split = getAbilityStringSplit();
        return split[0];
    }

    @Override
    public String getLongDescriptionImpl() {
        String[] split = getAbilityStringSplit();
        if (!split[2].equals(" ")) {
            return split[2];
        }
        else {
            return "*" + split[3] + ":* " + split[4];
        }
    }

    @Override
    public String getItemEmoji() {
        return Helper.getFactionIconFromDiscord(getAbilityStringSplit()[1]);
    }

    private String[] getAbilityStringSplit() {
        return Mapper.getAbility(ItemId).split("\\|");
    }
}
