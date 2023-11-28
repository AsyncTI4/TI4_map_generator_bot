package ti4.draft.items;

import java.util.List;
import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.FactionModel;
import ti4.model.TechnologyModel;

public class StartingTechDraftItem extends DraftItem {
    public StartingTechDraftItem(String itemId) {
        super(Category.STARTINGTECH, itemId);
    }

    private FactionModel getFaction() {
        if ("keleres".equals(ItemId)) {
            return Mapper.getFaction("keleresa");
        }
        return Mapper.getFaction(ItemId);
    }

    @Override
    public String getShortDescription() {
        return getFaction().getFactionName() + " Starting Tech";
    }

    @Override
    public String getLongDescriptionImpl() {
        if ("winnu".equals(ItemId)) {
            return "Choose any 1 technology that has no prerequisites.";
        } else if ("argent".equals(ItemId)) {
            return "Choose TWO of the following: :Biotictech: Neural Motivator, :Cybernetictech: Sarween Tools, :Warfaretech: Plasma Scoring";
        } else if ("keleres".equals(ItemId)) {
            return "Choose 2 non-faction technologies owned by other players.";
        } else if ("bentor".equals(ItemId)) {
            return "Choose 2 of the following: Psychoarchaeology, Dark Energy Tap, and Scanlink Drone Network.";
        } else if ("celdauri".equals(ItemId)) {
            return "Choose 2 of the following: Antimass Deflectors, Sarween Tools, Plasma Scoring";
        } else if ("cheiran".equals(ItemId)) {
            return "Choose 1 of the following: Magen Defense Grid, Self-Assembly Routines";
        } else if ("edyn".equals(ItemId)) {
            return "Choose any 3 technologies that have different colors and no prerequisites.";
        } else if ("ghoti".equals(ItemId)) {
            return "Choose 1 of the following: Gravity Drive, Sling Relay.";
        } else if ("gledge".equals(ItemId)) {
            return "Choose 2 of the following: Psychoarchaeology, Scanlink Drone Network, AI Development Algorithm.";
        } else if ("kjalengard".equals(ItemId)) {
            return "Choose 1 non-faction unit upgrade.";
        } else if ("kolume".equals(ItemId)) {
            return "Choose 1 of the following: Graviton Laser System, Predictive Intelligence.";
        } else if ("kyro".equals(ItemId)) {
            return "Choose 1 of the following: Daxcive Animators, Bio-Stims.";
        } else if ("lanefir".equals(ItemId)) {
            return "Choose 2 of the following: Dark Energy Tap, Scanlink Drone Network, AI Development Algorithm.";
        } else if ("nokar".equals(ItemId)) {
            return "Choose 2 of the following: Psychoarchaeology, Dark Energy Tap, AI Development Algorithm.";
        } else if ("tnelis".equals(ItemId)) {
            return "Choose 2 of the following: Neural Motivator, Antimass Deflectors, Plasma Scoring.";
        } else if ("vaden".equals(ItemId)) {
            return "Choose 2 of the following: Neural Motivator, Antimass Deflectors, Sarween Tools.";
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
