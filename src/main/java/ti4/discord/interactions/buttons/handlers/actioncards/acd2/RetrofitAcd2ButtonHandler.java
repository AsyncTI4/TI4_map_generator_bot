package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;

@UtilityClass
class RetrofitAcd2ButtonHandler {

    private static final UnitType[] NON_FIGHTER_SHIPS = {
        UnitType.Destroyer, UnitType.Cruiser, UnitType.Carrier, UnitType.Dreadnought, UnitType.Flagship, UnitType.Warsun
    };

    @ButtonHandler("resolveRetrofit")
    public static void resolveRetrofit(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            for (UnitType type : NON_FIGHTER_SHIPS) {
                if (tile.getSpaceUnitHolder().getUnitCount(type, player) > 0) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "retrofitShip_" + tile.getPosition() + "_" + type.name(),
                            type.humanReadableName() + " in " + tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you have no non-fighter ships for _Retrofit_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Cancel"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose 1 of your non-fighter ships for _Retrofit_.",
                buttons);
    }

    @ButtonHandler("retrofitShip_")
    public static void resolveRetrofitShip(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("retrofitShip_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Tile tile = game.getTileByPosition(parts[0]);
        UnitType type;
        try {
            type = UnitType.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
            return;
        }
        String where = tile == null ? "the active system" : tile.getRepresentationForButtons(game, player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.toString() + "'s " + type.humanReadableName() + " in " + where
                        + " gains **SPACE CANNON 5**, **BOMBARDMENT 5**, and **SUSTAIN DAMAGE** during this tactical"
                        + " action (_Retrofit_). Apply these manually when rolling dice and assigning hits.");
    }
}
