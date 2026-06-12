package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.RandomHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class AshenUnitHandler {

    private static final String ASHEN_INF_ID = "ashen_infantry";
    private static final String ASHEN_INF2_ID = "ashen_infantry2";

    public static boolean resolveAshenInfDestroy(
            Game game, Player player, List<RemovedUnit> units, GenericInteractionCreateEvent event) {
        if (game == null
                || player == null
                || units == null
                || (!player.hasUnit(ASHEN_INF_ID) && !player.hasUnit(ASHEN_INF2_ID))) {
            return false;
        }

        MessageChannel resultChannel = player.getCorrectChannel();
        MessageChannel promptChannel = event == null ? resultChannel : event.getMessageChannel();
        boolean handled = false;
        for (RemovedUnit unit : units) {
            if (!player.unitBelongsToPlayer(unit.unitKey()) || unit.unitKey().unitType() != UnitType.Infantry) {
                continue;
            }

            String planet = unit.uh() instanceof Planet ? unit.uh().getName() : null;
            for (int x = 0; x < unit.getTotalRemoved(); x++) {
                resolveSingleAshenInfDestroy(game, player, unit.tile(), planet, resultChannel, promptChannel);
            }
            handled = true;
        }
        return handled;
    }

    private static void resolveSingleAshenInfDestroy(
            Game game,
            Player player,
            Tile tile,
            String planet,
            MessageChannel resultChannel,
            MessageChannel promptChannel) {
        int threshold = player.hasUnit(ASHEN_INF2_ID) ? 6 : 9;
        Die die = new Die(threshold);

        StringBuilder message = new StringBuilder(UnitEmojis.infantry + " died. Rolling for resurrection. ");
        message.append(die.getGreenDieIfSuccessOrRedDieIfFailure());

        if (!die.isSuccess()) {
            message.append(" Failure.");
            if (RandomHelper.isOneInX(20)) {
                message.append(
                        " That infantry is now permanently dead, destined to be forgotten as just one more amongst untold billions who will die in this war.");
                message.append(" Already, you can't even remember ")
                        .append(RandomHelper.isOneInX(2) ? "his" : "her")
                        .append(" ")
                        .append(RandomHelper.isOneInX(2) ? "face" : "name")
                        .append(".");
            }
            MessageHelper.sendMessageToChannel(resultChannel, message.toString());
            return;
        }

        if (AshenAbilityHandler.offerPhoenixRising(player, game, tile, planet, die, promptChannel)) {
            message.append(
                    " Success. You may use _Phoenix Rising_ to place that infantry back on the planet, or decline to resolve _Cinderborn_.");
            MessageHelper.sendMessageToChannel(resultChannel, message.toString());
            return;
        }

        message.append(" Success. _Cinderborn_ will produce 1 hit and place that infantry in stasis.");
        MessageHelper.sendMessageToChannel(resultChannel, message.toString());
        AshenAbilityHandler.resolveCinderbornRevive(player, game, tile, planet, promptChannel);
    }
}
