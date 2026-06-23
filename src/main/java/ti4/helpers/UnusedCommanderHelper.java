package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.FactionModel;

@UtilityClass
public class UnusedCommanderHelper {

    public static String getUnusedCommander(Game game) {
        return getUnusedCommander(game, Set.of());
    }

    public static String getUnusedCommander(Game game, Set<String> excludedCommanders) {
        List<String> commanders = new ArrayList<>();
        List<FactionModel> allFactions = Mapper.getFactionsValues().stream()
                .filter(f -> f.getSource().isOfficial()
                        || (game.isDiscordantStarsMode() && f.getSource().isDs())
                        || (game.isBlueReverieMode() && f.getSource().isBr()))
                .toList();

        for (FactionModel faction : allFactions) {
            String commanderName = faction.getAlias() + "commander";
            if (commanderName.contains("keleres")) {
                commanderName = "kelerescommander";
            }
            if (game.getFactions().contains(faction.getAlias())
                    || (game.isFrankenGame() && "mahactcommander".equalsIgnoreCase(commanderName))
                    || ("obsidian".equalsIgnoreCase(faction.getAlias())
                            && game.getFactions().contains("firmament"))
                    || ("firmament".equalsIgnoreCase(faction.getAlias())
                            && game.getFactions().contains("obsidian"))
                    || (game.isMinorFactionsMode() && game.getTile(faction.getHomeSystem()) != null)
                    || (Helper.getPlayerFromLeader(game, commanderName) != null)
                    || commanders.contains(commanderName)
                    || Mapper.getLeader(commanderName) == null
                    || "unknown"
                            .equalsIgnoreCase(Mapper.getLeader(commanderName).getAbilityText())
                    || game.getStoredValue("mercCommander").contains(commanderName)
                    || excludedCommanders.contains(commanderName)
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
        return "";
    }
}
