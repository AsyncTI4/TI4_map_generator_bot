package ti4.discord.interactions.buttons.handlers.tech;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.TechnologyModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.UnitQueryService;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
class TechUseButtonHandler {

    @ButtonHandler("useTech_")
    public static void useTech(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String tech = buttonID.replace("useTech_", "");
        TechnologyModel techModel = Mapper.getTech(tech);
        if (!"st".equalsIgnoreCase(tech)) {
            String useMessage = player.toString() + " used the " + techModel.getRepresentation(false) + " technology.";
            if (game.isShowFullComponentTextEmbeds()) {
                MessageHelper.sendMessageToChannelWithEmbed(
                        event.getMessageChannel(), useMessage, techModel.getRepresentationEmbed());
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), useMessage);
            }
        }
        switch (tech) {
            case "st" -> { // Sarween Tools
                player.addSpentThing("sarween");
                String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
                player.setSarweenCounter(player.getSarweenCounter() + 1);
                String msg = player.getFactionEmoji() + " has used _Sarween Tools_ to save "
                        + StringHelper.pluralize(player.getSarweenCounter(), "resource") + " in this game so far. ";
                int result = ThreadLocalRandom.current().nextInt(0, 5);
                var userSettings = UserSettingsManager.get(player.getUserID());
                boolean startsWithSarween = false;
                FactionModel fmod = Mapper.getFaction(player.getFaction());
                if (fmod != null
                        && fmod.getStartingTech() != null
                        && fmod.getStartingTech().contains("st")) {
                    startsWithSarween = true;
                }
                if (userSettings.isPrefersSarweenMsg() && !startsWithSarween) {
                    if (player.getSarweenCounter() < 6) {

                        List<String> lameMessages = Arrays.asList(
                                "Not too impressive.",
                                "The technology has not yet proven its worth.",
                                "There better be more savings to come.",
                                "Your faction's stockholders are so far unimpressed.",
                                "Perhaps _AI Development Algorithm_ or _Scanlink Drone Network_ might have been more useful?");
                        msg += lameMessages.get(result);
                    } else if (player.getSarweenCounter() < 11) {
                        List<String> lameMessages = Arrays.asList(
                                "Not too shabby.",
                                "The technology is finally starting to justify its existence.",
                                "Hopefully there are still even more savings to come.",
                                "Your faction's stockholders are satisfied with the results of this technology.",
                                "Some folks still think _Scanlink Drone Network_ might have been more useful.");
                        msg += lameMessages.get(result);
                    } else if (player.getSarweenCounter() < 16) {
                        List<String> lameMessages = Arrays.asList(
                                "Very impressive.",
                                "If only all technology was this productive.",
                                "Surely there can't be even more savings to come?",
                                "Your faction's stockholders are ecstatic.",
                                "The _Scanlink Drone Network_ stans have been thoroughly shamed.");
                        msg += lameMessages.get(result);
                    } else {
                        List<String> lameMessages = Arrays.asList(
                                "Words cannot adequately express how impressive this is.",
                                "Is _Sarween Tools_ the best technology‽",
                                "Is this much saving even legal? The international IRS will be doing an audit on your paperwork sometime soon.",
                                "Your faction's stockholders have erected a statue of you in the city center.",
                                "Keep this up and we'll have to make a new channel, called \"Sarween Streaks\", just for your numbers.");
                        msg += lameMessages.get(result);
                    }
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), msg);
                event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
            }
            case "tf-sledfactories" -> { // Sarween Tools
                player.addSpentThing("sledfactories");
                String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);

                event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
            }
            case "tf-industrialjuggernaut" -> {
                List<Button> buttons = new ArrayList<>();
                for (Tile tile : UnitQueryService.getTilesContainingPlayersUnits(game, player, UnitType.Spacedock)) {
                    if (FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                        continue;
                    }
                    for (UnitHolder uH : tile.getUnitHolderValues()) {
                        if (uH.hasUnit(UnitType.Spacedock, player)) {
                            String uHName = "in space";
                            if (!"space".equalsIgnoreCase(uH.getName())) {
                                uHName = "on " + Helper.getPlanetRepresentation(uH.getName(), game);
                            }
                            buttons.add(Buttons.green(
                                    "industrialJugReplace_" + tile.getPosition() + "_" + uH.getName(),
                                    tile.getRepresentationForButtons() + " " + uHName));
                        }
                    }
                }

                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationNoPing()
                                + " choose a spacedock of yours to replace with a warsun (and spend 6r)",
                        buttons);
                ButtonHelper.deleteTheOneButton(event);
            }
            case "absol_st" -> { // Absol's Sarween Tools
                player.addSpentThing("absol_sarween");
                String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
                event.getMessage().editMessage(exhaustedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
            }
            case "absol_pa" -> { // Absol's Psychoarcheology
                List<Button> absolPAButtons = new ArrayList<>();
                absolPAButtons.add(Buttons.blue("getDiscardButtonsACs", "Discard", CardEmojis.getACEmoji(game)));
                for (String planetID : player.getReadiedPlanets()) {
                    Planet planet = game.getPlanet(planetID);
                    if (planet != null && isNotBlank(planet.getOriginalPlanetType())) {
                        List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
                        absolPAButtons.addAll(planetButtons);
                    }
                }
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + ", use buttons to discard 2 action cards to explore a readied planet.",
                        absolPAButtons);
            }
        }
    }

    @ButtonHandler("industrialJugReplace")
    public static void industrialJugReplace(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String pos = buttonID.split("_")[1];
        String uHName = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        UnitHolder uH = tile.getUnitHolder(uHName);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "ws");
        ButtonHelper.deleteMessage(event);
        RemoveUnitService.removeUnit(event, tile, game, player, uH, UnitType.Spacedock, 1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " used their industrial juggernaut ability to replace a spacedock in tile "
                        + tile.getRepresentationForButtons() + " with a warsun for the cost of 6r.");
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        buttons.add(Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", use these buttons to pay the 6 resources.",
                buttons);
    }

    @ButtonHandler("acquireAFreeTech") // Buttons.GET_A_FREE_TECH
    public static void acquireAFreeTech(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        String finsFactionCheckerPrefix = player.factionButtonChecker();
        game.setComponentAction(true);
        buttons.add(Buttons.blue(
                finsFactionCheckerPrefix + "getAllTechOfType_propulsion_noPay",
                "Get a Propulsion Technology",
                TechEmojis.PropulsionTech));
        buttons.add(Buttons.green(
                finsFactionCheckerPrefix + "getAllTechOfType_biotic_noPay",
                "Get a Biotic Technology",
                TechEmojis.BioticTech));
        buttons.add(Buttons.gray(
                finsFactionCheckerPrefix + "getAllTechOfType_cybernetic_noPay",
                "Get a Cybernetic Technology",
                TechEmojis.CyberneticTech));
        buttons.add(Buttons.red(
                finsFactionCheckerPrefix + "getAllTechOfType_warfare_noPay",
                "Get a Warfare Technology",
                TechEmojis.WarfareTech));
        buttons.add(Buttons.gray(
                finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade_noPay",
                "Get A Unit Upgrade Technology",
                TechEmojis.UnitUpgradeTech));
        String message = player.toString() + ", please choose what type of technology you wish to get?";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }
}
