package ti4.service.leader.agent.modules.homebrew.beans;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.deep.DeepUnitHandler;
import ti4.discord.interactions.commands.tokens.AddTokenCommand;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.explore.ExploreService;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class DeepAgent extends TargetedAgent {

    static final String ID = "deepagent";
    private static final String EXPLORE_FRONTIER = "deepAgentExploreFrontier_";
    private static final String PLACE_HOME_FRONTIER = "deepAgentPlaceHomeFrontier";
    private static final String SEPARATOR = "|";

    public DeepAgent() {
        super(ID, "Nhal, the First");
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (target == user) {
            return Optional.of("Nhal must target another player");
        }
        if (eligibleFrontierTiles(game, target).isEmpty()) {
            return Optional.of(target.getFactionEmojiOrColor() + " has no eligible frontier token to explore");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.of(Message.addressedTo(
                        target,
                        ", you may explore a frontier token in or adjacent to a system containing one of your ships with "
                                + use.agentNameFor(target) + ".",
                        exploreButtons(use.game(), use.user(), target, 0)))
                .deletingPressedMessage();
    }

    @ButtonHandler(EXPLORE_FRONTIER)
    static void exploreFrontier(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        Player owner = owner(game, buttonID);
        String prompt = frontierPrompt(target);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                target.getCorrectChannel(),
                eligibleButtons(game, owner, target),
                prompt,
                exploreButtonPrefix(owner, target),
                buttonID)) {
            return;
        }
        Tile tile = frontierTile(game, buttonID);
        if (owner == null
                || tile == null
                || !eligibleFrontierTiles(game, target).contains(tile)) {
            return;
        }

        ExploreService.expFront(event, tile, game, target);
        offerHomeFrontier(owner);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_HOME_FRONTIER)
    static void placeHomeFrontier(ButtonInteractionEvent event, Game game, Player player) {
        Tile homeSystem = homeSystem(game, player);
        if (homeSystem == null
                || homeSystem.getSpaceUnitHolder().getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER))) {
            return;
        }
        AddTokenCommand.addToken(event, homeSystem, Constants.FRONTIER, game);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed a frontier token in their home system with Nhal.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> exploreButtons(Game game, Player owner, Player target, int page) {
        return NewStuffHelper.buttonPagination(
                eligibleButtons(game, owner, target), null, exploreButtonPrefix(owner, target), 24, page, true);
    }

    private static List<Button> eligibleButtons(Game game, Player owner, Player target) {
        return eligibleFrontierTiles(game, target).stream()
                .map(tile -> Buttons.green(
                        target.factionButtonChecker()
                                + EXPLORE_FRONTIER
                                + owner.getFaction()
                                + SEPARATOR
                                + tile.getPosition(),
                        "Explore " + tile.getRepresentationForButtons(game, target)))
                .toList();
    }

    private static String exploreButtonPrefix(Player owner, Player target) {
        return target.factionButtonChecker() + EXPLORE_FRONTIER + owner.getFaction() + SEPARATOR;
    }

    private static List<Tile> eligibleFrontierTiles(Game game, Player player) {
        String frontierToken = Mapper.getTokenID(Constants.FRONTIER);
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getTokenList().contains(frontierToken))
                .filter(tile -> FoWHelper.playerHasActualShipsInSystem(player, tile)
                        || FoWHelper.playerHasShipsInAdjacentSystems(player, tile, game))
                .toList();
    }

    private static Player owner(Game game, String buttonID) {
        String payload = buttonID.substring(EXPLORE_FRONTIER.length());
        int separator = payload.indexOf(SEPARATOR);
        return separator < 0 ? null : game.getPlayerFromColorOrFaction(payload.substring(0, separator));
    }

    private static Tile frontierTile(Game game, String buttonID) {
        String payload = buttonID.substring(EXPLORE_FRONTIER.length());
        int separator = payload.indexOf(SEPARATOR);
        return separator < 0 ? null : game.getTileByPosition(payload.substring(separator + SEPARATOR.length()));
    }

    private static void offerHomeFrontier(Player player) {
        Tile homeSystem = homeSystem(player.getGame(), player);
        if (homeSystem == null
                || homeSystem.getSpaceUnitHolder().getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER))) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", you may place a frontier token in your home system with Nhal.",
                List.of(
                        Buttons.green(player.factionButtonChecker() + PLACE_HOME_FRONTIER, "Place Frontier Token"),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    private static Tile homeSystem(Game game, Player player) {
        return DeepUnitHandler.isDeepPlayer(player)
                ? DeepUnitHandler.getUnitTile(game, player)
                : player.getHomeSystemTile();
    }

    private static String frontierPrompt(Player target) {
        return target.getRepresentationUnfogged()
                + ", you may explore a frontier token in or adjacent to a system containing one of your ships with Nhal, the First agent.";
    }
}
