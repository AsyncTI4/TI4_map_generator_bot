package ti4.draft.items;

import java.util.List;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.model.FactionModel;
import ti4.model.TechnologyModel;

public class StartingTechDraftItem extends DraftItem {
    public StartingTechDraftItem(String itemId) {
        super(Category.STARTINGTECH, itemId);
    }

    private FactionModel getFaction() {
        if (ItemId.equals("keleres")) {
            return Mapper.getFactionSetup("keleresa");
        }
        return Mapper.getFactionSetup(ItemId);
    }

    @Override
    public String getShortDescription() {
        return getFaction().getFactionName() + " Starting Tech";
    }

    @Override
    public String getLongDescriptionImpl() {
        if (ItemId.equals("winnu")) {
            return "Choose any 1 technology that has no prerequisites.";
        } else if (ItemId.equals("argent")) {
            return "Choose TWO of the following: :Biotictech: Neural Motivator, :Cybernetictech: Sarween Tools, :Warfaretech: Plasma Scoring";
        } else if (ItemId.equals("keleres")) {
            return "Choose 2 non-faction technologies owned by other players.";
        }
        List<String> techs = startingTechs();
        StringBuilder builder = new StringBuilder();
        TechnologyModel tech;
        for (int i = 0; i < techs.size() - 1; i++) {
            tech = Mapper.getTech(techs.get(i));
            builder.append(Emojis.getEmojiFromDiscord(tech.getType().toString().toLowerCase() + "tech"));
            builder.append(" ");
            builder.append(tech.getName());
            builder.append(", ");
        }
        tech = Mapper.getTech(techs.get(techs.size() - 1));
        builder.append(Emojis.getEmojiFromDiscord(tech.getType().toString().toLowerCase() + "tech"));
        builder.append(" ");
        builder.append(tech.getName());
        return String.join(",\n", builder.toString());
    }

    private List<String> startingTechs() {
        return getFaction().getStartingTech();
    }

    @Override
    public String getItemEmoji() {
        return Emojis.UnitTechSkip;
    }
}
