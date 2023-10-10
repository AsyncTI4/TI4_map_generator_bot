package ti4.draft.items;

import ti4.draft.DraftItem;
import ti4.generator.Mapper;
import ti4.generator.PlanetHelper;
import ti4.generator.TileHelper;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.model.PlanetModel;
import ti4.model.PlanetTypeModel;
import ti4.model.TechSpecialtyModel;
import ti4.model.TileModel;

import java.util.List;

public class BlueTileDraftItem extends DraftItem {
    public BlueTileDraftItem(String itemId) {
        super(Category.BLUETILE, itemId);
    }

    @Override
    public String getShortDescription() {
        return TileHelper.getTile(ItemId).getName();
    }

    @Override
    public String getLongDescriptionImpl() {
        TileModel tile = TileHelper.getTile(ItemId);
        StringBuilder sb = new StringBuilder();
        List<String> planetIds = tile.getPlanetIds();
        for (int i = 0; i < planetIds.size()-1; i++) {
            buildPlanetString(Mapper.getPlanet(planetIds.get(i)), sb);
            sb.append(", ");
        }

        buildPlanetString(Mapper.getPlanet(planetIds.get(planetIds.size()-1)), sb);

        return sb.toString();
    }

    private void buildPlanetString(PlanetModel planet, StringBuilder sb) {
        sb.append(planet.getName());
        sb.append(planetTypeEmoji(planet.getPlanetType()));
        sb.append(" (");
        sb.append(planet.getResources()).append("/").append(planet.getInfluence());
        if (planet.isLegendary()) {
            sb.append("/").append(Emojis.LegendaryPlanet);
        }
        if (planet.getTechSpecialties() != null) {
            for (var spec : planet.getTechSpecialties()) {
                sb.append("/").append(techSpecEmoji(spec));
            }
        }
        sb.append(") ");
    }

    private String planetTypeEmoji(PlanetTypeModel.PlanetType type){
        switch (type) {

            case CULTURAL -> {
                return Emojis.Cultural;
            }
            case HAZARDOUS -> {
                return Emojis.Hazardous;
            }
            case INDUSTRIAL -> {
                return Emojis.Industrial;
            }
        }
        return Emojis.GoodDog;
    }

    private String techSpecEmoji(TechSpecialtyModel.TechSpecialty type){
        switch (type) {

            case BIOTIC -> {
                return Emojis.BioticTech;
            }
            case CYBERNETIC -> {
                return Emojis.CyberneticTech;
            }
            case PROPULSION -> {
                return Emojis.PropulsionTech;
            }
            case WARFARE -> {
                return Emojis.WarfareTech;
            }
        }
        return Emojis.GoodDog;
    }

    @Override
    public String getItemEmoji() {
        return Emojis.SemLor;
    }
}
