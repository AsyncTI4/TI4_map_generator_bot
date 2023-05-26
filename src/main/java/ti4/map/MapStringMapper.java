package ti4.map;

import ti4.helpers.AliasHandler;

import java.util.HashMap;
import java.util.StringTokenizer;

public class MapStringMapper {
    public static HashMap<String, String> getMappedTilesToPosition(String tileListAsString, Map userActiveMap) {
        HashMap<String, String> mappedTiles = new HashMap<>();
        StringTokenizer tokenizer = new StringTokenizer(tileListAsString, " ");


        int tileCount = 1;
        int ringCount = 1;
        while (tokenizer.hasMoreTokens()) {
            String tileID = tokenizer.nextToken();
            if (tileID.startsWith("{") && tileID.endsWith("}")) {
                tileID = tileID.replace("{", "").replace("}", "");
                mappedTiles.put("000", AliasHandler.resolveTile(tileID));
                continue;
            }
            String position = "" + ringCount + (tileCount < 10 ? "0" + tileCount : tileCount);
            mappedTiles.put(position, AliasHandler.resolveTile(tileID));
            tileCount++;
            if (tileCount > ringCount * 6){
                tileCount = 1;
                ringCount++;
            }
        }
        return mappedTiles;
    }

}
