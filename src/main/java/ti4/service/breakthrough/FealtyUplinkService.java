package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.regex.RegexService;

@UtilityClass
public class FealtyUplinkService {

    private String fealtyRep() {
        return Mapper.getBreakthrough("l1z1xbt").getNameRepresentation();
    }

    public void postInitialButtons(Game game, Player player, String planetName) {
        String prettyPlanet = Helper.getPlanetRepresentationNoResInf(planetName, game);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.finChecker() + "fealtyUplink_" + planetName,
                "Use Fealty Uplink on " + prettyPlanet,
                FactionEmojis.L1Z1X));
        String message = "When you gain control of a planet, you may use " + fealtyRep();
        message +=
                " to place infantry equal to that planet's influence.\n-# You may choose to do this either before or after exploring.";
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("fealtyUplink_")
    private static void addInfWithFealty(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "fealtyUplink_" + RegexHelper.unitHolderRegex(game, "planet");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String planetName = matcher.group("planet");
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            resolveAddInf(player, planet);
            ButtonHelper.deleteMessage(event);
        });
    }

    public static void resolveAddInf(Player player, Planet planet) {
        if (planet != null) {
            int influence = planet.getInfluence();
            planet.addUnit(Units.getUnitKey(UnitType.Infantry, player.getColorID()), influence);
            String prettyPlanet = Helper.getPlanetRepresentationNoResInf(planet.getName(), player.getGame());
            String message = player.getRepresentationNoPing() + " Added " + influence + " infantry to " + prettyPlanet
                    + " using " + fealtyRep() + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }
    }
}
