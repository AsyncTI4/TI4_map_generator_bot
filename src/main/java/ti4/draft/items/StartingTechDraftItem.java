package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.model.FactionModel;
import ti4.model.TechnologyModel;

import java.util.ArrayList;
import java.util.List;

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
    public String getLongDescription() {
        List<String> techs = startingTechs();
        StringBuilder builder = new StringBuilder();
        TechnologyModel tech;
        for (int i = 0; i < techs.size()-1; i++) {
            tech = Mapper.getTech(techs.get(i));
            builder.append(Helper.getEmojiFromDiscord(tech.getType().toString().toLowerCase() + "tech"));
            builder.append(" ");
            builder.append(tech.getName());
            builder.append(", ");
        }
        tech = Mapper.getTech(techs.get(techs.size()-1));
        builder.append(Helper.getEmojiFromDiscord(tech.getType().toString().toLowerCase() + "tech"));
        builder.append(" ");
        builder.append(tech.getName());
        return String.join(",\n", builder.toString());
    }

    private List<String> startingTechs() {
        if (ItemId.equals("argent")) {

        } else if (ItemId.equals("winnu")) {

        }
        return getFaction().getStartingTech();
    }

    @Override
    public String getItemEmoji() {
        return Emojis.UnitTechSkip;
    }
}
