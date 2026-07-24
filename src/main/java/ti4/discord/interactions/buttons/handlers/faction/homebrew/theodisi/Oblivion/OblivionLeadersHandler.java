package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class OblivionLeadersHandler {
    private static final String AGENT = "oblivionagent";
    private static final String USE_AGENT = "useOblivionAgent";
    private static final String ADD_TOKEN = "addOblivionFrontierToken_";
    private static final String DISCARD = "discardPeekedFrontier_";
    private static final String AGENT_TARGET = "oblivionAgentTarget_";

    public static Button getOblivionAgentButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_AGENT, "Use Avaris the Seer", FactionEmojis.oblivion);
    }

    @ButtonHandler("useOblivionAgent_other")
    public static void chooseOblivionAgentTarget(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(AGENT)) {
            return;
        }

        List<Button> targetButtons = new ArrayList<>();
        for (Player target : game.getRealPlayersExcludingThis(player)) {
            targetButtons.add(Buttons.green(
                    player.factionButtonChecker() + AGENT_TARGET + target.getFaction(),
                    target.getFactionNameOrColor(),
                    target.getFactionEmojiOrColor()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), "Please choose the target of the agent:", targetButtons);
    }

    @ButtonHandler(AGENT_TARGET)
    public static void resolveOblivionAgentTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasUnexhaustedLeader(AGENT)) {
            return;
        }
        String faction = buttonID.replace(AGENT_TARGET, "");
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target == null
                || target == player
                || !game.getRealPlayersExcludingThis(player).contains(target)) {
            return;
        }

        if (target != game.getActivePlayer()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "Target player must be active player and it must be the start of their turn.");
            return;
        }

        Leader agent = player.getLeaderByID(AGENT).orElse(null);
        List<Button> buttons = getFrontierTokenButtons(game, target);
        if (agent == null || buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no eligible systems for a frontier token.");
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, player, agent);

        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.getRepresentation() + ", please select which system to place a frontier token in:",
                NewStuffHelper.buttonPagination(buttons, target.factionButtonChecker() + ADD_TOKEN, 0));
    }

    @ButtonHandler(USE_AGENT)
    public static void useOblivionAgent(ButtonInteractionEvent event, Player player, Game game) {
        if (game == null || player == null || !player.hasUnexhaustedLeader(AGENT)) {
            return;
        }
        Leader agent = player.getLeaderByID(AGENT).orElse(null);
        List<Button> buttons = getFrontierTokenButtons(game, player);
        if (agent == null || buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no eligible systems for a frontier token.");
            return;
        }
        ExhaustLeaderService.exhaustLeader(game, player, agent);

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please select which system to place a frontier token in:",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + ADD_TOKEN, 0));
    }

    @ButtonHandler(ADD_TOKEN)
    public static void resolveOblivionAgentToken(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        List<Button> buttons = getFrontierTokenButtons(game, player);
        String message = player.getRepresentation() + ", please select which system to place a frontier token in:";
        String buttonPrefix = player.factionButtonChecker() + ADD_TOKEN;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        ButtonHelper.deleteMessage(event);

        String tile = buttonID.replace(ADD_TOKEN, "");
        Tile tilePos = game.getTileByPosition(tile);
        if (tilePos == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to locate that tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        tilePos.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Frontier token placed in system. The top card of the frontier deck has been sent to your cards info.");

        resolveAgentStep2(player, game);
    }

    public static void resolveAgentStep2(Player player, Game game) {
        if (player == null || game == null) {
            return;
        }

        List<String> frontierDeck = game.getExploreDeck(Constants.FRONTIER);
        if (frontierDeck.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "The frontier deck is empty.");
            return;
        }

        String cardId = frontierDeck.getFirst();
        ExploreModel card = Mapper.getExplore(cardId);

        List<Button> buttons = List.of(
                Buttons.red(player.factionButtonChecker() + DISCARD + cardId, "Discard"),
                Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", you looked at the top card of the " + ExploreEmojis.Frontier
                        + " frontier deck and saw _" + card.getName() + "_.",
                List.of(card.getRepresentationEmbed()),
                buttons);
    }

    @ButtonHandler(DISCARD)
    public static void resolveDiscardFrontier(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String cardId = buttonID.replace(DISCARD, "");
        List<String> frontierDeck = game.getExploreDeck(Constants.FRONTIER);

        if (!frontierDeck.isEmpty() && cardId.equals(frontierDeck.getFirst())) {
            game.discardExplore(cardId);
        }

        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getFrontierTokenButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String frontierToken = Mapper.getTokenID(Constants.FRONTIER);

        for (Tile tile : game.getTileMap().values()) {
            if (!tile.getPlanetUnitHolders().isEmpty()
                    || !Mapper.getFrontierTileIds().contains(tile.getTileID())
                    || tile.getSpaceUnitHolder().getTokenList().contains(frontierToken)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + ADD_TOKEN + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }
}
