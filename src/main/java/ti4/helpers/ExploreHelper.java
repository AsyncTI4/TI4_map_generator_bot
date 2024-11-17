package ti4.helpers;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.leaders.RefreshLeader;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitKey;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class ExploreHelper {

    @ButtonHandler("resolveLocalFab_")
    public static void resolveLocalFabricators(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetName = buttonID.split("_")[1];
        String commOrTg;
        if (player.getCommodities() > 0) {
            player.setCommodities(player.getCommodities() - 1);
            commOrTg = Emojis.comm;
        } else if (player.getTg() > 0) {
            player.setTg(player.getTg() - 1);
            commOrTg = Emojis.tg;
        } else {
            ButtonHelper.addReaction(event, false, false, "Didn't have any Comms/TGs to spend, no mech placed", "");
            return;
        }
        new AddUnits().unitParsing(event, player.getColor(), game.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName, game);
        planetName = Mapper.getPlanet(planetName) == null ? "`error?`" : Mapper.getPlanet(planetName).getName();
        ButtonHelper.addReaction(event, false, false, "Spent a " + commOrTg + " for a Mech on " + planetName, "");
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            String pF = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), pF + " Spent a " + commOrTg + " for a Mech on " + planetName);
        }
    }

    @ButtonHandler("resolveVolatile_")
    public static void resolveVolatileFuelSource(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetID = StringUtils.substringAfter(buttonID, "_");
        String mechOrInfCheckMessage = checkForMechOrRemoveInf(planetID, game, player);
        boolean failed = mechOrInfCheckMessage.contains("Please try again.");

        if (!failed) {
            String message = player.getRepresentation() + " the " + mechOrInfCheckMessage + " Please gain one CC. Your current CCs are " + player.getCCRepresentation();
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        }

        if (!failed && !event.getMessage().getContentRaw().contains("fragment")) {
            ButtonHelper.deleteMessage(event);
            if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " " + mechOrInfCheckMessage);
            }
        }
    }

    @ButtonHandler("resolveExpedition_")
    public static void resolveExpedition(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String message = "";
        String planetName = buttonID.split("_")[1];
        boolean failed = false;
        message += checkForMechOrRemoveInf(planetName, game, player);
        failed = message.contains("Please try again.");
        if (!failed) {
            PlanetRefresh.doAction(player, planetName);
            planetName = Mapper.getPlanet(planetName) == null ? planetName : Mapper.getPlanet(planetName).getName();
            message += "Readied " + planetName;
            ButtonHelper.addReaction(event, false, false, message, "");
            ButtonHelper.deleteMessage(event);
        } else {
            ButtonHelper.addReaction(event, false, false, message, "");
        }
    }

    @ButtonHandler("resolveCoreMine_")
    public static void resolveCoreMine(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String message = "";
        String planetName = buttonID.split("_")[1];
        boolean failed = false;
        message += checkForMechOrRemoveInf(planetName, game, player);
        failed = message.contains("Please try again.");
        if (!failed) {
            message += "Gained 1TG " + player.gainTG(1, true);
            ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        }
        ButtonHelper.addReaction(event, false, false, message, "");
        if (!failed) {
            ButtonHelper.deleteMessage(event);
            if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
                String pF = player.getFactionEmoji();
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), pF + " " + message);
            }
        }
    }

    public static String checkForMechOrRemoveInf(String planetName, Game game, Player player) {
        String message;
        Tile tile = game.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;
        int numInf = 0;
        String colorID = Mapper.getColorID(player.getColor());
        UnitKey mechKey = Mapper.getUnitKey("mf", colorID);
        UnitKey infKey = Mapper.getUnitKey("gf", colorID);
        if (unitHolder.getUnits() != null) {

            if (unitHolder.getUnits().get(mechKey) != null) {
                numMechs = unitHolder.getUnits().get(mechKey);
            }
            if (unitHolder.getUnits().get(infKey) != null) {
                numInf = unitHolder.getUnits().get(infKey);
            }
        }
        if (numMechs > 0 || numInf > 0) {
            if (numMechs > 0) {
                message = "Planet had a mech. ";
            } else {
                message = "Planet did not have a mech. Removed 1 infantry (" + numInf + "->" + (numInf - 1) + "). ";
                tile.removeUnit(planetName, infKey, 1);
            }
        } else {
            message = "Planet did not have a mech or an infantry. Please try again.";
        }
        return message;
    }

    @ButtonHandler("ruins_")
    public static void resolveWarForgeRuins(Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String mech = buttonID.split("_")[2];
        String message = "";
        boolean failed;
        message += checkForMechOrRemoveInf(planet, game, player);
        failed = message.contains("Please try again.");
        if (!failed) {
            if ("mech".equalsIgnoreCase(mech)) {
                new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet),
                    "mech " + planet, game);
                message += "Placed mech on" + Mapper.getPlanet(planet).getName();
            } else {
                new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(planet),
                    "2 infantry " + planet, game);
                message += "Placed 2 infantry on" + Mapper.getPlanet(planet).getName();
            }
            ButtonHelper.addReaction(event, false, false, message, "");
            ButtonHelper.deleteMessage(event);
        } else {
            ButtonHelper.addReaction(event, false, false, message, "");
        }
    }

    @ButtonHandler("seedySpace_")
    public static void resolveSeedySpace(Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[2];
        String acOrAgent = buttonID.split("_")[1];
        String message = "";
        boolean failed;
        message += checkForMechOrRemoveInf(planet, game, player);
        failed = message.contains("Please try again.");
        if (!failed) {
            if ("ac".equalsIgnoreCase(acOrAgent)) {
                if (player.hasAbility("scheming")) {
                    game.drawActionCard(player.getUserID());
                    game.drawActionCard(player.getUserID());
                    message = player.getFactionEmoji() + " Drew 2 ACs with Scheming. Please discard 1 AC with the blue buttons.";
                    MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                        player.getRepresentationUnfogged() + " use buttons to discard",
                        ActionCardHelper.getDiscardActionCardButtons(player, false));
                } else {
                    game.drawActionCard(player.getUserID());
                    message = player.getFactionEmoji() + " Drew 1 AC";
                    ActionCardHelper.sendActionCardInfo(game, player, event);
                }
                CommanderUnlockCheck.checkPlayer(player, "yssaril");
            } else {
                Leader playerLeader = player.getLeader(acOrAgent).orElse(null);
                if (playerLeader == null) {
                    return;
                }
                RefreshLeader.refreshLeader(player, playerLeader, game);
                message += " Refreshed " + Mapper.getLeader(acOrAgent).getName();
            }
            ButtonHelper.addReaction(event, false, false, message, "");
            ButtonHelper.deleteMessage(event);
        } else {
            ButtonHelper.addReaction(event, false, false, message, "");
        }
    }

    public static String getUnitListEmojisOnPlanetForHazardousExplorePurposes(Game game, Player player, String planetID) {
        String message = "";
        Planet planet = game.getUnitHolderFromPlanet(planetID);
        if (planet != null) {
            String planetName = Mapper.getPlanet(planetID) == null ? "`error?`" : Mapper.getPlanet(planetID).getName();
            String unitList = planet.getPlayersUnitListEmojisOnHolder(player);
            if (unitList.isEmpty()) {
                message += "no units on " + planetName;
            } else {
                message += unitList + " on " + planetName;
            }
        }
        return message;
    }
}
