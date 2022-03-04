package ti4.map;

import ti4.helpers.AliasHandler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

public class MapStringMapper {

    private static final List<String> mapFor6Player = Arrays.asList("1a", "1b", "1c", "1d", "1e", "1f",
            "2a", "2b", "2c", "2d", "2e", "2f", "2g", "2h", "2i", "2j", "2k", "2l",
            "3a", "3b", "3c", "3d", "3e", "3f", "3g", "3h", "3i", "3j", "3k", "3l", "3m", "3n", "3o", "3p", "3q", "3r");

    public static HashMap<String, String> getMappedTilesToPosition(String tileListAsString) {
        HashMap<String, String> mappedTiles = new HashMap<>();
        StringTokenizer tokenizer = new StringTokenizer(tileListAsString, " ");
        if (tokenizer.countTokens() == mapFor6Player.size()) {
            int index = 0;
            while (tokenizer.hasMoreTokens()) {
                String tileID = tokenizer.nextToken();
                mappedTiles.put(mapFor6Player.get(index), AliasHandler.resolveTile(tileID));
                index++;
            }
        }
        return mappedTiles;
    }

}
