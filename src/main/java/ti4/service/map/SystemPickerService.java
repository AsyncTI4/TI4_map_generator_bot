package ti4.service.map;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.image.BoardPosition;
import ti4.image.MapSegment;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;
import ti4.service.fow.FOWPlusService;
import ti4.service.option.FOWOptionService.FOWOption;

@UtilityClass
public class SystemPickerService {

    private static final String STEP_PREFIX = "systemPick_";
    private static final String CENTRE_RING = "0";
    private static final String OTHER_RING = "x";
    private static final int MAX_TILE_BUTTONS = 23;
    private static final Set<String> CORNER_POSITIONS = Set.of("tl", "tr", "bl", "br");
    private static final Comparator<String> POSITION_ORDER =
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    record Area(String name, String label, Set<String> positions) {}

    enum Part {
        W("West half"),
        E("East half"),
        N("North"),
        NE("North-east"),
        SE("South-east"),
        S("South"),
        SW("South-west"),
        NW("North-west");

        private final String label;

        Part(String label) {
            this.label = label;
        }

        String id() {
            return name().toLowerCase();
        }
    }

    public static boolean isEnabled(Game game) {
        return game.isFowMode();
    }

    public static boolean isSegmented(Game game) {
        return isEnabled(game) && !sectorsFor(game, null).isEmpty();
    }

    public static void dropUnknownSystems(List<Button> buttons, Player player, Game game) {
        if (!isSegmented(game)) {
            return;
        }
        Set<String> known = MapSegment.knownPositions(game, player);
        buttons.removeIf(button -> {
            String position = StringUtils.substringAfter(button.getCustomId(), "ringTile_");
            return !position.isEmpty() && !known.contains(position);
        });
        boolean knowsACorner = known.stream()
                .anyMatch(position ->
                        CORNER_POSITIONS.contains(position.toLowerCase()) || MapSegment.isFracturePosition(position));
        if (!knowsACorner) {
            buttons.removeIf(button -> StringUtils.contains(button.getCustomId(), "ring_corners"));
        }
    }

    public static void addFirstStep(List<Button> ringButtons, Player player, Game game) {
        int cornersIndex = 0;
        while (cornersIndex < ringButtons.size()
                && !StringUtils.contains(ringButtons.get(cornersIndex).getCustomId(), "ring_corners")) {
            cornersIndex++;
        }
        ringButtons.addAll(cornersIndex, firstStepButtons(player, game));
    }

    static List<Button> firstStepButtons(Player player, Game game) {
        Predicate<Tile> selectable = selectableFor(game, player);
        List<Area> areas = areas(game, player).stream()
                .filter(area ->
                        !selectablePositions(game, area.positions(), selectable).isEmpty())
                .toList();
        if (areas.size() == 1) {
            return ringButtons(player, game, areas.getFirst(), selectable);
        }
        return areas.stream()
                .map(area -> Buttons.green(
                        player.factionButtonChecker() + STEP_PREFIX + area.name(),
                        "Map: " + area.label() + " ("
                                + selectablePositions(game, area.positions(), selectable)
                                        .size()
                                + ")"))
                .toList();
    }

    static List<Area> areas(Game game, @Nullable Player player) {
        Set<String> onMap = game.getTileMap().keySet();
        List<Area> areas = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        sectorsFor(game, null).forEach(segment -> covered.addAll(segment.positions()));
        for (MapSegment segment : sectorsFor(game, player)) {
            Set<String> positions = new HashSet<>(segment.positions());
            positions.retainAll(onMap);
            if (positions.isEmpty()) continue;
            areas.add(new Area(segment.name(), segment.displayName(game), positions));
        }
        Set<String> rest = onMap.stream()
                .filter(position -> !CORNER_POSITIONS.contains(position.toLowerCase()))
                .filter(position -> !MapSegment.isFracturePosition(position))
                .filter(position -> !covered.contains(position))
                .collect(Collectors.toSet());
        if (!rest.isEmpty()) {
            areas.addFirst(new Area(MapSegment.MAIN, MapSegment.mainDisplayName(game), rest));
        }
        return areas;
    }

    private static List<MapSegment> sectorsFor(Game game, @Nullable Player player) {
        if (game.getFowOption(FOWOption.CLASSIC_MAP_LAYOUT)) {
            return List.of();
        }
        return MapSegment.visibleTo(game, player);
    }

    static Map<String, List<String>> byRing(List<String> positions) {
        Map<String, List<String>> rings = new LinkedHashMap<>();
        positions.stream()
                .sorted(Comparator.comparingInt(SystemPickerService::ringOrder).thenComparing(POSITION_ORDER))
                .forEach(position -> rings.computeIfAbsent(mapRing(position), key -> new ArrayList<>())
                        .add(position));
        return rings;
    }

    static String mapRing(String position) {
        String local = BoardPosition.parse(position).map(BoardPosition::local).orElse(position);
        return Helper.isInteger(local) ? String.valueOf(Integer.parseInt(local) / 100) : OTHER_RING;
    }

    private static int ringOrder(String position) {
        String ring = mapRing(position);
        return OTHER_RING.equals(ring) ? Integer.MAX_VALUE : Integer.parseInt(ring);
    }

    static String galaxyCentre(List<String> positions) {
        return positions.stream()
                .findFirst()
                .flatMap(BoardPosition::parse)
                .map(board -> board.withLocal("000"))
                .orElse("000");
    }

    static Map<Part, List<String>> split(String centre, List<String> positions) {
        Map<Part, List<String>> halves = new EnumMap<>(Part.class);
        Map<Part, List<String>> sides = new EnumMap<>(Part.class);
        for (String position : positions) {
            Point offset = offsetFrom(centre, position);
            if (offset.x <= 0)
                halves.computeIfAbsent(Part.W, key -> new ArrayList<>()).add(position);
            if (offset.x >= 0)
                halves.computeIfAbsent(Part.E, key -> new ArrayList<>()).add(position);
            sides.computeIfAbsent(sideOf(offset), key -> new ArrayList<>()).add(position);
        }
        boolean halvesFit = halves.values().stream().allMatch(half -> half.size() <= MAX_TILE_BUTTONS);
        return halvesFit ? halves : sides;
    }

    private static Point offsetFrom(String centre, String position) {
        Point from = PositionMapper.getTilePosition(centre);
        Point to = PositionMapper.getTilePosition(position);
        if (from == null || to == null) {
            return new Point(0, 0);
        }
        return new Point(to.x - from.x, to.y - from.y);
    }

    private static Part sideOf(Point offset) {
        double degreesFromNorth = (Math.toDegrees(Math.atan2(offset.x, -offset.y)) + 360) % 360;
        int sector = (int) (((degreesFromNorth + 30) % 360) / 60);
        return List.of(Part.N, Part.NE, Part.SE, Part.S, Part.SW, Part.NW).get(sector);
    }

    private static Predicate<Tile> selectableFor(Game game, Player player) {
        Set<String> visible = FOWPlusService.isActive(game) ? FoWHelper.getTilePositionsToShow(game, player) : null;
        Predicate<Tile> activatable = tile -> ButtonHelper.canActivateTile(game, player, tile, visible);
        if (!isSegmented(game)) {
            return activatable;
        }
        Set<String> known = MapSegment.knownPositions(game, player);
        return activatable.and(tile -> known.contains(tile.getPosition()));
    }

    private static List<String> selectablePositions(Game game, Set<String> positions, Predicate<Tile> selectable) {
        return positions.stream()
                .filter(position -> selectable.test(game.getTileByPosition(position)))
                .sorted(POSITION_ORDER)
                .toList();
    }

    private static List<Button> ringButtons(Player player, Game game, Area area, Predicate<Tile> selectable) {
        List<Button> buttons = new ArrayList<>();
        byRing(selectablePositions(game, area.positions(), selectable)).forEach((ring, positions) -> {
            String id = player.factionButtonChecker() + STEP_PREFIX + area.name() + "_" + ring;
            buttons.add(Buttons.green(id, ringLabel(ring) + " (" + positions.size() + ")"));
        });
        return buttons;
    }

    private static String ringLabel(String ring) {
        return switch (ring) {
            case CENTRE_RING -> "Centre";
            case OTHER_RING -> "Other systems";
            default -> "Ring #" + ring;
        };
    }

    private static List<Button> tileButtons(Player player, Game game, List<String> positions) {
        List<Button> buttons = new ArrayList<>();
        for (String position : positions) {
            Tile tile = game.getTileByPosition(position);
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "ringTile_" + position,
                    tile.getRepresentationForButtons(game, player),
                    tile.getTileEmoji(player)));
        }
        return buttons;
    }

    @ButtonHandler(value = STEP_PREFIX, save = false)
    public static void pick(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String[] step = buttonID.substring(STEP_PREFIX.length()).split("_");
        Optional<Area> area = areas(game, player).stream()
                .filter(candidate -> candidate.name().equals(step[0]))
                .findFirst();
        if (area.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    "That part of the map is no longer available. Please choose again.",
                    ButtonHelper.getPossibleRings(player, game));
            ButtonHelper.deleteMessage(event);
            return;
        }
        Predicate<Tile> selectable = selectableFor(game, player);
        List<String> positions = selectablePositions(game, area.get().positions(), selectable);
        List<Button> buttons = new ArrayList<>();
        String message = "Please choose the system that you wish to activate.";
        if (step.length == 1) {
            if (positions.size() <= MAX_TILE_BUTTONS) {
                buttons.addAll(tileButtons(player, game, positions));
            } else {
                buttons.addAll(ringButtons(player, game, area.get(), selectable));
                message = "Please choose the ring of `" + area.get().label() + "` that the system is in.";
            }
        } else {
            List<String> inRing = byRing(positions).getOrDefault(step[1], List.of());
            if (step.length == 2 && inRing.size() > MAX_TILE_BUTTONS) {
                split(galaxyCentre(inRing), inRing)
                        .forEach((part, partPositions) -> buttons.add(Buttons.green(
                                player.factionButtonChecker() + STEP_PREFIX + step[0] + "_" + step[1] + "_" + part.id(),
                                part.label + " (" + partPositions.size() + ")")));
                message = "That ring is large. Please choose the side of the map that the system is on.";
            } else {
                List<String> shown = step.length == 2
                        ? inRing
                        : split(galaxyCentre(inRing), inRing).getOrDefault(partOf(step[2]), List.of());
                buttons.addAll(tileButtons(player, game, shown));
            }
        }
        buttons.add(Buttons.red("ChooseDifferentDestination", "Get a Different Ring"));
        buttons.add(Buttons.red("resetTacticalMovement", "Reset all unit movement"));
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @Nullable
    private static Part partOf(String id) {
        for (Part part : Part.values()) {
            if (part.id().equals(id)) {
                return part;
            }
        }
        return null;
    }
}
