package ti4.helpers.twilightsfall;

import java.util.List;
import java.util.Objects;

import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.model.FactionModel;
import ti4.model.PlanetModel;
import ti4.model.TileModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TileEmojis;

public class TwilightsFallInfoHelper {

    /**
     * Get the string representation of the setup info a faction's reference card
     * provides during Twilight's Fall setup. This includes Priority Number,
     * Starting Units and Home System.
     */
    public static String getFactionSetupInfo(FactionModel faction) {
        return getFactionSetupInfo(faction, true, true, true);
    }

    public static String getFactionSetupInfo(FactionModel faction, boolean includeUnits, boolean includeSystem,
            boolean includePriority) {
        if (faction == null) {
            throw new IllegalArgumentException("FactionModel cannot be null");
        }

        StringBuilder setupInfo = new StringBuilder();
        setupInfo.append(faction.getFactionEmoji()).append(" ").append(faction.getFactionName())
                .append(System.lineSeparator());
        if (includeUnits) {
            setupInfo.append("> Starting Units: ").append(Helper.getUnitListEmojis(faction.getStartingFleet()))
                    .append(System.lineSeparator());
        }
        if (includeSystem) {
            TileModel homeTile = TileHelper.getTileById(faction.getHomeSystem());
            String homeAttributes = buildHomeSystemAttributes(homeTile);
            List<String> planetNames = faction.getHomePlanets();
            List<PlanetModel> planets = planetNames.stream().map(Mapper::getPlanet).filter(Objects::nonNull).toList();
            List<String> planetRepresentations = planets.stream()
                    .map(p -> "> - " + buildPlanetString(p))
                    .toList();
            setupInfo.append("> Home System: ").append(homeAttributes).append(System.lineSeparator());
            setupInfo.append(String.join(System.lineSeparator(), planetRepresentations)).append(System.lineSeparator());
            List<String> legendaryAbilities = planets.stream()
                    .filter(p -> p.isLegendary())
                    .map(p -> "**" + p.getShortName() + "**: " + p.getLegendaryAbilityText())
                    .toList();
            for (String ability : legendaryAbilities) {
                setupInfo.append("> ").append(ability).append(System.lineSeparator());
            }
        }
        if (includePriority) {
            if (faction.getPriorityNumber() != null) {
                setupInfo.append("> Priority Number: **").append(faction.getPriorityNumber()).append("**")
                        .append(System.lineSeparator());
            }
        }

        return setupInfo.toString();
    }

    private static String buildHomeSystemAttributes(TileModel homeTile) {
        if (homeTile == null) {
            return "";
        }
        StringBuilder attributes = new StringBuilder();
        if (homeTile.isAsteroidField()) {
            attributes.append(TileEmojis.Asteroids_44);
        }
        if (homeTile.isSupernova()) {
            attributes.append(TileEmojis.Supernova_43);
        }
        if (homeTile.isNebula()) {
            attributes.append(TileEmojis.Nebula_42);
        }
        if (homeTile.isGravityRift()) {
            attributes.append(TileEmojis.GravityRift_41);
        }
        if (homeTile.isScar()) {
            // TODO: Scar
            attributes.append(TileEmojis.TileRedBack);
        }
        if (homeTile.getAliases().contains("creussgate") || homeTile.getAliases().contains("crimsongate")) {
            attributes.append("(off board)");
        }
        return attributes.toString().trim();
    }

    private static String buildPlanetString(PlanetModel planet) {
        StringBuilder sb = new StringBuilder();
        sb.append(planet.getName());
        sb.append(" (");
        sb.append(planet.getResources()).append("/").append(planet.getInfluence());
        if (planet.isLegendary()) {
            sb.append("/").append(MiscEmojis.LegendaryPlanet);
        }
        if (planet.getTechSpecialties() != null) {
            for (var spec : planet.getTechSpecialties()) {
                sb.append("/").append(spec.getEmoji());
            }
        }
        sb.append(") ");
        return sb.toString();
    }
}
