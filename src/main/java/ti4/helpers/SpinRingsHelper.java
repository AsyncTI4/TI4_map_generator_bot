package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.fow.FowCommunicationThreadService;
import ti4.service.fow.GMService;
import ti4.service.map.SpinService;
import ti4.service.map.SpinService.Direction;
import ti4.service.map.SpinService.SpinSetting;

@UtilityClass
public class SpinRingsHelper {
    private static final String DEFAULT_MSG = "Spun the rings";
    private static final List<String> STATUS_MSGS = Arrays.asList(
            "Cosmic rings were shifted",
            "Stellar orbits were re-aligned",
            "Galactic core is twisting",
            "Celestial dance has spun",
            "Astral axis is tilting",
            "Planetary rings have rotated",
            "Adjusted the star-bound gyroscope",
            "Galactic arms are spiraling",
            "Warped the orbital paths");

    private static Map<String, Hex> indexToHex = new HashMap<>();
    private static Map<Hex, String> hexToIndex = new HashMap<>();

    static final int[][] RING_DIRS = {
        {1, 0}, // SE
        {0, 1}, // S
        {-1, 1}, // SW
        {-1, 0}, // NW
        {0, -1}, // N
        {1, -1} // NE
    };

    private static class Hex {
        final int q;
        final int r;

        Hex(int q, int r) {
            this.q = q;
            this.r = r;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Hex)) return false;
            Hex h = (Hex) o;
            return q == h.q && r == h.r;
        }

        @Override
        public int hashCode() {
            return 31 * q + r;
        }
    }

    private static void generateBoard(int maxRing) {
        Hex center = new Hex(0, 0);
        indexToHex.put("000", center);
        hexToIndex.put(center, "000");

        for (int ring = 1; ring <= maxRing; ring++) {
            generateRing(ring);
        }
    }

    private static void generateRing(int ring) {
        int q = 0;
        int r = -ring; // north start

        int pos = 1;

        for (int d = 0; d < 6; d++) {
            for (int i = 0; i < ring; i++) {
                Hex h = new Hex(q, r);

                // Guard: never overwrite 000
                if (!(h.q == 0 && h.r == 0)) {
                    String index = ring + (pos < 10 ? "0" : "") + pos;

                    indexToHex.put(index, h);
                    hexToIndex.put(h, index);
                }

                q += RING_DIRS[d][0];
                r += RING_DIRS[d][1];
                pos++;
            }
        }
    }

    public static void spinRingsCustom(Game game, List<SpinSetting> spinSettings) {
        // init board hex positions
        if (indexToHex.isEmpty()) {
            generateBoard(SpinService.MAX_RING_TO_SPIN);
        }

        StringBuilder sb = new StringBuilder("# " + randomStatusMessage());

        List<Tile> tilesToSet = new ArrayList<>();
        List<Map<String, String>> customHyperlanesToMove = new ArrayList<>();
        Map<Player, Set<String>> systemsPlayersSee = new HashMap<>();
        Map<Player, Set<String>> systemsPlayersSawMoving = new HashMap<>();
        if (game.isFowMode()) {
            for (Player p : game.getRealPlayers()) {
                systemsPlayersSee.put(p, FoWHelper.getTilePositionsToShow(game, p));
                systemsPlayersSawMoving.put(p, new HashSet<>());
            }
        }

        for (SpinSetting spin : spinSettings) {
            int ring = spin.ring();
            Direction dir = spin.direction();
            int steps = spin.steps();
            String center = spin.center();
            Hex centerHex = indexToHex.get(spin.center());

            if (steps == 0) continue;

            List<Hex> ringHexes = buildRing(centerHex, ring);
            int size = ringHexes.size();

            for (int i = 0; i < size; i++) {
                Hex fromHex = ringHexes.get(i);
                String fromPosition = hexToIndex.get(fromHex);

                Tile tile = game.getTileByPosition(fromPosition);
                if (tile == null) continue;

                int newIndex = dir == Direction.CW ? (i + steps) % size : (i - steps + size) % size;
                Hex toHex = ringHexes.get(newIndex);
                String toPosition = hexToIndex.get(toHex);

                tile.setPosition(toPosition);
                if (game.getCustomHyperlaneData().get(fromPosition) != null) {
                    customHyperlanesToMove.add(Map.of(fromPosition, toPosition));
                }
                tilesToSet.add(tile);
                updateHomeSystem(game, tile);
                if (game.isFowMode()) {
                    updateSystemsPlayerSawMoving(fromPosition, toPosition, systemsPlayersSawMoving, systemsPlayersSee);
                }
            }
            sb.append("\n-# ").append(spunMessage(ring, dir, steps, center, game.isFowMode()));
            if (game.isFowMode()) {
                GMService.logActivity(game, spunMessage(ring, dir, steps, center, false), false);
            }
        }

        for (Tile tile : tilesToSet) {
            game.setTile(tile);
        }
        if (!customHyperlanesToMove.isEmpty()) {
            Map<String, String> currentData = new HashMap<>(game.getCustomHyperlaneData());
            Map<String, String> newData = new HashMap<>();
            for (Map<String, String> pair : customHyperlanesToMove) {
                Entry<String, String> entry = pair.entrySet().iterator().next();
                String from = entry.getKey();
                String to = entry.getValue();
                String previousValue = currentData.remove(from);
                if (previousValue != null) {
                    newData.put(to, previousValue);
                }
            }
            currentData.putAll(newData);
            game.setCustomHyperlaneData(currentData);
        }
        game.rebuildTilePositionAutoCompleteList();
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), sb.toString());

        if (game.isFowMode()) {
            FowCommunicationThreadService.checkAllCommThreads(game);
            for (Map.Entry<Player, Set<String>> entry : systemsPlayersSawMoving.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    Player p = entry.getKey();
                    MessageHelper.sendMessageToChannel(
                            p.getPrivateChannel(),
                            p.getRepresentationUnfogged() + ", ⚙️ system"
                                    + (entry.getValue().size() > 1 ? "s " : " ") + String.join(", ", entry.getValue())
                                    + (entry.getValue().size() > 1 ? " have " : " has ") + "moved.");
                }
            }
        }
    }

    private static List<Hex> buildRing(Hex center, int ring) {
        List<Hex> result = new ArrayList<>(ring * 6);

        // 1. start at north
        int q = center.q;
        int r = center.r - ring;

        // 2. walk the 6 sides
        for (int d = 0; d < 6; d++) {
            for (int i = 0; i < ring; i++) {
                result.add(new Hex(q, r));
                q += RING_DIRS[d][0];
                r += RING_DIRS[d][1];
            }
        }
        return result;
    }

    private static void updateSystemsPlayerSawMoving(
            String from,
            String to,
            Map<Player, Set<String>> systemsPlayersSawMoving,
            Map<Player, Set<String>> systemsPlayersSee) {
        for (Map.Entry<Player, Set<String>> entry : systemsPlayersSee.entrySet()) {
            if (entry.getValue().contains(from)) {
                systemsPlayersSawMoving.get(entry.getKey()).add(from);
            }
            if (entry.getValue().contains(to)) {
                systemsPlayersSawMoving.get(entry.getKey()).add(to);
            }
        }
    }

    // the original spin logic which does
    // - ring 1 cw one step
    // - ring 2 ccw two steps
    // - ring 3 cw three steps (except 6p map HS positions)
    public static void spinRings(Game game) {
        List<Tile> tilesToSet = new ArrayList<>();

        for (int y = 1; y < 4; y++) {
            for (int x = 1; x < (y * 6 + 1); x++) {
                if (y == 3 && (x - 1) % 3 == 0) {
                    continue;
                }
                Tile tile;
                if (x < 10) {
                    tile = game.getTileByPosition(y + "0" + x);
                } else {
                    tile = game.getTileByPosition(y + "" + x);
                }
                if (tile == null) {
                    continue;
                }

                if (y == 2) {
                    if ((x - 2) < 1) {
                        tile.setPosition(2 + "" + ((x - 2) + (2 * 6)));
                    } else {
                        if ((x - 2) < 10) {
                            tile.setPosition(2 + "0" + (x - 2));
                        } else {
                            tile.setPosition(2 + "" + (x - 2));
                        }
                    }
                } else {
                    if ((x + y) > (y * 6)) {
                        tile.setPosition(y + "0" + ((x + y) % (y * 6)));
                    } else {
                        if ((x + y) < 10) {
                            tile.setPosition(y + "0" + (x + y));
                        } else {
                            tile.setPosition(y + "" + (x + y));
                        }
                    }
                }
                tilesToSet.add(tile);
            }
        }
        for (Tile tile : tilesToSet) {
            game.setTile(tile);
        }
        game.rebuildTilePositionAutoCompleteList();

        StringBuilder sb = new StringBuilder("# " + randomStatusMessage());
        sb.append("\n-# ").append(spunMessage(1, Direction.CW, 1, null, game.isFowMode()));
        sb.append("\n-# ").append(spunMessage(2, Direction.CCW, 2, null, game.isFowMode()));
        sb.append("\n-# ").append(spunMessage(3, Direction.CW, 3, null, game.isFowMode()));
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), sb.toString());
    }

    private static String spunMessage(int ring, Direction direction, int steps, String position, boolean fow) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚙️ Ring **");
        sb.append(fow ? "?" : ring);
        sb.append("** to **");
        sb.append(fow ? "?" : direction.displayName);
        sb.append("** direction by **");
        sb.append(fow ? "?" : steps);
        if (position != null) {
            sb.append("** steps around **");
            sb.append(fow ? "?" : position);
        }
        sb.append("**.");
        return sb.toString();
    }

    private static void updateHomeSystem(Game game, Tile tile) {
        if (!tile.isHomeSystem(game)) return;

        for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
            if (tile.getPosition().equals(player.getHomeSystemPosition())) {
                player.setHomeSystemPosition(tile.getPosition());
                return;
            }
        }
    }

    private static String randomStatusMessage() {
        if (new Random().nextBoolean()) {
            return DEFAULT_MSG;
        }
        return RandomHelper.pickRandomFromList(STATUS_MSGS);
    }
}
