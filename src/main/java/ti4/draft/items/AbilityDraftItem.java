package ti4.draft.items;

import org.apache.commons.lang3.StringUtils;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Helper;
import ti4.model.AbilityModel;

public class AbilityDraftItem extends DraftItem {
    public AbilityDraftItem(String itemId) {
        super(Category.ABILITY, itemId);
    }

    @Override
    public String getShortDescription() {
        return getAbilityModel().getId();
    }

    @Override
    public String getLongDescriptionImpl() {
        AbilityModel abilityModel = getAbilityModel();
        if (StringUtils.isNotBlank(abilityModel.getPermanentEffect().orElse(""))) {    
            return abilityModel.getPermanentEffect().get();
        }
        else {
            return "*" + abilityModel.getWindow() + ":* " + abilityModel.getWindowEffect();
        }
    }

    @Override
    public String getItemEmoji() {
        return Helper.getFactionIconFromDiscord(getAbilityModel().getFactionEmoji());
    }

    private AbilityModel getAbilityModel() {
        return Mapper.getAbility(ItemId);
    }
}
