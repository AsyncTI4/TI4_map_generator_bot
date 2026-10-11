package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.stream.Stream;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel;
import ti4.service.emoji.TI4Emoji;

public class TechDraftItem extends DraftItem {

    public TechDraftItem(String itemId) {
        super(DraftCategory.TECH, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getTech().getNameRepresentation();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getTech().getName();
    }

    private TechnologyModel getTech() {
        return Mapper.getTech(getItemId());
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        if ("none".equalsIgnoreCase(getTech().getRequirementsEmoji())) {
            return getTech().getText();
        }
        return getTech().getText() + " " + getTech().getRequirementsEmoji();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return getTech().getSingleTechEmoji();
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        return draftable(DraftCategory.TECH, buildAllItems(factions));
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        return generateAll(DraftCategory.TECH, factions.stream().flatMap(faction -> faction.getFactionTech().stream()));
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> items = game.isTwilightsFallMode()
                ? generateAll(DraftCategory.TECH, twilightsFallTechs(game))
                : buildAllItems(factions);
        return draftable(DraftCategory.TECH, withoutBanned(items, game, "bannedTechs"), game.isTwilightsFallMode());
    }

    private static Stream<String> twilightsFallTechs(Game game) {
        Stream<String> deckTechs = Mapper.getDeck(game.getAbilitySpliceDeckID()).getCardIDs().stream();
        if (!game.isTwilightDS()) {
            return deckTechs;
        }
        return Stream.concat(
                deckTechs,
                Mapper.getTechs().values().stream()
                        .filter(tech -> tech.getSource() == ComponentSource.twilight_ds)
                        .map(TechnologyModel::getID));
    }
}
