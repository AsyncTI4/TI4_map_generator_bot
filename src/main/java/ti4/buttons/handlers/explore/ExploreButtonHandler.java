package ti4.buttons.handlers.explore;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ExploreHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.PlanetService;
import ti4.service.button.ReactionService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.unit.AddUnitService;

@UtilityClass
class ExploreButtonHandler {

    @ButtonHandler("resolveLocalFab_")
    public static void resolveLocalFabricators(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetName = buttonID.split("_")[1];
        String commOrTg;
        if (player.getCommodities() > 0) {
            player.setCommodities(player.getCommodities() - 1);
            commOrTg = "commodity";
            if (player.getPromissoryNotesInPlayArea().contains("dark_pact"))
            {
                commOrTg += " (though you may wish to manually spend a trade good instead because of _Dark Pact_)";
            }
        } else if (player.getTg() > 0) {
            player.setTg(player.getTg() - 1);
            commOrTg = "trade good";
        } else {
            ReactionService.addReaction(event, game, player, "Didn't have any commodities or trade goods to spend, so no mech has been placed.");
            return;
        }
        AddUnitService.addUnits(event, game.getTile(AliasHandler.resolveTile(planetName)), game, player.getColor(), "mech " + planetName);
        planetName = Mapper.getPlanet(planetName) == null ? "`error?`" : Mapper.getPlanet(planetName).getName();
        ReactionService.addReaction(event, game, player, "Spent a " + commOrTg + " for a mech on " + planetName + ".");
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            String pF = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), pF + " Spent a " + commOrTg + " for a mech on " + planetName +".");
        }
    }

    @ButtonHandler("resolveVolatile_")
    public static void resolveVolatileFuelSource(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetID = StringUtils.substringAfter(buttonID, "_");
        String mechOrInfCheckMessage = ExploreHelper.checkForMechOrRemoveInf(planetID, game, player);
        boolean failed = mechOrInfCheckMessage.contains("Please try again.");

        if (!failed) {
            String message = player.getRepresentation() + ", " + mechOrInfCheckMessage + "Please gain 1 command token. Your current command tokens are " + player.getCCRepresentation();
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        }

        if (!failed && !event.getMessage().getContentRaw().contains("fragment")) {
            ButtonHelper.deleteMessage(event);
            if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " " + mechOrInfCheckMessage);
            }
        }
    }

    @ButtonHandler("resolveExpedition_")
    public static void resolveExpedition(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetName = buttonID.split("_")[1];
        String message = ExploreHelper.checkForMechOrRemoveInf(planetName, game, player);
        boolean failed = message.contains("Please try again.");
        if (failed) {
            ReactionService.addReaction(event, game, player, message);
            return;
        }
        PlanetService.refreshPlanet(player, planetName);
        planetName = Mapper.getPlanet(planetName) == null ? planetName : Mapper.getPlanet(planetName).getName();
        message = player.getRepresentation() + ", " + message + "Readied " + planetName + ".";
        ReactionService.addReaction(event, game, player, message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveCoreMine_")
    public static void resolveCoreMine(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetName = buttonID.split("_")[1];
        String message = ExploreHelper.checkForMechOrRemoveInf(planetName, game, player);
        boolean failed = message.contains("Please try again.");
        if (!failed) {
            message = player.getRepresentation() + ", " + message + "Gained 1 trade good " + player.gainTG(1, true) + ".";
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }
        ReactionService.addReaction(event, game, player, message);
        if (!failed) {
            ButtonHelper.deleteMessage(event);
            if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
                String pF = player.getFactionEmoji();
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), pF + " " + message);
            }
        }
    }

    @ButtonHandler("ruins_")
    public static void resolveWarForgeRuins(Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[1];
        String mech = buttonID.split("_")[2];
        String message = ExploreHelper.checkForMechOrRemoveInf(planet, game, player);
        boolean failed = message.contains("Please try again.");
        if (failed) {
            ReactionService.addReaction(event, game, player, message);
            return;
        }
        if ("mech".equalsIgnoreCase(mech)) {
            AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "mech " + planet);
            message = player.getRepresentation() + ", " + message + "Placed mech on" + Mapper.getPlanet(planet).getName();
        } else {
            AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "2 infantry " + planet);
            message = player.getRepresentation() + ", " + message + "Placed " + UnitEmojis.infantry + UnitEmojis.infantry + " on" + Mapper.getPlanet(planet).getName();
        }
        ReactionService.addReaction(event, game, player, message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("seedySpace_")
    public static void resolveSeedySpace(Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        String planet = buttonID.split("_")[2];
        String acOrAgent = buttonID.split("_")[1];
        String message = ExploreHelper.checkForMechOrRemoveInf(planet, game, player);
        boolean failed = message.contains("Please try again.");
        if (failed) {
            ReactionService.addReaction(event, game, player, message);
            return;
        }
        if ("ac".equalsIgnoreCase(acOrAgent)) {
            if (player.hasAbility("scheming")) {
                game.drawActionCard(player.getUserID());
                game.drawActionCard(player.getUserID());
                message = player.getRepresentation() + ", " + message + "Drew 2 action cards with **Scheming**. Please discard 1 action card with the blue buttons.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " use buttons to discard.",
                    ActionCardHelper.getDiscardActionCardButtons(player, false));
            } else {
                game.drawActionCard(player.getUserID());
                message = player.getRepresentation() + ", " + message + "Drew 1 action card.";
                ActionCardHelper.sendActionCardInfo(game, player, event);
            }
            CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        } else {
            Leader playerLeader = player.getLeader(acOrAgent).orElse(null);
            if (playerLeader == null) {
                return;
            }
            RefreshLeaderService.refreshLeader(player, playerLeader, game);
            message = player.getRepresentation() + ", " + message + "Readied " + Mapper.getLeader(acOrAgent).getName();
        }
        ReactionService.addReaction(event, game, player, message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolve_explore_")
    public static void resolveExplore(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ExploreService.resolveExplore(event, player, buttonID, game);
    }
}
