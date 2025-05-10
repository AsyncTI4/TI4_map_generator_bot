package ti4.map;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import ti4.helpers.AliasHandler;
import ti4.message.BotLogger;

public class MapStringMapper {
    public static Map<String, String> getMappedTilesToPosition(String tileListAsString, Game userActiveGame) {
        Map<String, String> mappedTiles = new HashMap<>();
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
            if (tileCount > ringCount * 6) {
                tileCount = 1;
                ringCount++;
            }

            if (ringCount > 16) {
                BotLogger.warning(new BotLogger.LogMessageOrigin(userActiveGame), "Exceeding max ring (16) count for " + userActiveGame.getName());
                break;
            }
        }
        return mappedTiles;
    }
}
