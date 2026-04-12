package ti4.buttons.handlers.faction.glimmer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;

@UtilityClass
public class GlimmerButtonHandler {

    public static void startGlimmersRedTech(Player player, Game game) {
        Set<UnitType> allowedUnits = Set.of(
                UnitType.Fighter,
                UnitType.Destroyer,
                UnitType.Cruiser,
                UnitType.Carrier,
                UnitType.Dreadnought,
                UnitType.Flagship,
                UnitType.Warsun);

        List<Button> buttons = new ArrayList<>();
        for (UnitType unit : allowedUnits) {
            buttons.add(
                    Buttons.green("endGlimmersRedTech_" + unit.plainName(), unit.plainName(), unit.getUnitTypeEmoji()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please choose the unit that was destroyed, and that you will be placing via _Fractal Plating_.",
                buttons);
    }

    @ButtonHandler("endGlimmersRedTech_")
    public static void endGlimmersRedTech(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event != null) {
            ButtonHelper.deleteMessage(event);
        }
        String unit = buttonID.split("_")[1];

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", please choose the system adjacent to your destroyed unit that you wish to place the unit."
                        + "\n-# Note that not all options displayed are legal options. The bot did not check where the unit was destroyed.",
                Helper.getTileWithShipsPlaceUnitButtons(player, game, unit, "placeOneNDone_skipbuild"));
    }
}
