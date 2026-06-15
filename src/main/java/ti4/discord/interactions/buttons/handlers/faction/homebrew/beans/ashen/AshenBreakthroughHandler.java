package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Helper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollType;

@UtilityClass
public class AshenBreakthroughHandler {

    private static final String ASHEN_BT = "ashenbt";
    private static final String EXHAUST_FROM_FIRE_RESOLVE_PREFIX = "ashenBtExhaustRevive_";

    public static boolean offerFromFireResolveInfantryButton(
            Player player, Game game, Tile tile, String planet, Die die, MessageChannel promptChannel) {
        if (player == null
                || game == null
                || tile == null
                || planet == null
                || planet.isBlank()
                || die == null
                || promptChannel == null
                || !player.hasReadyBreakthrough(ASHEN_BT)
                || !player.hasAbility("phoenix_rising")
                || die.getResult() > 5
                || player.getCardsInfoThread() == null) {
            return false;
        }

        String buttonId = player.factionButtonChecker()
                + EXHAUST_FROM_FIRE_RESOLVE_PREFIX
                + tile.getPosition()
                + "|"
                + planet
                + "|"
                + promptChannel.getId();
        Button button = Buttons.green(buttonId, "Exhaust From Fire, Resolve");
        String message = player.getRepresentationUnfogged()
                + ", one of your infantry rolled a 5 or lower to revive on "
                + Helper.getPlanetRepresentation(planet, game)
                + ". Exhaust _From Fire, Resolve_ to treat that roll like a 10.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, List.of(button));
        return true;
    }

    public static String appendBombardmentManualReminder(Player player, CombatRollType rollType, String message) {
        if (player == null || rollType != CombatRollType.bombardment || !player.hasUnlockedBreakthrough(ASHEN_BT)) {
            return message;
        }
        return message + "\nBT damage to reroll misses is not automated, please resolve manually.";
    }

    @ButtonHandler(EXHAUST_FROM_FIRE_RESOLVE_PREFIX)
    public static void exhaustFromFireResolve(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null || !player.hasReadyBreakthrough(ASHEN_BT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(EXHAUST_FROM_FIRE_RESOLVE_PREFIX.length());
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        String planet = parts[1];
        MessageChannel promptChannel = JdaService.jda.getChannelById(MessageChannel.class, parts[2]);
        if (tile == null || promptChannel == null || !player.hasAbility("phoenix_rising")) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "_From Fire, Resolve_ is no longer available for that infantry roll.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        BreakthroughCommandHelper.exhaustBreakthrough(player, ASHEN_BT);
        AshenAbilityHandler.startPhoenixRisingFromBreakthrough(player, game, tile, planet, promptChannel);
        ButtonHelper.deleteMessage(event);
    }
}
