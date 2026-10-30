package ti4.draft.items;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import ti4.draft.DraftCategory;
import ti4.draft.DraftItem;
import ti4.game.Game;
import ti4.model.Mapper;
import ti4.model.PlanetModel;
import ti4.model.TileHelper;
import ti4.model.TileModel;
import ti4.service.draft.DraftTileManager;
import ti4.service.emoji.MiscEmojis;
import ti4.service.milty.MiltyDraftTile;

public abstract class TileDraftItem extends DraftItem {

    protected TileDraftItem(DraftCategory cat, String itemId) {
        super(cat, itemId);
    }

    public TileModel getTileModel() {
        return TileHelper.getTileById(getItemId());
    }

    @JsonIgnore
    @Override
    public String getTitle(Game game) {
        return getTileModel().getNameRepresentation();
    }

    @JsonIgnore
    @Override
    public String getShortDescription() {
        return getTileModel().getName() + " (" + getItemId() + ")";
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl(Game game) {
        return getLongDescriptionImpl();
    }

    @JsonIgnore
    @Override
    public String getLongDescriptionImpl() {
        StringBuilder sb = new StringBuilder();
        List<String> planetIds = getTileModel().getPlanets();
        if (planetIds.isEmpty()) {
            return getTileModel().getName();
        }
        for (int i = 0; i < planetIds.size() - 1; i++) {
            buildPlanetString(Mapper.getPlanet(planetIds.get(i)), sb);
            sb.append(", ");
        }

        buildPlanetString(Mapper.getPlanet(planetIds.getLast()), sb);

        return sb.toString();
    }

    private void buildPlanetString(PlanetModel planet, StringBuilder sb) {
        sb.append(planet.getName());
        planet.getPlanetTypes().stream()
                .filter(type -> switch (type) {
                    case CULTURAL, HAZARDOUS, INDUSTRIAL -> true;
                    default -> false;
                })
                .forEach(type -> sb.append(' ').append(type.getEmoji()));
        sb.append(" (");
        sb.append(planet.getResources()).append('/').append(planet.getInfluence());
        if (planet.isLegendary()) {
            sb.append('/').append(MiscEmojis.LegendaryPlanet);
        }
        if (planet.getTechSpecialties() != null) {
            planet.getTechSpecialties().forEach(specialty -> sb.append('/').append(specialty.getEmoji()));
        }
        sb.append(") ");
        if (planet.getBasicAbilityText() != null) {
            sb.append('\n').append("**").append(planet.getName()).append("**: ").append(planet.getBasicAbilityText());
        }
        if (planet.isLegendary()) {
            sb.append('\n')
                    .append(MiscEmojis.LegendaryPlanet)
                    .append(" **")
                    .append(planet.getLegendaryAbilityName())
                    .append("**: ")
                    .append(planet.getLegendaryAbilityText());
        }
    }

    public MiltyDraftTile getMiltyTile() {
        return DraftTileManager.findTile(getItemId());
    }
}
