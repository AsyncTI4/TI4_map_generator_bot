package ti4.discord.interactions.buttons.handlers.explore;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.kairn.KairnLeadershandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ExploreHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.button.ReactionService;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.planet.PlanetService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
class ExploreButtonHandler {

    @ButtonHandler("resolveLocalFab_")
    public static void resolveLocalFabricators(
            String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String planetName = buttonID.split("_")[1];
        String commOrTg = ExploreService.spendCommodityOrTg(player);
        if (commOrTg == null) {
            ReactionService.addReaction(
                    event,
                    game,
                    player,
                    "Didn't have any commodities or trade goods to spend, so no mech has been placed.");
            return;
        }
        Tile tile = TileHelper.getTile(event, planetName, game);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "mech " + planetName);
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
        if (tile != null && tile.isFracture()) {
            CommanderUnlockCheckService.checkPlayer(player, "obsidian");
        }
    }

    private static boolean lacksUnit(
            boolean mech, String planetID, Game game, Player player, ButtonInteractionEvent event) {
        boolean hasUnit = mech
                ? ExploreHelper.checkForMech(planetID, game, player)
                : ExploreHelper.checkForInf(planetID, game, player);
        if (hasUnit) {
            return false;
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                planetID + " does not seem to contain " + (mech ? "a mech" : "an infantry") + ", please try again.");
        return true;
    }

    private static void removeInfantry(
            Tile tile,
            String planetID,
            Game game,
            Player player,
            ButtonInteractionEvent event,
            boolean resolveRemoval) {
        Planet planet = tile.getUnitHolderFromPlanet(planetID);
        RemoveUnitService.removeUnit(event, tile, game, player, planet, UnitType.Infantry, 1);
        if (resolveRemoval) {
            ButtonHelper.resolveInfantryRemoval(player, 1, tile);
        }
    }

    private static String payHazardCost(
            boolean mech,
            String planetID,
            String cardName,
            Game game,
            Player player,
            ButtonInteractionEvent event,
            boolean resolveRemoval) {
        if (lacksUnit(mech, planetID, game, player, event)) {
            return null;
        }
        if (mech) {
            return player.getRepresentationNoPing() + " is using a mech to resolve _" + cardName + "_.";
        }
        removeInfantry(game.getTile(AliasHandler.resolveTile(planetID)), planetID, game, player, event, resolveRemoval);
        return player.getRepresentation() + " is removing an infantry to resolve _" + cardName + "_.";
    }

    @ButtonHandler("resolveVolatileMech_")
    public static void resolveVolatileFuelSourceMech(
            String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        resolveVolatileFuelSource(buttonID, game, player, event, true);
    }

    @ButtonHandler("resolveVolatileInf_")
    public static void resolveVolatileFuelSourceInf(
            String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        resolveVolatileFuelSource(buttonID, game, player, event, false);
    }

    private static void resolveVolatileFuelSource(
            String buttonID, Game game, Player player, ButtonInteractionEvent event, boolean mech) {
        String planetID = StringUtils.substringAfter(buttonID, "_");
        String message = payHazardCost(mech, planetID, "Volatile Fuel Source", game, player, event, true);
        if (message == null) {
            return;
        }
        String gainMessage =
                " Please gain 1 command token. Your current command tokens are " + player.getCCRepresentation() + ".";
        if (mech) {
            gainMessage = message + gainMessage;
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(), gainMessage, ButtonHelper.getGainCCButtons(player));

        if (!event.getMessage().getContentRaw().contains("fragment")) {
            ButtonHelper.deleteMessage(event);
            if (!game.isFowMode() && event.getChannel() != game.getActionsChannel()) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getFactionEmoji() + (mech ? " has a mech." : " has had an infantry removed."));
            }
        }
    }

    @ButtonHandler("resolveExpeditionMech_")
    public static void resolveExpeditionMech(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        resolveExpedition(buttonID, game, player, event, true);
    }

    @ButtonHandler("resolveExpeditionInf_")
    public static void resolveExpeditionInf(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        resolveExpedition(buttonID, game, player, event, false);
    }

    private static void resolveExpedition(
            String buttonID, Game game, Player player, ButtonInteractionEvent event, boolean mech) {
        String planetID = StringUtils.substringAfter(buttonID, "_");
        String message = payHazardCost(mech, planetID, "Expedition", game, player, event, true);
        if (message == null) {
            return;
        }
        PlanetService.refreshPlanet(player, planetID);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                message + " " + Helper.getPlanetRepresentation(planetID, game) + " has been readied.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveScorchedDepot_")
    public static void resolveScorchedDepot(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        String unit = buttonID.split("_")[1];
        String planetID = buttonID.split("_")[2];
        Tile tile = game.getTileFromPlanet(planetID);
        boolean mech = "mech".equalsIgnoreCase(unit);
        if (lacksUnit(mech, planetID, game, player, event)) {
            return;
        }
        if (!mech) {
            removeInfantry(tile, planetID, game, player, event, true);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentation() + " is removing an infantry to resolve _Scorched Depot_. ");
        }

        String id = player.getFactionCheckerPrefix() + "removeCCFromBoard_depot_" + tile.getPosition() + "_"
                + player.getFaction();
        String label = "Remove Token From " + tile.getRepresentationForButtons(game, player);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(id, label));
        buttons.add(Buttons.REDISTRIBUTE_CCs);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationNoPing()
                        + " is resolving _Scorched Depot_. Please remove a command token from the tile or redistribute command tokens.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveCoreMineMech_")
    public static void resolveCoreMineMech(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        resolveCoreMine(buttonID, game, player, event, true);
    }

    @ButtonHandler("resolveCoreMineInf_")
    public static void resolveCoreMineInf(String buttonID, Game game, Player player, ButtonInteractionEvent event) {
        resolveCoreMine(buttonID, game, player, event, false);
    }

    private static void resolveCoreMine(
            String buttonID, Game game, Player player, ButtonInteractionEvent event, boolean mech) {
        String planetID = StringUtils.substringAfter(buttonID, "_");
        String message = payHazardCost(mech, planetID, "Core Mine", game, player, event, true);
        if (message == null) {
            return;
        }
        message += " Gained 1 trade good " + player.gainTG(1, true) + ".";
        ButtonHelperAgents.resolveArtunoCheck(player, 1);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode() && (event.getChannel() != game.getActionsChannel())) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getFactionEmoji() + " " + message);
        }
    }

    @ButtonHandler("resolveRuinsMech_")
    public static void resolveWarForgeRuinsMech(
            Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        resolveWarForgeRuins(game, buttonID, player, event, true);
    }

    @ButtonHandler("resolveRuinsInf_")
    public static void resolveWarForgeRuinsInf(
            Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        resolveWarForgeRuins(game, buttonID, player, event, false);
    }

    private static void resolveWarForgeRuins(
            Game game, String buttonID, Player player, ButtonInteractionEvent event, boolean mech) {
        String planetID = buttonID.split("_")[1];
        String placedUnit = "mech".equalsIgnoreCase(buttonID.split("_")[2]) ? "mech" : "2 infantry";
        String message = payHazardCost(mech, planetID, "War Forge Ruins", game, player, event, true);
        if (message == null) {
            return;
        }
        AddUnitService.addUnits(
                event, game.getTileFromPlanet(planetID), game, player.getColor(), placedUnit + " " + planetID);
        message += " Placing " + placedUnit + " on " + Helper.getPlanetRepresentation(planetID, game) + ".";
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveSeedySpaceMech_")
    public static void resolveSeedySpaceMech(Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        resolveSeedySpace(game, buttonID, player, event, true);
    }

    @ButtonHandler("resolveSeedySpaceInf_")
    public static void resolveSeedySpaceInf(Game game, String buttonID, Player player, ButtonInteractionEvent event) {
        resolveSeedySpace(game, buttonID, player, event, false);
    }

    private static void resolveSeedySpace(
            Game game, String buttonID, Player player, ButtonInteractionEvent event, boolean mech) {
        String planetID = buttonID.split("_")[2];
        String agent = buttonID.split("_")[1];
        String message = payHazardCost(mech, planetID, "Seedy Space Port", game, player, event, false);
        if (message == null) {
            return;
        }
        if (mech) {
            message = " is using a mech to resolve _Seedy Space Port_.";
        }
        if ("ac".equalsIgnoreCase(agent)) {
            ActionCardHelper.drawActionCards(player, 1);
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
        String commOrTg = player.getCommodities() > 0 ? "commodity" : "trade good";
        if (player.getCommodities() > 0) {
            player.setCommodities(player.getCommodities() - 1);
        } else if (player.getTg() > 0) {
            player.setTg(player.getTg() - 1);
        } else {
            String msg = player.getFactionEmoji() + " didn't have any commodities or trade goods to spend, ";
            msg += "so no action card was drawn.";
            MessageHelper.sendMessageToEventChannel(event, msg);
            return;
        }
        String msg = player.getFactionEmoji() + " spent 1 " + commOrTg + " to resolve **Functioning Base**.";
        MessageHelper.sendMessageToEventChannel(event, msg);
        ActionCardHelper.drawActionCards(player, 1);
        ButtonHelper.deleteMessage(event);
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
        boolean skipKairnAgentInterrupt = bID.startsWith("skipKairnAgent_");
        if (skipKairnAgentInterrupt) {
            bID = bID.substring("skipKairnAgent_".length());
        }
        String[] info = bID.split("_");
        if (!skipKairnAgentInterrupt
                && KairnLeadershandler.offerKairnAgentExploreInterrupt(event, game, player, buttonID, info)) {
            return;
        }
        boolean dsdihmy = bID.startsWith("dsdihmy_");
        boolean scanlink = bID.startsWith("scanlink_");
        Tile tile = game.getTileFromPlanet(info[1]);
        if (buttonID.contains("frontier")) {
            player.setBreakthroughExhausted("bentorbt", true);
            ExploreService.expFront(event, tile, game, player, true);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " exhausted their breakthrough to explore the frontier deck instead of a normal explore.");
        } else {
            ExploreService.explorePlanet(
                    event, game.getTileFromPlanet(info[1]), info[1], info[2], player, false, game, 1, scanlink);
        }
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
