package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.map.Faction;
import ti4.model.AbilityModel;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;

import java.util.ArrayList;
import java.util.List;

public class AbilityDraftItem extends DraftItem {
    public AbilityDraftItem(String itemId) {
        super(Category.ABILITY, itemId);
    }

    @Override
    public String getShortDescription() {
        return getAbilityModel().getName();
    }

    @Override
    public String getLongDescriptionImpl() {
        AbilityModel abilityModel = getAbilityModel();
        if (abilityModel.getPermanentEffect().isPresent()) {    
            return abilityModel.getPermanentEffect().get();
        } else if (abilityModel.getWindow().isPresent() && abilityModel.getWindowEffect().isPresent()) {
            return "*" + abilityModel.getWindow().get() + ":* " + abilityModel.getWindowEffect().get();
        }
        return "";
    }

    @Override
    public String getItemEmoji() {
        return getAbilityModel().getFactionEmoji();
    }

    private AbilityModel getAbilityModel() {
        return Mapper.getAbility(ItemId);
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            for (var ability : faction.getAbilities()) {
                allItems.add(DraftItem.Generate(DraftItem.Category.ABILITY, ability));
            }
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.ABILITY);
        return allItems;
    }
}
