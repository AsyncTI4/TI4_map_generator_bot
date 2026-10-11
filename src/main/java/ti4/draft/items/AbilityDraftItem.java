package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.AbilityModel;
import ti4.model.FactionModel;
import ti4.service.emoji.TI4Emoji;

public class AbilityDraftItem extends DraftItem {

    public AbilityDraftItem(String itemId) {
        super(DraftCategory.ABILITY, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        AbilityModel ability = getAbilityModel();
        return ability.getNameRepresentation();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getAbilityModel().getName();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    protected String getLongDescriptionImpl() {
        AbilityModel abilityModel = getAbilityModel();
        StringBuilder sb = new StringBuilder();
        if (abilityModel.getPermanentEffect().isPresent()) {
            sb.append(abilityModel.getPermanentEffect().get()).append('\n');
        }
        if (abilityModel.getWindow().isPresent()
                && abilityModel.getWindowEffect().isPresent()) {
            sb.append("*")
                    .append(abilityModel.getWindow().get())
                    .append(":* ")
                    .append(abilityModel.getWindowEffect().get());
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
        return Mapper.getAbility(getItemId());
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        return draftable(
                DraftCategory.ABILITY,
                withoutBanned(buildAllItems(factions), game, "bannedAbilities"),
                game.isTwilightsFallMode());
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        return draftable(DraftCategory.ABILITY, buildAllItems(factions));
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        return generateAll(
                DraftCategory.ABILITY, factions.stream().flatMap(faction -> faction.getAbilities().stream()));
    }
}
