package ti4.service.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.image.GalaxyNames;
import ti4.image.MapSegment;
import ti4.service.map.MapJsonIOService.MapDataIO;
import ti4.service.map.MapJsonIOService.SectorIO;
import ti4.service.map.MapJsonIOService.TileIO;

@UtilityClass
class MapSectorJsonService {

    enum SectorType {
        AUTOMATIC("automatic", "auto"),
        MANUAL_AUTO("manual_auto", "cluster"),
        MANUAL_CIRCLE("manual_circle", "circle");

        private final String jsonName;
        private final String alias;

        SectorType(String jsonName, String alias) {
            this.jsonName = jsonName;
            this.alias = alias;
        }

        @Nullable
        static SectorType parse(@Nullable String raw) {
            if (raw == null) {
                return null;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            for (SectorType type : values()) {
                if (type.jsonName.equals(normalized) || type.alias.equals(normalized)) {
                    return type;
                }
            }
            return null;
        }
    }

    private record SectorEntry(String position, SectorType type, int distance, String name) {}

    static boolean hasSectorData(MapDataIO mapData) {
        boolean anyTileSector = mapData.getMapInfo() != null
                && mapData.getMapInfo().stream().anyMatch(tile -> tile.getSector() != null);
        boolean anyGalaxy =
                mapData.getGalaxies() != null && !mapData.getGalaxies().isEmpty();
        return anyTileSector || anyGalaxy || mapData.getSectorGap() != null;
    }

    static void importSectors(Game game, MapDataIO mapData, StringBuilder errors) {
        if (!hasSectorData(mapData)) {
            return;
        }
        MapSegment.clearAll(game);
        GalaxyNames.clearNames(game);

        List<SectorEntry> entries = readEntries(game, mapData, errors);
        if (!game.isFowMode() && (!entries.isEmpty() || mapData.getSectorGap() != null)) {
            errors.append("- sectors: map sectors are only used in Fog of War games; they were stored but have no"
                    + " effect in this game.\n");
        }
        applyGap(game, mapData, entries, errors);
        Set<String> takenNames = new HashSet<>(Set.of(MapSegment.MAIN, MapSegment.FRACTURE));
        applyManualSectors(game, entries, takenNames, errors);
        applyAutomaticSectors(game, entries, takenNames, errors);
        applyGalaxies(game, mapData.getGalaxies(), errors);
        reportOverlappingManualSectors(game, errors);
        reportUnusedAutomaticNames(game, errors);
    }

    private static List<SectorEntry> readEntries(Game game, MapDataIO mapData, StringBuilder errors) {
        List<SectorEntry> entries = new ArrayList<>();
        if (mapData.getMapInfo() == null) {
            return entries;
        }
        for (TileIO tile : mapData.getMapInfo()) {
            SectorIO sector = tile.getSector();
            if (sector == null || game.getTileByPosition(tile.getPosition()) == null) {
                continue;
            }
            String position = tile.getPosition();
            if (!MapSegment.canJoinSector(position)) {
                sectorError(errors, position, "fracture and corner systems cannot be part of a sector");
                continue;
            }
            SectorType type = SectorType.parse(sector.getType());
            if (type == null) {
                sectorError(
                        errors,
                        position,
                        "unknown type `" + sector.getType() + "` (use automatic, manual_auto or manual_circle)");
                continue;
            }
            int distance = sector.getDistance() == null ? 0 : sector.getDistance();
            String name = StringUtils.trimToEmpty(sector.getName()).toLowerCase(Locale.ROOT);
            entries.add(new SectorEntry(position, type, distance, name));
        }
        return entries;
    }

    private static void applyGap(Game game, MapDataIO mapData, List<SectorEntry> entries, StringBuilder errors) {
        Set<Integer> automaticGaps = entries.stream()
                .filter(entry -> entry.type() == SectorType.AUTOMATIC)
                .map(SectorEntry::distance)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (mapData.getSectorGap() != null) {
            MapSegment.setGap(game, mapData.getSectorGap());
            if (automaticGaps.stream().anyMatch(gap -> !gap.equals(mapData.getSectorGap()))) {
                errors.append("- sectors: `sectorGap` ")
                        .append(MapSegment.gap(game))
                        .append(" overrides the distances on automatic systems.\n");
            }
            return;
        }
        if (automaticGaps.isEmpty()) {
            return;
        }
        int largest = automaticGaps.stream().max(Integer::compare).orElse(0);
        MapSegment.setGap(game, largest);
        if (automaticGaps.size() > 1) {
            errors.append("- sectors: automatic systems disagree on the gap ")
                    .append(automaticGaps)
                    .append("; the gap is game-wide, so ")
                    .append(MapSegment.gap(game))
                    .append(" is used.\n");
        }
    }

    private static void applyManualSectors(
            Game game, List<SectorEntry> entries, Set<String> takenNames, StringBuilder errors) {
        Set<String> namesToAvoid = new HashSet<>(takenNames);
        entries.stream().map(SectorEntry::name).filter(name -> !name.isEmpty()).forEach(namesToAvoid::add);
        for (SectorEntry entry : entries) {
            if (entry.type() == SectorType.AUTOMATIC) {
                continue;
            }
            String name = entry.name().isEmpty()
                    ? MapSegment.freeSectorName(game, entry.position(), namesToAvoid)
                    : entry.name();
            namesToAvoid.add(name);
            if (takenNames.contains(name)) {
                sectorError(errors, entry.position(), "the name `" + name + "` is already used; skipped");
                continue;
            }
            boolean cluster = entry.type() == SectorType.MANUAL_AUTO;
            String problem = MapSegment.validate(game, name, entry.position(), entry.distance(), cluster);
            if (problem != null) {
                sectorError(errors, entry.position(), problem);
                continue;
            }
            MapSegment.put(
                    game,
                    cluster
                            ? MapSegment.cluster(name, entry.position(), entry.distance())
                            : new MapSegment(name, entry.position(), entry.distance()));
            takenNames.add(name);
        }
    }

    private static void applyAutomaticSectors(
            Game game, List<SectorEntry> entries, Set<String> takenNames, StringBuilder errors) {
        List<SectorEntry> automatic = entries.stream()
                .filter(entry -> entry.type() == SectorType.AUTOMATIC)
                .toList();
        if (automatic.isEmpty()) {
            return;
        }
        MapSegment.setAutoSectors(game, true);
        Map<String, Set<String>> positionsByName = new LinkedHashMap<>();
        for (SectorEntry entry : automatic) {
            if (!entry.name().isEmpty()) {
                positionsByName
                        .computeIfAbsent(entry.name(), name -> new HashSet<>())
                        .add(entry.position());
            }
        }
        positionsByName.forEach((name, positions) -> {
            String anyPosition = positions.iterator().next();
            if (takenNames.contains(name)) {
                sectorError(errors, anyPosition, "the name `" + name + "` is already used by a manual sector");
                return;
            }
            String problem = MapSegment.putPin(game, name, positions);
            if (problem != null) {
                sectorError(errors, anyPosition, problem);
                return;
            }
            takenNames.add(name);
        });
    }

    private static void applyGalaxies(Game game, @Nullable Map<String, String> galaxies, StringBuilder errors) {
        if (galaxies == null || galaxies.isEmpty()) {
            GalaxyNames.ensureAssigned(game);
            return;
        }
        if (!game.isFowMode()) {
            errors.append("- galaxies: extra galaxies need a Fog of War game; galaxy names were skipped.\n");
            return;
        }
        List<String> inUse = GalaxyNames.inUse(game);
        galaxies.forEach((rawId, rawName) -> {
            String id = StringUtils.trimToEmpty(rawId).toLowerCase(Locale.ROOT);
            String name = StringUtils.trimToEmpty(rawName).toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                GalaxyNames.resetToAuto(game, id);
                return;
            }
            String problem = GalaxyNames.rename(game, id, name);
            if (problem != null) {
                errors.append("- galaxy `")
                        .append(id)
                        .append("`: ")
                        .append(problem)
                        .append('\n');
            } else if (!inUse.contains(id)) {
                errors.append("- galaxy `")
                        .append(id)
                        .append("`: no systems are on it yet; the name is kept for when it is used.\n");
            }
        });
        GalaxyNames.ensureAssigned(game);
    }

    private static void reportOverlappingManualSectors(Game game, StringBuilder errors) {
        Set<String> placed = game.getTileMap().keySet();
        List<MapSegment> manual = MapSegment.all(game).stream()
                .filter(segment ->
                        segment.kind() == MapSegment.Kind.CIRCLE || segment.kind() == MapSegment.Kind.CLUSTER)
                .toList();
        for (int first = 0; first < manual.size(); first++) {
            for (int second = first + 1; second < manual.size(); second++) {
                Set<String> shared = new HashSet<>(manual.get(first).positions());
                shared.retainAll(manual.get(second).positions());
                shared.retainAll(placed);
                if (!shared.isEmpty()) {
                    errors.append("- sectors: `")
                            .append(manual.get(first).name())
                            .append("` and `")
                            .append(manual.get(second).name())
                            .append("` share ")
                            .append(shared.size())
                            .append(" system(s).\n");
                }
            }
        }
    }

    private static void reportUnusedAutomaticNames(Game game, StringBuilder errors) {
        for (MapSegment.Dormant dormant : MapSegment.dormantNames(game)) {
            errors.append("- sectors: automatic name `").append(dormant.name()).append("` is not used: ");
            if (dormant.mergedInto() == null) {
                errors.append("its systems touch a manual sector or are not on the map.\n");
            } else {
                errors.append("its cluster is already named `")
                        .append(dormant.mergedInto())
                        .append("`.\n");
            }
        }
    }

    private static void sectorError(StringBuilder errors, String position, String reason) {
        errors.append("- sector at ")
                .append(position)
                .append(": ")
                .append(reason)
                .append('\n');
    }

    static void exportSectors(Game game, MapDataIO mapData) {
        Map<String, TileIO> tilesByPosition = mapData.getMapInfo().stream()
                .collect(Collectors.toMap(TileIO::getPosition, Function.identity(), (first, second) -> first));
        for (MapSegment segment : MapSegment.stored(game)) {
            SectorType type =
                    segment.kind() == MapSegment.Kind.CLUSTER ? SectorType.MANUAL_AUTO : SectorType.MANUAL_CIRCLE;
            attachSector(tilesByPosition, segment.centre(), sectorIO(type, segment.radius(), segment.name()));
        }
        if (MapSegment.isAutoSectors(game)) {
            for (MapSegment segment : MapSegment.all(game)) {
                if (segment.kind() != MapSegment.Kind.AUTO) {
                    continue;
                }
                String name = MapSegment.isPinned(game, segment.name()) ? segment.name() : null;
                attachSector(
                        tilesByPosition, segment.anchor(), sectorIO(SectorType.AUTOMATIC, MapSegment.gap(game), name));
            }
        }
        if (MapSegment.gap(game) > 0) {
            mapData.setSectorGap(MapSegment.gap(game));
        }
        Map<String, String> manualGalaxies = new LinkedHashMap<>();
        for (String id : GalaxyNames.IDS) {
            if (GalaxyNames.isManual(game, id)) {
                manualGalaxies.put(id, GalaxyNames.name(game, id));
            }
        }
        if (!manualGalaxies.isEmpty()) {
            mapData.setGalaxies(manualGalaxies);
        }
    }

    // TODO: sectors live on systems, so a second segment on the same centre, or a circle centred on an empty
    // position, is not exported.
    private static void attachSector(Map<String, TileIO> tilesByPosition, String position, SectorIO sector) {
        TileIO tile = tilesByPosition.get(position);
        if (tile != null && tile.getSector() == null) {
            tile.setSector(sector);
        }
    }

    private static SectorIO sectorIO(SectorType type, int distance, @Nullable String name) {
        SectorIO sector = new SectorIO();
        sector.setType(type.jsonName);
        sector.setDistance(distance);
        sector.setName(name);
        return sector;
    }
}
