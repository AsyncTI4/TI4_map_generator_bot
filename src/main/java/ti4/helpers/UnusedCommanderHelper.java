package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.FactionModel;

public class UnusedCommanderHelper {

    public static String getUnusedCommander(Game game) {
        return getUnusedCommander(game, Set.of());
    }

    public static String getUnusedCommander(Game game, Set<String> excludedCommanders) {
        List<String> commanders = new ArrayList<>();
        List<FactionModel> allFactions = Mapper.getFactionsValues().stream()
                .filter(f -> game.isDiscordantStarsMode()
                        ? f.getSource().isDs() || f.getSource().isOfficial()
                        : f.getSource().isOfficial())
                .toList();
        Set<String> exclusions = new HashSet<>(excludedCommanders);

        for (FactionModel faction : allFactions) {
            String commanderName = faction.getAlias() + "commander";
            if (commanderName.contains("keleres")) {
                commanderName = "kelerescommander";
            }
            if (game.getFactions().contains(faction.getAlias())
                    || (game.isMinorFactionsMode() && game.getTile(faction.getHomeSystem()) != null)
                    || (Helper.getPlayerFromLeader(game, commanderName) != null)
                    || commanders.contains(commanderName)
                    || Mapper.getLeader(commanderName) == null
                    || "unknown".equalsIgnoreCase(Mapper.getLeader(commanderName).getAbilityText())
                    || game.getStoredValue("mercCommander").contains(commanderName)
                    || exclusions.contains(commanderName)
                    || Mapper.getLeader(commanderName)
                            .getAbilityText()
                            .toLowerCase()
                            .contains("fracture")) {
                continue;
            }
            commanders.add(commanderName);
        }
        if (!commanders.isEmpty()) {
            Collections.shuffle(commanders);
            return commanders.getFirst();
        }
        return null;
    }
}
