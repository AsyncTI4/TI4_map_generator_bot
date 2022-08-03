package ti4.map;

import ti4.helpers.AliasHandler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

public class MapStringMapper {

    public static final List<String> mapFor6Player = Arrays.asList("1a", "1b", "1c", "1d", "1e", "1f",
            "2a", "2b", "2c", "2d", "2e", "2f", "2g", "2h", "2i", "2j", "2k", "2l",
            "3a", "3b", "3c", "3d", "3e", "3f", "3g", "3h", "3i", "3j", "3k", "3l", "3m", "3n", "3o", "3p", "3q", "3r");

    public static final List<String> mapFor8Player = Arrays.asList("1a", "1b", "1c", "1d", "1e", "1f",
            "2a", "2b", "2c", "2d", "2e", "2f", "2g", "2h", "2i", "2j", "2k", "2l",
            "3a", "3b", "3c", "3d", "3e", "3f", "3g", "3h", "3i", "3j", "3k", "3l", "3m", "3n", "3o", "3p", "3q", "3r",
            "4a", "4b", "4c", "4d", "4e", "4f", "4g", "4h", "4i", "4j", "4k", "4l", "4m", "4n", "4o", "4p", "4q", "4r", "4s", "4t", "4v", "4w", "4x", "4z");

    public static HashMap<String, String> getMappedTilesToPosition(String tileListAsString, Map userActiveMap) {
        HashMap<String, String> mappedTiles = new HashMap<>();
        StringTokenizer tokenizer = new StringTokenizer(tileListAsString, " ");
        List<String> mapForPlayers = mapFor6Player;
        if (userActiveMap != null && userActiveMap.getPlayerCountForMap() == 8) {
            mapForPlayers = mapFor8Player;
        }
        if (tokenizer.countTokens() == mapForPlayers.size()) {
            int index = 0;
            while (tokenizer.hasMoreTokens()) {
                String tileID = tokenizer.nextToken();
                mappedTiles.put(mapForPlayers.get(index), AliasHandler.resolveTile(tileID));
                index++;
            }
        }
        return mappedTiles;
    }

}
