package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatUnitSelectionHelper;

@UtilityClass
class ClassifiedWeaponsAcd2ButtonHandler {

    @ButtonHandler("resolveClassifiedWeapons")
    public static void resolveClassifiedWeapons(Player player, Game game, ButtonInteractionEvent event) {
        String factionChecker = player.factionButtonChecker();
        List<Button> buttons = new ArrayList<>();
        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        if (activeTile != null) {
            UnitHolder spaceHolder = activeTile.getSpaceUnitHolder();
            if (spaceHolder != null) {
                Map<UnitModel, Integer> combatUnits =
                        CombatUnitSelectionHelper.collectCombatRoundUnits(activeTile, spaceHolder, player);
                for (UnitModel unit : combatUnits.keySet()) {
                    buttons.add(Buttons.gray(
                            factionChecker + "resolveClassifiedWeaponsUnit_" + unit.getAsyncId(),
                            unit.getName(),
                            unit.getUnitEmoji()));
                }
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " resolved _Classified Weapons_. Declare the unit now; it rolls 2 additional dice.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the unit that will roll 2 additional dice (_Classified Weapons_).",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveClassifiedWeaponsUnit_")
    public static void resolveClassifiedWeaponsUnit(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String unitAsyncId = buttonID.replace("resolveClassifiedWeaponsUnit_", "");
        UnitModel unit = player.getUnitFromAsyncID(unitAsyncId);
        String unitName = unit != null ? unit.getName() : unitAsyncId;
        game.setCurrentReacts("classifiedWeapons", player.getFaction() + ";" + unitAsyncId);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " chose " + unitName
                        + " via _Classified Weapons_; it rolls 2 additional dice this combat round.");
        ButtonHelper.deleteMessage(event);
    }
}
