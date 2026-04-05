package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.DraftErrataModel;
import ti4.model.FactionModel;
import ti4.model.TechnologyModel;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;

public class StartingTechDraftItem extends DraftItem {

    public StartingTechDraftItem(String itemId) {
        super(DraftCategory.STARTINGTECH, itemId);
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getFaction().getFactionEmoji() + " Starting Tech";
    }

    @JsonIgnore
    private FactionModel getFaction() {
        if ("keleres".equals(getItemId())) {
            return Mapper.getFaction("keleresa");
        }
        return Mapper.getFaction(getItemId());
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getFaction().getShortName() + " Starting Tech";
    }

    private static final Map<String, String> specialStartingTech = Map.ofEntries(
            Map.entry("winnu", "Choose any 1 technology that has no prerequisites."),
            Map.entry("keleresa", "Choose 2 non-faction technologies owned by other players."),
            Map.entry("deepwrought", "Research 2 technologies."),
            Map.entry("edyn", "Choose any 3 technologies that have different colors and no prerequisites."),
            Map.entry("kjalengard", "Choose 1 non-faction unit upgrade."));

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        if (specialStartingTech.containsKey(getItemId())) {
            return specialStartingTech.get(getItemId());
        }

        List<String> techs = startingTechs();
        if (techs != null) {
            techs = startingTechs().stream()
                    .map(Mapper::getTech)
                    .map(TechnologyModel::getNameRepresentation)
                    .toList();
            return String.join(", ", techs);
        } else if (getFaction().getStartingTechOptions() != null) {
            int choices = getFaction().getStartingTechAmount();
            techs = getFaction().getStartingTechOptions().stream()
                    .map(Mapper::getTech)
                    .map(TechnologyModel::getNameRepresentation)
                    .toList();
            return "Choose " + choices + " of the following: " + String.join(", ", techs);
        } else {
            return "Starting Tech of " + getItemId();
        }
    }

    private List<String> startingTechs() {
        return getFaction().getStartingTech();
    }

    @JsonIgnore
    @Override
    public TI4Emoji getItemEmoji() {
        return TechEmojis.UnitTechSkip;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.STARTINGTECH);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            allItems.add(generate(DraftCategory.STARTINGTECH, faction.getAlias()));
        }
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = buildAllItems(factions, game);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.STARTINGTECH);
        return allItems;
    }

    private static List<DraftItem> buildAllItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        String[] results = PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue("bannedStartingTechs"));
        for (FactionModel faction : factions) {
            if (Arrays.asList(results).contains(faction.getAlias())) {
                continue;
            }
            allItems.add(generate(DraftCategory.STARTINGTECH, faction.getAlias()));
        }
        return allItems;
    }
}
