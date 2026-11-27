package ti4.service.leader.agent.modules.homebrew.goose;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.adjacency.Adjacency;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;
import ti4.service.unit.MoveUnitService;

public final class BrynfolkAgent extends AgentModule<Player> {

    static final String ID = "brynfolkagent";
    private static final String SELECT_ACTIVE_SYSTEM = "selectBrynfolkAgentActiveSystem_";
    private static final String SELECT_SHIP = "selectBrynfolkAgentShip_";
    private static final String SELECT_DESTINATION = "selectBrynfolkAgentDestination_";
    private static final String SELECT_OTHER_TARGET = "selectOtherBrynfolkAgentTarget";
    private static final String USE_OTHER = "useOtherBrynfolkAgent_";
    private static final String SEPARATOR = "|";

    public BrynfolkAgent() {
        super(ID, "Olistef Olafsson, the Brynfolk");
    }

    public static void offerAtEndOfTacticalAction(Game game, Player activePlayer) {
        Player agentOwner = game.getPlayerFromLeader(ID);
        if (activePlayer == null || agentOwner == null || !agentOwner.hasUnexhaustedLeader(ID)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                activePlayer == agentOwner ? agentOwner.getCorrectChannel() : agentOwner.getCardsInfoThread(),
                agentOwner.getRepresentationNoPing()
                        + ", you may use _Olistef Olafsson, the Brynfolk agent_ to move 1 of the active player's "
                        + "ships from the active system.",
                List.of(
                        Buttons.green(
                                ownedButtonId(agentOwner, ID, activePlayer.getFaction()),
                                useLabel(agentOwner, ID, "Brynfolk Agent"),
                                FactionEmojis.brynfolk),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    public static void addCardsInfoButton(List<Button> buttons, Player player) {
        if (player.hasUnexhaustedLeader(ID)) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + SELECT_OTHER_TARGET,
                    useLabel(player, ID, "Brynfolk Agent"),
                    FactionEmojis.brynfolk));
        }
    }

    @ButtonHandler(SELECT_OTHER_TARGET)
    static void selectOtherTarget(ButtonInteractionEvent event, Game game, Player agentOwner) {
        List<Button> buttons = game.getRealPlayersExcludingThis(agentOwner).stream()
                .map(target -> Buttons.gray(
                        agentOwner.factionButtonChecker() + USE_OTHER + target.getFaction(),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                agentOwner.getRepresentationNoPing()
                        + ", please choose the player on whom you wish to use _Olistef Olafsson_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_OTHER)
    static void useOnOtherPlayer(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(USE_OTHER.length()));
        if (target == null || target == agentOwner) {
            return;
        }
        AgentLifecycle.use(game, agentOwner, ID, target.getFaction(), event);
    }

    @ButtonHandler(SELECT_ACTIVE_SYSTEM)
    static void selectActiveSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(SELECT_ACTIVE_SYSTEM.length()).split("\\|", 2);
        Tile activeSystem = parts.length == 2 ? game.getTileByPosition(parts[1]) : null;
        if (activeSystem == null || !parts[0].equals(player.getFaction())) {
            return;
        }
        List<Button> shipButtons = activeSystem.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                .filter(key -> isShip(player, key))
                .map(key -> Buttons.green(
                        player.factionButtonChecker()
                                + SELECT_SHIP
                                + activeSystem.getPosition()
                                + SEPARATOR
                                + key.asyncID(),
                        "Move " + player.getUnitFromUnitKey(key).getName(),
                        player.getUnitFromUnitKey(key).getUnitEmoji()))
                .toList();
        if (shipButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no ships in the active system.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose the ship to move with _Olistef Olafsson_.",
                shipButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_SHIP)
    static void selectShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(SELECT_SHIP.length()).split("\\|", 2);
        Tile activeSystem = parts.length == 2 ? game.getTileByPosition(parts[0]) : null;
        UnitKey ship = activeSystem == null ? null : unitKey(player, activeSystem, parts.length == 2 ? parts[1] : "");
        if (activeSystem == null || !isShip(player, ship)) {
            return;
        }
        List<Button> destinationButtons =
                Adjacency.of(game, player).forMovement().from(activeSystem.getPosition()).stream()
                        .map(game::getTileByPosition)
                        .filter(destination -> destination != null
                                && !FoWHelper.otherPlayersHaveShipsInSystem(player, destination, game))
                        .map(destination -> Buttons.green(
                                player.factionButtonChecker()
                                        + SELECT_DESTINATION
                                        + activeSystem.getPosition()
                                        + SEPARATOR
                                        + ship.asyncID()
                                        + SEPARATOR
                                        + destination.getPosition(),
                                "Move to " + destination.getRepresentationForButtons(game, player)))
                        .toList();
        if (destinationButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), player.getRepresentationNoPing() + " has no eligible adjacent systems.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose the adjacent system for that ship.",
                destinationButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_DESTINATION)
    static void moveShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(SELECT_DESTINATION.length()).split("\\|", 3);
        Tile activeSystem = parts.length == 3 ? game.getTileByPosition(parts[0]) : null;
        Tile destination = parts.length == 3 ? game.getTileByPosition(parts[2]) : null;
        UnitKey ship = activeSystem == null ? null : unitKey(player, activeSystem, parts.length == 3 ? parts[1] : "");
        if (activeSystem == null || destination == null || !isShip(player, ship)) {
            return;
        }
        MoveUnitService.moveUnits(
                event, activeSystem, game, player.getColor(), "1 " + ship.asyncID(), destination, Constants.SPACE);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " moved 1 "
                        + player.getUnitFromUnitKey(ship).getName() + " from "
                        + activeSystem.getRepresentationForButtons(game, player) + " to "
                        + destination.getRepresentationForButtons(game, player) + " with _Olistef Olafsson_.");
        ButtonHelper.deleteMessage(event);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.player(game, payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Tile activeSystem = use.game().getTileByPosition(use.game().getActiveSystem());
        if (activeSystem == null) {
            return AgentOutcome.none().deletingPressedMessage();
        }
        Player target = use.payload();
        return AgentOutcome.of(Message.addressedTo(
                                target,
                                ", please choose the active system for _Olistef Olafsson_.",
                                List.of(Buttons.green(
                                        target.factionButtonChecker()
                                                + SELECT_ACTIVE_SYSTEM
                                                + target.getFaction()
                                                + SEPARATOR
                                                + activeSystem.getPosition(),
                                        activeSystem.getRepresentationForButtons(use.game(), target))))
                        .inCorrectChannel())
                .deletingPressedMessage();
    }

    private static UnitKey unitKey(Player player, Tile tile, String asyncId) {
        return tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                .filter(key -> asyncId.equals(key.asyncID()))
                .findFirst()
                .orElse(null);
    }

    private static boolean isShip(Player player, UnitKey unitKey) {
        UnitModel unit = unitKey == null ? null : player.getUnitFromUnitKey(unitKey);
        return unit != null && unit.getIsShip();
    }
}
