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

    public static String getFactionSetupInfo(
            FactionModel faction, boolean includeUnits, boolean includeSystem, boolean includePriority) {
        if (faction == null) {
            throw new IllegalArgumentException("FactionModel cannot be null");
        }

        boolean keleres = faction.getAlias().toLowerCase().contains("keleres");
        StringBuilder setupInfo = new StringBuilder();

        // Display name
        setupInfo.append(faction.getFactionEmoji()).append(" ");
        if (keleres) {
            setupInfo.append("The Council Keleres");
        } else {
            setupInfo.append(faction.getFactionName());
        }
        setupInfo.append(System.lineSeparator());

        // Starting units
        if (includeUnits) {
            setupInfo
                    .append("> Starting Units: ")
                    .append(Helper.getUnitListEmojis(faction.getStartingFleet()))
                    .append(System.lineSeparator());
        }

        // Home system tile
        if (includeSystem) {
            if (keleres) {
                setupInfo.append("> Home System: Random unused home system").append(System.lineSeparator());
            } else {
                setupInfo.append(buildHomeSystemString(faction));
            }
        }

        // Priority Number
        if (includePriority) {
            if (faction.getPriorityNumber() != null) {
                setupInfo
                        .append("> Priority Number: **")
                        .append(faction.getPriorityNumber())
                        .append("**")
                        .append(System.lineSeparator());
            }
        }

        return setupInfo.toString();
    }

    private static String buildHomeSystemString(FactionModel faction) {
        StringBuilder homeInfo = new StringBuilder();

        TileModel homeTile = TileHelper.getTileById(faction.getHomeSystem());
        String homeAttributes = buildHomeSystemAttributes(homeTile);
        List<String> planetNames = faction.getHomePlanets();
        List<PlanetModel> planets = planetNames.stream()
                .map(Mapper::getPlanet)
                .filter(Objects::nonNull)
                .toList();
        List<String> planetRepresentations =
                planets.stream().map(p -> "> - " + buildPlanetString(p)).toList();
        homeInfo.append("> Home System: ").append(homeAttributes).append(System.lineSeparator());
        homeInfo.append(String.join(System.lineSeparator(), planetRepresentations))
                .append(System.lineSeparator());
        List<String> legendaryAbilities = planets.stream()
                .filter(PlanetModel::isLegendary)
                .map(p -> "**" + p.getShortName() + "**: " + p.getLegendaryAbilityText())
                .toList();
        for (String ability : legendaryAbilities) {
            homeInfo.append("> ").append(ability).append(System.lineSeparator());
        }

        return homeInfo.toString();
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
        if (homeTile.getAliases().contains("creussgate")
                || homeTile.getAliases().contains("crimsongate")) {
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
