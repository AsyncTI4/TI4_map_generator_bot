package ti4.buttons.handlers.planet;

import static org.apache.commons.lang3.StringUtils.countMatches;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;
import ti4.service.planet.PlanetService;
import ti4.service.unit.AddUnitService;

@UtilityClass
class PlanetActionButtonHandler {

    @ButtonHandler("planetAbilityExhaust_")
    public static void planetAbilityExhaust(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.replace("planetAbilityExhaust_", "");
        PlanetExhaustAbility.doAction(event, player, planet, game, true);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("spend_")
    public static void spend(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planetName = buttonID.split("_")[1];
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        PlanetExhaust.doAction(player, planetName, game);
        player.addSpentThing(planetName);

        UnitHolder uH = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        if (uH != null) {
            if (uH.getTokenList().contains("attachment_arcane_citadel.png")) {
                Tile tile = game.getTileFromPlanet(planetName);
                String msg = player.getRepresentation() + " added 1 infantry to "
                        + Helper.getPlanetRepresentation(planetName, game) + " due to the _Arcane Citadel_.";
                AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planetName);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
            if (uH.getTokenList().contains("attachment_facilitylogisticshub.png")) {
                String msg = player.getRepresentation() + " gained 1 commodity due to exhausting "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " while it had a _Logistics Hub Facility_.";
                player.setCommodities(player.getCommodities() + 1);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
            if (uH.getTokenList().contains("attachment_facilityresearchlab.png")) {
                int amountThereNow = game.changeCommsOnPlanet(1, planetName);

                String msg =
                        player.getRepresentation() + " gained 1 trade good on the _Research Lab_ due to exhausting "
                                + Helper.getPlanetRepresentation(planetName, game)
                                + ". It now has " + amountThereNow
                                + " trade good" + (amountThereNow == 1 ? "" : "s") + " on it.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
        }
        if (whatIsItFor.contains("tech") && player.hasAbility("ancient_knowledge")) {
            if ((Mapper.getPlanet(planetName).getTechSpecialties() != null
                            && !Mapper.getPlanet(planetName)
                                    .getTechSpecialties()
                                    .isEmpty())
                    || ButtonHelper.checkForTechSkips(game, planetName)) {
                String msg = player.getRepresentation()
                        + " due to your **Ancient Knowledge** ability, you may be eligible to receive a commodity here if you exhausted this planet ("
                        + planetName
                        + ") for its technology speciality.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.blue("gain_1_comms", "Gain 1 Commodity", MiscEmojis.comm));
                buttons.add(Buttons.red("deleteButtons", "N/A"));
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmoji()
                                + " may have the opportunity to gain a commodity from their **Ancient Knowledge** ability due to exhausting a technology speciality planet.");
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
            }
        }
        String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("refresh_")
    public static void refresh(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planetName = buttonID.split("_")[1];
        Player p2 = player;
        if (countMatches(buttonID, "_") > 1) {
            String faction = buttonID.split("_")[2];
            p2 = game.getPlayerFromColorOrFaction(faction);
        }

        PlanetService.refreshPlanet(p2, planetName);
        String totalVotesSoFar = event.getMessage().getContentRaw();
        if (totalVotesSoFar.contains("Readied")) {
            totalVotesSoFar += ", " + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        } else {
            totalVotesSoFar = player.getFactionEmojiOrColor() + " Readied "
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game);
        }
        event.getMessage().editMessage(totalVotesSoFar).queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
