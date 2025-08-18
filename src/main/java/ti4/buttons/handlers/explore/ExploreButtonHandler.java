package ti4.buttons.handlers.explore;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ExploreHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.PlanetService;
import ti4.service.button.ReactionService;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
class ExploreButtonHandler {

    @ButtonHandler("resolveLocalFab_")
    public static void resolveLocalFabricators(
            String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetName = buttonID.split("_")[1];
        String commOrTg;
        if (player.getCommodities() > 0) {
            player.setCommodities(player.getCommodities() - 1);
            commOrTg = "commodity";
            if (player.getPromissoryNotesInPlayArea().contains("dark_pact")) {
                commOrTg += " (though you may wish to manually spend a trade good instead because of _Dark Pact_)";
            }
        } else if (player.getTg() > 0) {
            player.setTg(player.getTg() - 1);
            commOrTg = "trade good";
        } else {
            ReactionService.addReaction(
                    event,
                    game,
                    player,
                    "Didn't have any commodities or trade goods to spend, so no mech has been placed.");
            return;
        }
        AddUnitService.addUnits(
                event, TileHelper.getTile(event, planetName, game), game, player.getColor(), "mech " + planetName);
        planetName = Mapper.getPlanet(planetName) == null
                ? "`error?`"
                : Mapper.getPlanet(planetName).getName();
        ReactionService.addReaction(event, game, player, "Spent a " + commOrTg + " for a mech on " + planetName + ".");
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            String pF = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), pF + " Spent a " + commOrTg + " for a mech on " + planetName + ".");
        }
        CommanderUnlockCheckService.checkPlayer(player, "naaz");
    }

    @ButtonHandler("resolveVolatileMech_")
    public static void resolveVolatileFuelSourceMech(
            String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetID = StringUtils.substringAfter(buttonID, "_");
        if (!ExploreHelper.checkForMech(planetID, game, player)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), planetID + " does not seem to contain a mech, please try again.");
            return;
        }

        String message = player.getRepresentation() + " is using a mech to resolve _Volatile Fuel Source_.";
        message +=
                " Please gain 1 command token. Your current command tokens are " + player.getCCRepresentation() + ".";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);

        if (!event.getMessage().getContentRaw().contains("fragment")) {
            ButtonHelper.deleteMessage(event);
            if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), player.getFactionEmoji() + " has a mech.");
            }
        }
    }

    @ButtonHandler("resolveVolatileInf_")
    public static void resolveVolatileFuelSourceInf(
            String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetID = StringUtils.substringAfter(buttonID, "_");
        if (!ExploreHelper.checkForInf(planetID, game, player)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), planetID + " does not seem to contain an infantry, please try again.");
            return;
        }

        Tile tile = game.getTile(AliasHandler.resolveTile(planetID));
        Planet planet = tile.getUnitHolderFromPlanet(planetID);
        RemoveUnitService.removeUnit(event, tile, game, player, planet, UnitType.Infantry, 1);

        String message = player.getRepresentation() + " is removing an infantry to resolve _Volatile Fuel Source_.";
        message +=
                " Please gain 1 command token. Your current command tokens are " + player.getCCRepresentation() + ".";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);

        if (!event.getMessage().getContentRaw().contains("fragment")) {
            // what is this here for?
            ButtonHelper.deleteMessage(event);
            if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), player.getFactionEmoji() + " has had an infantry removed.");
            }
        }
    }

    @ButtonHandler("resolveExpeditionMech_")
    public static void resolveExpeditionMech(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetID = StringUtils.substringAfter(buttonID, "_");
        if (!ExploreHelper.checkForMech(planetID, game, player)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), planetID + " does not seem to contain a mech, please try again.");
            return;
        }

        PlanetService.refreshPlanet(player, planetID);
        String message = player.getRepresentation() + " is using a mech to resolve _Expedition_. ";
        message += Helper.getPlanetRepresentation(planetID, game) + " has been readied.";
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveExpeditionInf_")
    public static void resolveExpeditionInf(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetID = StringUtils.substringAfter(buttonID, "_");
        if (!ExploreHelper.checkForInf(planetID, game, player)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), planetID + " does not seem to contain an infantry, please try again.");
            return;
        }

        Tile tile = game.getTile(AliasHandler.resolveTile(planetID));
        Planet planet = tile.getUnitHolderFromPlanet(planetID);
        RemoveUnitService.removeUnit(event, tile, game, player, planet, UnitType.Infantry, 1);

        PlanetService.refreshPlanet(player, planetID);
        String message = player.getRepresentation() + " is removing an infantry to resolve _Expedition_. ";
        message += Helper.getPlanetRepresentation(planetID, game) + " has been readied.";
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveCoreMineMech_")
    public static void resolveCoreMineMech(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetID = StringUtils.substringAfter(buttonID, "_");
        if (!ExploreHelper.checkForMech(planetID, game, player)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), planetID + " does not seem to contain a mech, please try again.");
            return;
        }

        String message = player.getRepresentation() + " is using a mech to resolve _Core Mine_.";
        message += " Gained 1 trade good " + player.gainTG(1, true) + ".";
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            String pF = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), pF + " " + message);
        }
    }

    @ButtonHandler("resolveCoreMineInf_")
    public static void resolveCoreMineInf(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetID = StringUtils.substringAfter(buttonID, "_");
        if (!ExploreHelper.checkForInf(planetID, game, player)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), planetID + " does not seem to contain an infantry, please try again.");
            return;
        }

        Tile tile = game.getTile(AliasHandler.resolveTile(planetID));
        Planet planet = tile.getUnitHolderFromPlanet(planetID);
        RemoveUnitService.removeUnit(event, tile, game, player, planet, UnitType.Infantry, 1);

        String message = player.getRepresentation() + " is removing an infantry to resolve _Core Mine_. ";
        message += " Gained 1 trade good " + player.gainTG(1, true) + ".";
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            String pF = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), pF + " " + message);
        }
    }

    @ButtonHandler("resolveRuinsMech_")
    public static void resolveWarForgeRuinsMech(
            Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        String planetID = buttonID.split("_")[1];
        String placedUnit = "mech".equalsIgnoreCase(buttonID.split("_")[2]) ? "mech" : "2 infantry";
        if (!ExploreHelper.checkForMech(planetID, game, player)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), planetID + " does not seem to contain a mech, please try again.");
            return;
        }

        AddUnitService.addUnits(
                event, game.getTileFromPlanet(planetID), game, player.getColor(), placedUnit + " " + planetID);
        String message = player.getRepresentation() + " is using a mech to resolve _War Forge Ruins_.";
        message += " Placing " + placedUnit + " on " + Helper.getPlanetRepresentation(planetID, game) + ".";
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveRuinsInf_")
    public static void resolveWarForgeRuinsInf(
            Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        String planetID = buttonID.split("_")[1];
        String placedUnit = "mech".equalsIgnoreCase(buttonID.split("_")[2]) ? "mech" : "2 infantry";
        if (!ExploreHelper.checkForInf(planetID, game, player)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), planetID + " does not seem to contain an infantry, please try again.");
            return;
        }

        Tile tile = game.getTile(AliasHandler.resolveTile(planetID));
        Planet planet = tile.getUnitHolderFromPlanet(planetID);
        RemoveUnitService.removeUnit(event, tile, game, player, planet, UnitType.Infantry, 1);

        AddUnitService.addUnits(
                event, game.getTileFromPlanet(planetID), game, player.getColor(), placedUnit + " " + planetID);
        String message = player.getRepresentation() + " is removing an infantry to resolve _War Forge Ruins_.";
        message += " Placing " + placedUnit + " on " + Helper.getPlanetRepresentation(planetID, game) + ".";
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveSeedySpaceMech_")
    public static void resolveSeedySpaceMech(Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        String planetID = buttonID.split("_")[2];
        String agent = buttonID.split("_")[1];
        if (!ExploreHelper.checkForMech(planetID, game, player)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), planetID + " does not seem to contain a mech, please try again.");
            return;
        }

        String message = " is using a mech to resolve _Seedy Space Port_.";
        if ("ac".equalsIgnoreCase(agent)) {
            if (player.hasAbility("scheming")) {
                game.drawActionCard(player.getUserID());
                game.drawActionCard(player.getUserID());
                message +=
                        " Drew 2 action cards with **Scheming**. Please discard 1 action card with the blue buttons.";
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        player.getRepresentationUnfogged() + " use buttons to discard.",
                        ActionCardHelper.getDiscardActionCardButtons(player, false));
            } else {
                game.drawActionCard(player.getUserID());
                message += " Drew 1 action card.";
                ActionCardHelper.sendActionCardInfo(game, player, event);
            }
            CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        } else {
            Leader playerLeader = player.getLeader(agent).orElse(null);
            if (playerLeader == null) {
                return;
            }
            RefreshLeaderService.refreshLeader(player, playerLeader, game);
            message += " Readied " + Mapper.getLeader(agent).getName() + ".";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveSeedySpaceInf_")
    public static void resolveSeedySpaceInf(Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        String planetID = buttonID.split("_")[2];
        String agent = buttonID.split("_")[1];
        if (!ExploreHelper.checkForInf(planetID, game, player)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), planetID + " does not seem to contain an infantry, please try again.");
            return;
        }

        Tile tile = game.getTile(AliasHandler.resolveTile(planetID));
        Planet planet = tile.getUnitHolderFromPlanet(planetID);
        RemoveUnitService.removeUnit(event, tile, game, player, planet, UnitType.Infantry, 1);

        String message = player.getRepresentation() + " is removing an infantry to resolve _Seedy Space Port_.";
        if ("ac".equalsIgnoreCase(agent)) {
            if (player.hasAbility("scheming")) {
                game.drawActionCard(player.getUserID());
                game.drawActionCard(player.getUserID());
                message +=
                        " Drew 2 action cards with **Scheming**. Please discard 1 action card with the blue buttons.";
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        player.getRepresentationUnfogged() + " use buttons to discard.",
                        ActionCardHelper.getDiscardActionCardButtons(player, false));
            } else {
                game.drawActionCard(player.getUserID());
                message += " Drew 1 action card.";
                ActionCardHelper.sendActionCardInfo(game, player, event);
            }
            CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        } else {
            Leader playerLeader = player.getLeader(agent).orElse(null);
            if (playerLeader == null) {
                return;
            }
            RefreshLeaderService.refreshLeader(player, playerLeader, game);
            message += " Readied " + Mapper.getLeader(agent).getName() + ".";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolve_explore_")
    public static void resolveExplore(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ExploreService.resolveExplore(event, player, buttonID, game);
    }

    @ButtonHandler("comm_for_AC")
    static void commForAC(ButtonInteractionEvent event, Game game, Player player) {
        boolean hasSchemingAbility = player.hasAbility("scheming");
        int count2 = hasSchemingAbility ? 2 : 1;
        String commOrTg;
        if (player.getCommodities() > 0) {
            commOrTg = "commodity";
            player.setCommodities(player.getCommodities() - 1);

        } else if (player.getTg() > 0) {
            player.setTg(player.getTg() - 1);
            commOrTg = "trade good";
        } else {
            ReactionService.addReaction(
                    event,
                    game,
                    player,
                    " didn't have any commodities or trade goods to spend, so no action card was drawn.");
            return;
        }
        String message = hasSchemingAbility
                ? " spent 1 " + commOrTg + " to draw " + count2
                        + " action card (**Scheming** added 1 action card). Please discard 1 action card from your hand."
                : " spent 1 " + commOrTg + " to draw " + count2 + " action card.";
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, count2);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            for (int i = 0; i < count2; i++) {
                game.drawActionCard(player.getUserID());
            }
            ButtonHelper.checkACLimit(game, player);
            ActionCardHelper.sendActionCardInfo(game, player, event);
        }

        CommanderUnlockCheckService.checkPlayer(player, "yssaril");

        if (hasSchemingAbility) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " use buttons to discard.",
                    ActionCardHelper.getDiscardActionCardButtons(player, false));
        }

        ReactionService.addReaction(event, game, player, message);
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            String pF = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), pF + " " + message);
        }
    }

    @ButtonHandler("resFrontier_")
    static void resFrontier(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        buttonID = buttonID.replace("resFrontier_", "");
        String[] stuff = buttonID.split("_");
        String cardChosen = stuff[0];
        String pos = stuff[1];
        String cardRefused = stuff[2];
        game.addExplore(cardRefused);
        ExploreService.expFrontAlreadyDone(event, game.getTileByPosition(pos), game, player, cardChosen);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("explore_look_All")
    static void exploreLookAll(ButtonInteractionEvent event, Player player, Game game) {
        List<String> order = List.of("cultural", "industrial", "hazardous");
        for (String type : order) {
            List<String> deck = game.getExploreDeck(type);
            List<String> discard = game.getExploreDiscard(type);

            String traitNameWithEmoji = ExploreEmojis.getTraitEmoji(type) + type;
            if (deck.isEmpty() && discard.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        traitNameWithEmoji + " exploration deck & discard is empty - nothing to look at.");
                continue;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("__**Look at Top of ").append(traitNameWithEmoji).append(" Deck**__\n");
            ExploreModel exp = Mapper.getExplore(deck.getFirst());
            sb.append(exp.textRepresentation());
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, sb.toString());
        }

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "The top card of each of the cultural, industrial, and hazardous exploration decks has been set to "
                        + player.getFactionEmoji() + " `#cards-info` thread.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("discardExploreTop_")
    static void discardExploreTop(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String deckType = buttonID.replace("discardExploreTop_", "");
        ButtonHelperFactionSpecific.resolveExpDiscard(player, game, event, deckType);
    }

    @ButtonHandler("resolveExp_Look_")
    static void resolveExpLook(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String deckType = buttonID.replace("resolveExp_Look_", "");
        ButtonHelperFactionSpecific.resolveExpLook(player, game, event, deckType);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("movedNExplored_")
    static void movedNExplored(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String bID = buttonID.replace("movedNExplored_", "");
        boolean dsdihmy = bID.startsWith("dsdihmy_");
        String[] info = bID.split("_");
        Tile tile = game.getTileFromPlanet(info[1]);
        ExploreService.explorePlanet(
                event, game.getTileFromPlanet(info[1]), info[1], info[2], player, false, game, 1, false);
        if (dsdihmy) {
            player.exhaustPlanet(info[1]);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), info[1] + " was exhausted by _Impressment Programs_.");
        }
        if (tile != null && player.getTechs().contains("dsdihmy")) {
            var produce = new ArrayList<Button>();
            String pos = tile.getPosition();
            produce.add(Buttons.blue("dsdihmy_" + pos, "Produce (1) Units"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", you explored a planet, and due to _Impressment Programs_ you may now produce 1 ship in the system.",
                    produce);
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("exploreAPlanet")
    static void exploreAPlanet(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please use these buttons to explore.",
                buttons);
    }

    @ButtonHandler("shuffleExplores")
    static void shuffleExplores(ButtonInteractionEvent event, Game game) {
        game.shuffleExplores();
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("decline_explore")
    static void declineExplore(ButtonInteractionEvent event, Player player, Game game, MessageChannel mainGameChannel) {
        ReactionService.addReaction(event, game, player, "declined exploration card.");
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            String pF = player.getFactionEmoji();
            MessageHelper.sendMessageToChannel(mainGameChannel, pF + " declined exploration card.");
        }
    }
}
