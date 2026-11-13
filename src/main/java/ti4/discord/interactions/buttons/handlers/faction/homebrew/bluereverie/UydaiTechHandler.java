package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.UnitEmojis;
import ti4.service.tech.PlayerTechService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class UydaiTechHandler {
    private static final String MESSIAH_PROTOCOLS = "dsuydag";
    private static final String PLACE_MESSIAH_INFANTRY = "placeMessiahInfantry_";

    public static void resolveMessiahProtocols(GenericInteractionCreateEvent event, Game game, Player player) {
        PlayerTechService.deleteTheOneButtonIfButtonEvent(event);

        List<Button> paymentButtons = new ArrayList<>(Helper.getPlanetExhaustButtons(player, game, "inf"));
        paymentButtons.add(Buttons.DONE_EXHAUSTING_PLANETS_SPIT_IT_OUT);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", spend any amount of influence for _Messiah Protocols_.",
                paymentButtons);

        List<Button> placementButtons = getMessiahInfantryButtons(game, player);
        if (placementButtons.isEmpty()) {
            return;
        }
        String message = player.getRepresentationNoPing()
                + ", please choose each different controlled planet on which to place 1 infantry from your reinforcements with _Messiah Protocols_.";
        String prefix = player.factionButtonChecker() + PLACE_MESSIAH_INFANTRY;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                message,
                NewStuffHelper.paginateWithPinnedButtons(
                        placementButtons, List.of(Buttons.DONE_DELETE_BUTTONS), prefix, 25, 0));
    }

    @ButtonHandler(PLACE_MESSIAH_INFANTRY)
    public static void placeMessiahInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String message = player.getRepresentationNoPing()
                + ", please choose each different controlled planet on which to place 1 infantry from your reinforcements with _Messiah Protocols_.";
        String prefix = player.factionButtonChecker() + PLACE_MESSIAH_INFANTRY;
        if (NewStuffHelper.turnPage(
                event,
                buttonID,
                message,
                () -> getMessiahInfantryButtons(game, player),
                List.of(Buttons.DONE_DELETE_BUTTONS),
                prefix)) {
            return;
        }

        String planetName = buttonID.substring(PLACE_MESSIAH_INFANTRY.length());
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())
                || !player.hasTech(MESSIAH_PROTOCOLS)
                || !player.getExhaustedTechs().contains(MESSIAH_PROTOCOLS)
                || planet == null
                || !player.getPlanets().contains(planetName)
                || planet.isSpaceStation(game)
                || planet.getTokenList().stream().anyMatch(token -> token.contains("dmz"))) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        AddUnitService.addUnits(
                event, game.getTileFromPlanet(planetName), game, player.getColor(), "1 gf " + planetName);
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 " + UnitEmojis.infantry + " on "
                        + Helper.getPlanetRepresentation(planetName, game) + " with _Messiah Protocols_.");
    }

    private static List<Button> getMessiahInfantryButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null
                    || planet.isSpaceStation(game)
                    || planet.getTokenList().stream().anyMatch(token -> token.contains("dmz"))) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_MESSIAH_INFANTRY + planetName,
                    Helper.getPlanetRepresentation(planetName, game),
                    UnitEmojis.infantry));
        }
        return buttons;
    }
}
