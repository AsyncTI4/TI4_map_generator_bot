package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.PlanetButtonService;

@UtilityClass
public class ButtonHelperRelics {

    @ButtonHandler("jrResolution_")
    public static void jrResolution(String buttonID, Game game, ButtonInteractionEvent event) {
        String faction2 = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction2);
        if (p2 != null) {
            Button sdButton = Buttons.green("jrStructure_sd", "Place 1 Space Dock", UnitEmojis.spacedock);
            Button pdsButton = Buttons.green("jrStructure_pds", "Place 1 PDS", UnitEmojis.pds);
            Button tgButton = Buttons.green("jrStructure_tg", "Gain 1 Trade Good");
            List<Button> buttons = new ArrayList<>();
            buttons.add(sdButton);
            buttons.add(pdsButton);
            buttons.add(tgButton);
            String msg = p2.getRepresentationUnfogged() + ", please choose which structure to build.";
            MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg, buttons);
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("prophetsTears_")
    public static void prophetsTears(Player player, String buttonID, ButtonInteractionEvent event) {
        player.addExhaustedRelic("prophetstears");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmoji() + " is exhausting _The Prophet's Tears_.");
        if (buttonID.contains("AC")) {
            ActionCardHelper.drawActionCards(player, 1);
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        } else {
            String msg = " exhausted _The Prophet's Tears_.";
            String exhaustedMessage = event.getMessage().getContentRaw();
            if (!exhaustedMessage.contains("Please choose the")) {
                exhaustedMessage += ", " + msg;
            } else {
                exhaustedMessage = player.toString() + msg;
            }
            event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
    }

    public static void offerNanoforgeButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        List<Button> buttons = PlanetButtonService.buttonsForUsablePlanets(
                player,
                game,
                location -> !location.planet().isFake()
                        && !location.planet().isSpaceStation(game)
                        && !ButtonHelper.isPlanetLegendaryOrHome(
                                location.planet().getName(), game, false, null),
                ButtonStyle.SUCCESS,
                "nanoforgePlanet_");
        String message = "Please choose which planet you wish to attach _Nano-Forge_ to.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    public static void offerTitansHeroButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        List<Button> buttons = PlanetButtonService.buttonsForUsablePlanets(
                player, game, location -> !location.planet().isFake(), ButtonStyle.SUCCESS, "titansHeroPlanet_");
        String message = "Please choose which planet you wish to attach _Geoform_ to.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    @ButtonHandler("nanoforgePlanet_")
    public static void nanoforgePlanet(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String planet = buttonID.replace("nanoforgePlanet_", "");
        Planet planetReal = game.getPlanet(planet);
        planetReal.addToken("attachment_nanoforge.png");
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Attached _Nano-Forge_ to " + Helper.getPlanetRepresentation(planet, game) + ".");
        ButtonHelper.deleteMessage(event);
        CommanderUnlockCheckService.checkPlayer(player, "sol", "xxcha");
    }

    @ButtonHandler("titansHeroPlanet_")
    public static void titansHeroPlanet(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String planet = buttonID.replace("titansHeroPlanet_", "");
        Planet planetReal = game.getPlanet(planet);
        planetReal.addToken("attachment_titanshero.png");
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Attached _Geoform_ to " + Helper.getPlanetRepresentation(planet, game) + ", and readied it.");
        ButtonHelper.deleteMessage(event);
        player.refreshPlanet(planet);
        CommanderUnlockCheckService.checkPlayer(player, "sol", "xxcha");
    }
}
