package ti4.discord.interactions.buttons.handlers.planet;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.commodities.CommodityConversionService;

@UtilityClass
public class TradeStationButtonHandler {

    @ButtonHandler("startTradeStationConvert")
    public static void startTradeStationConvert(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        String onlyPlanet = null;
        for (String planet : player.getReadiedPlanets()) {
            if (game.getUnitHolderFromPlanet(planet) == null
                    || !game.getUnitHolderFromPlanet(planet).isSpaceStation()) {
                continue;
            }
            onlyPlanet = planet;
            buttons.add(Buttons.gray("useTradeStation_" + planet, Helper.getPlanetRepresentation(planet, game)));
        }
        if (buttons.size() == 1) {
            useTradeStation(event, player, game, "useTradeStation_" + onlyPlanet);
            return;
        } else if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), player.getRepresentation() + ", you have no readied space stations.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getRepresentation()
                        + ", please choose the space station you wish to exhaust to wash your commodit"
                        + (player.getCommodities() == 1 ? "y" : "ies")
                        + ". A reminder that the space station must be readied in order to exhaust it this way.",
                buttons);
    }

    @ButtonHandler("useTradeStation")
    public static void useTradeStation(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String planet = buttonID.split("_")[1];
        player.exhaustPlanet(planet);
        CommodityConversionService.convertAllComm(event, player, game);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted " + Helper.getPlanetRepresentation(planet, game)
                        + ", washing their commodit" + (player.getCommodities() == 1 ? "y" : "ies") + ".");
    }
}
