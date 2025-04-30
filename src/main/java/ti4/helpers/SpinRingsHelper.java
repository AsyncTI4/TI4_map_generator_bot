package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.fow.FowCommunicationThreadService;

@UtilityClass
public class SpinRingsHelper {
    private static final String DEFAULT_MSG = "Spun the rings";
    private static final List<String> STATUS_MSGS = Arrays.asList(
      "Shifted the cosmic rings",
        "Aligned the stellar orbits",
        "Twisted the galactic core",
        "Rearranged the celestial dance",
        "Tilted the astral axis",
        "Rotated the planetary rings",
        "Adjusted the star-bound gyroscope",
        "Spiraled the galactic arms",
        "Warped the orbital paths"
    );

    private static final String CW = "cw";
    private static final String CCW = "ccw";
    private static final String RND = "rnd";

    public static boolean validateSpinSettings(String customSpinSptring) {
        String[] customSpins = customSpinSptring.toLowerCase().split(" ");
        for (String spinString : customSpins) {

            //Needs to have Ring:Direction:Steps  
            String[] spinSettings = spinString.toLowerCase().split(":");
            if (spinSettings.length != 3) {
                return false;
            }

            int smallestRing = 99;
            for (String ringString : spinSettings[0].split(",")) {
                int ring = parseInt(ringString);
                if (ring <= 0) {
                    return false;
                }
                smallestRing = ring < smallestRing ? ring : smallestRing;
            }

            String direction = spinSettings[1];
            if (!Arrays.asList(CW, CCW, RND).contains(direction)) {
                return false;
            }

            //Step counts must be less than tiles in smallest ring
            for (String stepsString : spinSettings[2].split(",")) {
                int steps = parseInt(stepsString);
                if (steps < 0 || steps > smallestRing * 6 - 1) {
                    return false;
                }
            }
        }
        return true;
    }

    /*
     * Custom rotation with random support
     * 
     * Ring:Direction:Steps
     * 1:cw:1 2:ccw:2
     * 
     * Or with random options
     * 1,2:rnd:2,3 
     * to spin ring 1 OR 2 to random direction for 2 OR 3 steps
     * 
     */
    public static void spinRingsCustom(Game game, String customSpinString, String flavourMsg) {
        String[] customSpins = customSpinString.toLowerCase().split(" ");
        StringBuffer sb = new StringBuffer(flavourMsg != null ? flavourMsg : "## ⚙️ " + randomStatusMessage());
        List<Tile> tilesToSet = new ArrayList<>();

        for (String spinString : customSpins) {
            Random random = new Random();
            String[] spinSettings = spinString.toLowerCase().split(":");

            String[] ringOptions = spinSettings[0].split(",");
            int ring = parseInt(ringOptions[random.nextInt(ringOptions.length)]);

            String direction = spinSettings[1];
            if (RND.equals(direction)) {
                if (random.nextBoolean()) {
                    direction = CW;
                } else {
                    direction = CCW;
                }
            }

            String[] stepsOptions = spinSettings[2].split(",");
            int steps = parseInt(stepsOptions[random.nextInt(stepsOptions.length)]);

            if (steps > 0) {
                for (int x = 1; x < (ring * 6 + 1); x++) {
                    Tile tile = game.getTileByPosition(ring + (x < 10 ? "0" : "") + x);
                    if (tile == null) {
                        continue;
                    }

                    int pos;
                    if (CW.equals(direction)) {
                        if ((x + steps) > (ring * 6)) {
                            pos = (x + steps) % (ring * 6);
                        } else {
                            pos = x + steps;
                        }
                    } else {
                        if ((x - steps) < 1) {
                            pos = (x - steps) + (ring * 6);
                        } else {
                            pos = x - steps;
                        }
                    }
                    tile.setPosition(ring + (pos < 10 ? "0" : "") + pos);
                    tilesToSet.add(tile);
                    updateHomeSystem(game, tile);
                }
            }
            spunMessage(sb, ring, direction, steps, game);
        }

        for (Tile tile : tilesToSet) {
            game.setTile(tile);
        }
        game.rebuildTilePositionAutoCompleteList();
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), sb.toString());
        FowCommunicationThreadService.checkAllCommThreads(game);
    }

    private static int parseInt(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    //the original spin logic which does
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
                    if ((x - y) < 1) {
                        tile.setPosition(y + "" + ((x - y) + (y * 6)));
                    } else {
                        if ((x - y) < 10) {
                            tile.setPosition(y + "0" + (x - y));
                        } else {
                            tile.setPosition(y + "" + (x - y));
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

        StringBuffer sb = new StringBuffer("## ⚙️ " + randomStatusMessage());
        spunMessage(sb, 1, CW, 1, game);
        spunMessage(sb, 2, CCW, 2, game);
        spunMessage(sb, 3, CW, 3, game);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), sb.toString());
    }

    private static void spunMessage(StringBuffer sb, int ring, String direction, int steps, Game game) {
        sb.append("\n-# Ring ").append(ring).append(" ");
        sb.append(game.isFowMode() ? "?" : direction.toUpperCase()).append(" for ");
        sb.append(game.isFowMode() ? "?" : steps).append(" steps.");
    }

    private static void updateHomeSystem(Game game, Tile tile) {
        if (!tile.isHomeSystem()) return;

        for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
            if (tile.getPosition().equals(player.getHomeSystemPosition())) {
                player.setHomeSystemPosition(tile.getPosition());
                return;
            }
        }
    }

    private static String randomStatusMessage() {
        Random random = new Random();
        if (random.nextBoolean()) {
            return DEFAULT_MSG;
        }
        return STATUS_MSGS.get(random.nextInt(STATUS_MSGS.size()));
    }
}
