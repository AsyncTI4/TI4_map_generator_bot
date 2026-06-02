package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class IronUnitsHandler {

    public static void resolveRiptideDestroy(
            GenericInteractionCreateEvent event, Game game, Player player, RemovedUnit unit) {
        String replacementUnitList = getRiptideReplacementUnitList(unit);
        if (replacementUnitList.isEmpty()) {
            return;
        }

        AddUnitService.addUnits(event, unit.tile(), game, player.getColor(), replacementUnitList);
        sendRiptideMessage(event, player, unit);
    }

    static String getRiptideReplacementUnitList(RemovedUnit unit) {
        if (unit == null || unit.uh() == null || unit.getTotalRemoved() <= 0) {
            return "";
        }

        int totalRemoved = unit.getTotalRemoved();
        if (Constants.SPACE.equals(unit.uh().getName())) {
            return totalRemoved + " fighter";
        }
        return totalRemoved + " infantry " + unit.uh().getName();
    }

    private static void sendRiptideMessage(GenericInteractionCreateEvent event, Player player, RemovedUnit unit) {
        int totalRemoved = unit.getTotalRemoved();
        boolean inSpace = Constants.SPACE.equals(unit.uh().getName());
        String replacementName = inSpace ? "fighter" + (totalRemoved == 1 ? "" : "s") : "infantry";
        String placement = inSpace ? "the space area" : unit.uh().getName();
        String message = "> Added " + totalRemoved + " " + replacementName + " from reinforcements to " + placement
                + " due to _Riptide_.\n";

        if (event != null) {
            MessageHelper.sendMessageToEventChannel(event, message);
        } else if (player.getCorrectChannel() != null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }
    }
}
