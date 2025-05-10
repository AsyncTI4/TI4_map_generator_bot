package ti4.draft.items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.AbilityModel;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.service.emoji.TI4Emoji;

public class AbilityDraftItem extends DraftItem {
    public AbilityDraftItem(String itemId) {
        super(Category.ABILITY, itemId);
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getAbilityModel().getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        AbilityModel abilityModel = getAbilityModel();
        StringBuilder sb = new StringBuilder();
        if (abilityModel.getPermanentEffect().isPresent()) {
            sb.append(abilityModel.getPermanentEffect().get()).append("\n");
        }
        if (abilityModel.getWindow().isPresent() && abilityModel.getWindowEffect().isPresent()) {
            sb.append("*").append(abilityModel.getWindow().get()).append(":* ").append(abilityModel.getWindowEffect().get());
        }
        return sb.toString();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return getAbilityModel().getFactionEmoji();
    }

    @JsonIgnore
    private AbilityModel getAbilityModel() {
        return Mapper.getAbility(ItemId);
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = buildAllItems(factions, game);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.ABILITY);
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftItem.Category.ABILITY);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            String[] results = game.getStoredValue("bannedAbilities").split("finSep");
            for (String ability : faction.getAbilities()) {
                if (Arrays.asList(results).contains(ability)) {
                    continue;
                }
                allItems.add(DraftItem.generate(DraftItem.Category.ABILITY, ability));
            }
        }
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            for (String ability : faction.getAbilities()) {
                allItems.add(DraftItem.generate(DraftItem.Category.ABILITY, ability));
            }
        }
        return allItems;
    }
}
