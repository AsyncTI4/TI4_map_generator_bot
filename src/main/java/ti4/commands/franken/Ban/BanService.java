package ti4.commands.franken.Ban;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.service.franken.FrankenBanList;

public class BanService implements IBanService {

    private static final Map<String, BiFunction<Game, String, String>> BAN_APPLIERS = new HashMap<>();

    static {
        BAN_APPLIERS.put(Constants.ABILITY, (game, id) -> {
            if (isBlank(id) || Mapper.getAbility(id) == null) return "";
            appendStoredValue(game, "bannedAbilities", id);
            return "Successfully banned ability: " + Mapper.getAbility(id).getName() + ".\n";
        });

        BAN_APPLIERS.put(Constants.LEADER, (game, id) -> {
            if (isBlank(id) || Mapper.getLeader(id) == null) return "";
            appendStoredValue(game, "bannedLeaders", id);
            return "Successfully banned leader: " + Mapper.getLeader(id).getName() + ".\n";
        });

        BAN_APPLIERS.put(Constants.PROMISSORY_NOTE_ID, (game, id) -> {
            if (isBlank(id) || Mapper.getPromissoryNote(id) == null) return "";
            appendStoredValue(game, "bannedPNs", id);
            return "Successfully banned promissory note: " + Mapper.getPromissoryNote(id).getName() + ".\n";
        });

        BAN_APPLIERS.put(Constants.TECH, (game, id) -> {
            if (isBlank(id) || Mapper.getTech(id) == null) return "";
            appendStoredValue(game, "bannedTechs", id);
            return "Successfully banned tech: " + Mapper.getTech(id).getName() + ".\n";
        });

        BAN_APPLIERS.put(Constants.TILE_NAME, (game, id) -> {
            if (isBlank(id)) return "";
            Tile tile = new Tile(id, "000");
            if (tile.getTileModel() == null) return "";
            appendStoredValue(game, "bannedTiles", tile.getTileID());
            return "Successfully banned tile: " + tile.getTileModel().getName() + ".\n";
        });

        BAN_APPLIERS.put(Constants.BAN_COMMODITIES, (game, id) -> {
            if (isBlank(id) || !Mapper.getFactionIDs().contains(id)) return "";
            appendStoredValue(game, "bannedComms", id);
            return "Successfully banned " + Mapper.getFaction(id).getFactionName() + " commodities.\n";
        });

        BAN_APPLIERS.put(Constants.BAN_FLEET, (game, id) -> {
            if (isBlank(id) || !Mapper.getFactionIDs().contains(id)) return "";
            appendStoredValue(game, "bannedFleets", id);
            return "Successfully banned " + Mapper.getFaction(id).getFactionName() + " starting fleet.\n";
        });

        BAN_APPLIERS.put(Constants.BAN_HS, (game, id) -> {
            if (isBlank(id) || !Mapper.getFactionIDs().contains(id)) return "";
            appendStoredValue(game, "bannedHSs", id);
            return "Successfully banned " + Mapper.getFaction(id).getFactionName() + " home system.\n";
        });

        BAN_APPLIERS.put(Constants.BAN_STARTING_TECH, (game, id) -> {
            if (isBlank(id) || !Mapper.getFactionIDs().contains(id)) return "";
            appendStoredValue(game, "bannedStartingTechs", id);
            return "Successfully banned " + Mapper.getFaction(id).getFactionName() + " starting tech.\n";
        });

        BAN_APPLIERS.put(Constants.MECH_ID, (game, unitId) -> {
            if (isBlank(unitId) || Mapper.getUnit(unitId) == null) return "";
            String[] parts = unitId.split("_");
            if (parts.length < 2 || !Constants.MECH_ID.equalsIgnoreCase(parts[1])) return "";
            appendStoredValue(game, "bannedMechs", parts[0]);
            return "Successfully banned " + parts[0] + " " + Constants.MECH_ID + ".\n";
        });

        BAN_APPLIERS.put(Constants.FLAGSHIP_ID, (game, unitId) -> {
            if (isBlank(unitId) || Mapper.getUnit(unitId) == null) return "";
            String[] parts = unitId.split("_");
            if (parts.length < 2 || !Constants.FLAGSHIP_ID.equalsIgnoreCase(parts[1])) return "";
            appendStoredValue(game, "bannedFSs", parts[0]);
            return "Successfully banned " + parts[0] + " " + Constants.FLAGSHIP_ID + ".\n";
        });

        BAN_APPLIERS.put(Constants.UNIT_ID, (game, unitId) -> {
            if (isBlank(unitId) || Mapper.getUnit(unitId) == null) return "";
            if (unitId.endsWith("_mech")) return BAN_APPLIERS.get(Constants.MECH_ID).apply(game, unitId);
            if (unitId.endsWith("_flagship")) return BAN_APPLIERS.get(Constants.FLAGSHIP_ID).apply(game, unitId);
            return "";
        });
    }
    @Override
    public String applyOption(Game game, String optionName, String value) {
        if (isBlank(optionName) || isBlank(value)) return "";
        BiFunction<Game, String, String> applier = BAN_APPLIERS.get(optionName);
        if (applier == null) return "Failed to Ban " + optionName;
        return applier.apply(game, value);
    }

    @Override
    public String applyBanList(Game game, FrankenBanList banList) {
        if (banList == null || banList.getBansByType() == null) return "";
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : banList.getBansByType().entrySet()) {
            String type = entry.getKey();
            List<String> values = entry.getValue();
            for (String value : values) {
                String line = applyOption(game, type, value);
                if (!isBlank(line)) out.append(line);
            }
        }
        return out.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static void appendStoredValue(Game game, String key, String value) {
        String prev = game.getStoredValue(key);
        if (prev == null) prev = "";
        game.setStoredValue(key, prev + "finSep" + value);
    }
}
