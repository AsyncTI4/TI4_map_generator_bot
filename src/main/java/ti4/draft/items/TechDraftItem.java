package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.helpers.PatternHelper;
import ti4.image.Mapper;
import ti4.model.DeckModel;
import ti4.model.DraftErrataModel;
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
        List<DraftItem> allItems = buildAllItems(factions);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.TECH);
        return allItems;
    }

    public static List<DraftItem> buildAllItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            for (var tech : faction.getFactionTech()) {
                allItems.add(generate(DraftCategory.TECH, tech));
            }
        }
        return allItems;
    }

    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = buildAllItems(factions, game);
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, DraftCategory.TECH, game.isTwilightsFallMode());
        return allItems;
    }

    private static List<DraftItem> buildAllItems(List<FactionModel> factions, Game game) {
        List<DraftItem> allItems = new ArrayList<>();
        String[] results = PatternHelper.FIN_SEPERATOR_PATTERN.split(game.getStoredValue("bannedTechs"));
        if (game.isTwilightsFallMode()) {
            DeckModel deck = Mapper.getDeck(game.getAbilitySpliceDeckID());
            List<String> allCards = new ArrayList<>();
            allCards.addAll(deck.getCardIDs());
            if (game.isTwilightDS()) {
                for (TechnologyModel tech : Mapper.getTechs().values()) {
                    if (tech.getSource() == ComponentSource.twilight_ds) {
                        allCards.add(tech.getID());
                    }
                }
            }
            for (String id : allCards) {
                if (!Arrays.asList(results).contains(id)) {
                    allItems.add(generate(DraftCategory.TECH, id));
                }
            }
        } else {
            for (FactionModel faction : factions) {
                for (var tech : faction.getFactionTech()) {
                    if (Arrays.asList(results).contains(tech)) {
                        continue;
                    }
                    allItems.add(generate(DraftCategory.TECH, tech));
                }
            }
        }
        return allItems;
    }
}
