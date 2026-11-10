package ti4.service.megagame;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.StrategyCardModel;

@UtilityClass
public class MegagameScThreads {

    public static String threadName(String baseName, int sc) {
        String group = MegagameGroupService.groupOfSc(sc);
        return baseName + "-" + sc + (group == null ? "" : "-" + MegagameGroupService.name(group));
    }

    public static int scOfThreadName(String threadName) {
        String[] parts = threadName.split("-");
        for (int i = parts.length - 1; i >= 0; i--) {
            int sc = NumberUtils.toInt(parts[i]);
            if (sc > 0) {
                return sc;
            }
        }
        return 0;
    }

    public static ThreadChannel forSc(Game game, int sc) {
        StrategyCardModel model = game.getStrategyCardModelByInitiative(sc).orElse(null);
        if (model == null || game.getActionsChannel() == null) {
            return null;
        }
        String name = threadName(game.getName() + "-round-" + game.getRound() + "-" + model.getName(), sc);
        return game.getActionsChannel().getThreadChannels().stream()
                .filter(thread -> thread.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public static ThreadChannel forPlayer(Game game, Player player, String stratName) {
        String group = MegagameGroupService.groupOf(game, player);
        if (group == null || ("technology".equalsIgnoreCase(stratName) && game.isComponentAction())) {
            return null;
        }
        for (int sc : MegagameGroupService.groupCards(game, group)) {
            StrategyCardModel model = game.getStrategyCardModelByInitiative(sc).orElse(null);
            if (model != null && model.getName().equalsIgnoreCase(stratName)) {
                return forSc(game, sc);
            }
        }
        return null;
    }
}
