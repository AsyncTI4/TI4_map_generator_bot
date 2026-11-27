package ti4.image.map;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.io.ImageEncoder;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.io.ImageUploads;
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;
import ti4.model.FactionModel;
import ti4.model.GalaxyNames;
import ti4.model.Mapper;
import ti4.model.PlanetModel;
import ti4.model.Source.ComponentSource;
import ti4.model.TileHelper;
import ti4.model.TileModel;
import ti4.model.TileModel.TileBack;

public final class TileCatalogueImage {

    enum Group {
        HOME_SYSTEMS('0', "faction-homes"),
        HYPERLANES('a', "base-and-custom"),
        WORMHOLES('b', "wormholes"),
        LEGENDARY('b', "legendary"),
        ONE_PLANET('c', "1-planet"),
        THREE_PLUS_PLANETS('c', "3-plus-planets"),
        TWO_PLANETS('d', "2-planets"),
        ANOMALIES('e', "anomalies"),
        EMPTY_SYSTEMS('e', "empty-systems"),
        BLANKS_AND_MARKERS('f', "draft-fow-utility"),
        FRACTURE_VOID('g', "fracture-void"),
        FRACTURE_LEGENDARY('g', "fracture-legendary"),
        FRACTURE_PLANETS('g', "fracture-planets"),
        FRACTURE_WORMHOLES('g', "fracture-wormholes");

        private final char board;
        private final String segmentName;

        Group(char board, String segmentName) {
            this.board = board;
            this.segmentName = segmentName;
        }

        String segmentName() {
            return segmentName;
        }

        char board() {
            return board;
        }
    }

    private static final Map<Character, String> GALAXY_NAMES = Map.of(
            '0', "home-systems",
            'a', "hyperlanes",
            'b', "wormholes-legendary",
            'c', "one-three-planets",
            'd', "two-planets",
            'e', "planetless",
            'f', "blanks-and-markers",
            'g', "fracture-tiles");
    private static final int MAX_BOARD_RING = 16;
    private static final List<TileStep> STEPS = List.of(TileStep.Tile, TileStep.Extras);
    private static final CompactOverviewGenerator.Options OPTIONS =
            new CompactOverviewGenerator.Options(CompactOverviewGenerator.Style.HEXES, 1, false);
    private static final Pattern ROTATED_BASE_HYPERLANE = Pattern.compile("\\d+[ab]\\d+");
    private static final Pattern ROTATED_CUSTOM_HYPERLANE = Pattern.compile("hl_.*_[1-5]");
    // TODO: remove once the obsolete er### tiles are deleted from the repo
    private static final Pattern OBSOLETE_ERONOUS = Pattern.compile("er\\d+[a-z]?");
    private static final String SAMPLE_DRAFT_COLOUR = "red";
    private static final Set<ComponentSource> MARKER_SOURCES =
            Set.of(ComponentSource.other, ComponentSource.draft, ComponentSource.fow);
    private static final Pattern NATURAL_PARTS = Pattern.compile("^(\\D*)(\\d*)(.*)$");
    private static final int LABEL_MAX_WIDTH = 250;
    private static final int LABEL_BASELINE = 138;
    private static final int LABEL_MAX_FONT = 52;
    private static final int LABEL_MIN_FONT = 22;
    private static final ReentrantLock RENDERING = new ReentrantLock();

    private TileCatalogueImage() {}

    public static List<TileModel> catalogued(Collection<TileModel> tiles) {
        Set<String> factionHomes = factionHomeSystems();
        return tiles.stream()
                .filter(tile -> groupOf(tile, factionHomes).isPresent())
                .toList();
    }

    public static Optional<FileUpload> upload(Collection<TileModel> tiles) {
        if (!RENDERING.tryLock()) {
            return Optional.empty();
        }
        try {
            List<TileModel> shown = catalogued(tiles);
            byte[] bytes = ImageHelper.leavingCacheAsFound(artPaths(shown), () -> ImageEncoder.webp(render(shown)));
            return Optional.of(ImageUploads.createFileUpload(bytes, "tile_catalogue", "webp"));
        } finally {
            RENDERING.unlock();
        }
    }

    private static List<String> artPaths(Collection<TileModel> tiles) {
        return tiles.stream().map(ImageResources::tilePath).toList();
    }

    static BufferedImage render(Collection<TileModel> tiles) {
        Set<String> factionHomes = factionHomeSystems();
        Map<Group, List<TileModel>> groups = new EnumMap<>(Group.class);
        for (TileModel tile : tiles) {
            groupOf(tile, factionHomes)
                    .ifPresent(group -> groups.computeIfAbsent(group, key -> new ArrayList<>())
                            .add(tile));
        }
        Game game = new Game();
        game.newGameSetup();
        game.setName("tile-catalogue");
        game.setFowMode(true);
        Map<String, String> idByPosition = place(game, groups);
        String header = "Tile catalogue - " + idByPosition.size() + " tiles";
        CompactOverviewGenerator.Layout layout = CompactOverviewGenerator.layout(
                CompactOverviewGenerator.gmGroups(game, OPTIONS.reach()), header, OPTIONS);
        BufferedImage image = CompactOverviewGenerator.render(
                game,
                layout,
                new HashMap<>(game.getTileMap()),
                TileLayers.of(game, null, DisplayType.map),
                STEPS,
                header,
                OPTIONS);
        drawTileIds(image, layout, idByPosition);
        return image;
    }

    static Optional<Group> groupOf(TileModel tile, Set<String> factionHomes) {
        if (isLeftOut(tile)) {
            return Optional.empty();
        }
        List<PlanetModel> planets = planetsOf(tile);
        boolean legendary = planets.stream().anyMatch(PlanetModel::isLegendary);
        if (tile.isFracture()) {
            return Optional.of(fractureGroup(tile, legendary));
        }
        if (tile.isHyperlane()) {
            return Optional.of(Group.HYPERLANES);
        }
        boolean homeworld = planets.stream().anyMatch(planet -> planet.getFactionHomeworld() != null);
        if (homeworld || tile.getTileBack() == TileBack.GREEN && factionHomes.contains(tile.getId())) {
            return Optional.of(Group.HOME_SYSTEMS);
        }
        if (tile.hasWormhole()) {
            return Optional.of(Group.WORMHOLES);
        }
        if (legendary) {
            return Optional.of(Group.LEGENDARY);
        }
        return Optional.of(byPlanetCount(tile));
    }

    private static boolean isLeftOut(TileModel tile) {
        String id = tile.getId();
        if (OBSOLETE_ERONOUS.matcher(id).matches()) {
            return true;
        }
        if (tile.getSource() == ComponentSource.draft) {
            return !id.startsWith(SAMPLE_DRAFT_COLOUR);
        }
        return tile.isHyperlane()
                && (ROTATED_BASE_HYPERLANE.matcher(id).matches()
                        || ROTATED_CUSTOM_HYPERLANE.matcher(id).matches());
    }

    private static Group fractureGroup(TileModel tile, boolean legendary) {
        if (tile.hasWormhole()) {
            return Group.FRACTURE_WORMHOLES;
        }
        if (legendary) {
            return Group.FRACTURE_LEGENDARY;
        }
        return tile.getNumPlanets() == 0 ? Group.FRACTURE_VOID : Group.FRACTURE_PLANETS;
    }

    private static Group byPlanetCount(TileModel tile) {
        return switch (tile.getNumPlanets()) {
            case 0 -> planetlessGroup(tile);
            case 1 -> Group.ONE_PLANET;
            case 2 -> Group.TWO_PLANETS;
            default -> Group.THREE_PLUS_PLANETS;
        };
    }

    private static Group planetlessGroup(TileModel tile) {
        if (tile.isAnomaly()) {
            return Group.ANOMALIES;
        }
        return MARKER_SOURCES.contains(tile.getSource()) ? Group.BLANKS_AND_MARKERS : Group.EMPTY_SYSTEMS;
    }

    private static List<PlanetModel> planetsOf(TileModel tile) {
        return Objects.requireNonNullElse(tile.getPlanets(), List.<String>of()).stream()
                .map(TileHelper::getPlanetById)
                .filter(Objects::nonNull)
                .toList();
    }

    static Set<String> factionHomeSystems() {
        return Mapper.getFactionsValues().stream()
                .map(FactionModel::getHomeSystem)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    static Map<String, String> place(Game game, Map<Group, List<TileModel>> groups) {
        Map<String, String> idByPosition = new HashMap<>();
        Map<Character, Set<String>> usedByBoard = new HashMap<>();
        groups.forEach((group, tiles) -> {
            List<TileModel> sorted = tiles.stream()
                    .sorted(Comparator.comparing(TileModel::getId, TileCatalogueImage::naturalOrder))
                    .toList();
            Set<String> used = usedByBoard.computeIfAbsent(group.board(), board -> new HashSet<>());
            List<String> positions = blobPositions(group.board(), sorted.size(), used);
            for (int index = 0; index < sorted.size(); index++) {
                String id = sorted.get(index).getId();
                game.setTile(new Tile(id, positions.get(index)));
                idByPosition.put(positions.get(index), id);
            }
            MapSegment.put(game, MapSegment.shape(group.segmentName(), new HashSet<>(positions)));
            GalaxyNames.rename(game, GalaxyNames.idOf(group.board()), GALAXY_NAMES.get(group.board()));
        });
        return idByPosition;
    }

    private static List<String> blobPositions(char board, int count, Set<String> used) {
        int radius = radiusFor(count);
        String boardCentre = board == '0' ? "000" : board + "000";
        List<String> candidates = MapFrame.positionsWithin(boardCentre, MAX_BOARD_RING).stream()
                .sorted(MapFrame.POSITION_ORDER)
                .toList();
        for (String centre : candidates) {
            Set<String> blob = MapFrame.positionsWithin(centre, radius);
            boolean fitsOnBoard = blob.size() == hexCount(radius);
            if (fitsOnBoard
                    && MapFrame.positionsWithin(centre, radius + 1).stream().noneMatch(used::contains)) {
                used.addAll(blob);
                return spiral(centre, radius).subList(0, count);
            }
        }
        throw new IllegalStateException("No room for " + count + " tiles on galaxy " + board);
    }

    private static List<String> spiral(String centre, int radius) {
        List<String> positions = new ArrayList<>();
        Set<String> inner = Set.of();
        for (int ring = 0; ring <= radius; ring++) {
            Set<String> within = MapFrame.positionsWithin(centre, ring);
            Set<String> finalInner = inner;
            within.stream()
                    .filter(position -> !finalInner.contains(position))
                    .sorted(MapFrame.POSITION_ORDER)
                    .forEach(positions::add);
            inner = within;
        }
        return positions;
    }

    private static int radiusFor(int count) {
        int radius = 0;
        while (hexCount(radius) < count) {
            radius++;
        }
        return radius;
    }

    private static int hexCount(int radius) {
        return 1 + 3 * radius * (radius + 1);
    }

    static int naturalOrder(String a, String b) {
        Matcher left = NATURAL_PARTS.matcher(a);
        Matcher right = NATURAL_PARTS.matcher(b);
        if (!left.matches() || !right.matches()) {
            return a.compareTo(b);
        }
        int prefix = left.group(1).compareTo(right.group(1));
        if (prefix != 0) {
            return prefix;
        }
        String leftNumber = left.group(2);
        String rightNumber = right.group(2);
        if (!leftNumber.isEmpty() && !rightNumber.isEmpty() && leftNumber.length() != rightNumber.length()) {
            return Integer.compare(leftNumber.length(), rightNumber.length());
        }
        return a.compareTo(b);
    }

    private static void drawTileIds(
            BufferedImage image, CompactOverviewGenerator.Layout layout, Map<String, String> idByPosition) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            layout.canvas().enter(graphics);
            for (CompactOverviewGenerator.Placed box : layout.placed()) {
                box.centres().forEach((position, centre) -> drawTileId(graphics, idByPosition.get(position), centre));
            }
        } finally {
            graphics.dispose();
        }
    }

    private static void drawTileId(Graphics2D graphics, String id, Point centre) {
        if (id == null) {
            return;
        }
        graphics.setFont(fittingFont(graphics, id));
        OutlinedText.draw(
                graphics,
                id,
                centre.x,
                centre.y + LABEL_BASELINE,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Bottom,
                Strokes.STROKE_8,
                Color.BLACK);
    }

    private static Font fittingFont(Graphics2D graphics, String text) {
        int size = LABEL_MAX_FONT;
        Font font = Fonts.ti(size);
        while (size > LABEL_MIN_FONT && graphics.getFontMetrics(font).stringWidth(text) > LABEL_MAX_WIDTH) {
            size -= 2;
            font = Fonts.ti(size);
        }
        return font;
    }
}
