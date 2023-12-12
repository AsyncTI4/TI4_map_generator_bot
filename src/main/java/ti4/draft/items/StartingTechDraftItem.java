package ti4.draft.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.DraftErrataModel;
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

    public static Map<String, String> selectableStartingTechs = Map.ofEntries(
            Map.entry("winnu", "Choose any 1 technology that has no prerequisites."),
            Map.entry("argent", "Choose 2 of the following: :Biotictech: Neural Motivator, :Cybernetictech: Sarween Tools, :Warfaretech: Plasma Scoring"),
            Map.entry("keleresa", "Choose 2 non-faction technologies owned by other players."),
            Map.entry("bentor", "Choose 2 of the following: :Biotictech: Psychoarchaeology, :Propulsiontech: Dark Energy Tap, and :Cybernetictech: Scanlink Drone Network."),
            Map.entry("celdauri", "Choose 2 of the following: :Propulsiontech: Antimass Deflectors, :Cybernetictech: Sarween Tools, :Warfaretech: Plasma Scoring"),
            Map.entry("cheiran", "Choose 1 of the following: :Warfaretech: Magen Defense Grid, :Warfaretech: Self-Assembly Routines"),
            Map.entry("edyn", "Choose any 3 technologies that have different colors and no prerequisites."),
            Map.entry("ghoti", "Choose 1 of the following: :Propulsiontech: Gravity Drive, :Propulsiontech: Sling Relay."),
            Map.entry("gledge", "Choose 2 of the following: :Biotictech: Psychoarchaeology, :Cybernetictech: Scanlink Drone Network, :Warfaretech: AI Development Algorithm."),
            Map.entry("kjalengard", "Choose 1 non-faction unit upgrade."),
            Map.entry("kolume", "Choose 1 of the following: :Cybernetictech: Graviton Laser System, :Cybernetictech: Predictive Intelligence."),
            Map.entry("kyro", "Choose 1 of the following: :Biotictech: Daxcive Animators, :Biotictech: Bio-Stims."),
            Map.entry("lanefir", "Choose 2 of the following: :Propulsiontech: Dark Energy Tap, :Cybernetictech: Scanlink Drone Network, :Warfaretech: AI Development Algorithm."),
            Map.entry("nokar", "Choose 2 of the following: :Biotictech: Psychoarchaeology, :Propulsiontech: Dark Energy Tap, :Warfaretech: AI Development Algorithm."),
            Map.entry("tnelis", "Choose 2 of the following: :Biotictech: Neural Motivator, :Propulsiontech: Antimass Deflectors, :Warfaretech: Plasma Scoring."),
            Map.entry("vaden", "Choose 2 of the following: :Biotictech: Neural Motivator, :Propulsiontech: Antimass Deflectors, :Cybernetictech: Sarween Tools.")
    );
    @Override
    public String getLongDescriptionImpl() {
        if (selectableStartingTechs.containsKey(ItemId)) {
            return selectableStartingTechs.get(ItemId);
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


    public static List<DraftItem> buildAllDraftableItems(List<FactionModel> factions) {
        List<DraftItem> allItems = new ArrayList<>();
        for (FactionModel faction : factions) {
            allItems.add(DraftItem.Generate(Category.STARTINGTECH, faction.getAlias()));
        }
        DraftErrataModel.filterUndraftablesAndShuffle(allItems, Category.STARTINGTECH);
        return allItems;
    }
}
