package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.unit.DestroyUnitService;

public final class MyrrAgent extends AgentModule<MyrrAgent.ShipsMoved> {

    static final String ID = "myrragent";
    private static final String LEGACY_USE = "useMyrrAgent_";
    private static final String LEGACY_USE_ON_ACTIVE_PLAYER = "useMyrrAgentOnActivePlayer";
    private static final String DESTROY = "myrrAgentDestroy_";
    private static final String PRODUCTION = "myrrAgentProduction_";
    private static final String POSITION = "myrrAgentPosition_";
    private static final String SEPARATOR = "|";
    private static final int MAX_SHIP_COST = 4;
    private static final int PRODUCTION_BONUS = 2;

    public record ShipsMoved(Player mover, Tile system, boolean fromMoveOffer) {}

    public MyrrAgent() {
        super(ID, "Kelron Dross, the Myrr");
    }

    public static void offerAfterMove(Game game, Player activatingPlayer, Tile tile) {
        if (game == null || activatingPlayer == null || tile == null) {
            return;
        }
        if (!activatingPlayer.hasUnexhaustedLeader(ID)
                || eligibleShips(activatingPlayer, tile).isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                activatingPlayer.getCorrectChannel(),
                activatingPlayer.getRepresentation() + ", you may exhaust Kelron Dross, the Myrr agent.",
                List.of(offerButton(activatingPlayer, tile), Buttons.red("deleteButtons", "Decline")));
    }

    static Button offerButton(Player holder, Tile tile) {
        return Buttons.gray(
                ownedButtonId(holder, ID, holder.getFaction() + SEPARATOR + tile.getPosition()),
                useLabel(holder, ID, "Myrr Agent"),
                FactionEmojis.myrr);
    }

    public static Button cardsInfoButton(Player holder) {
        return Buttons.gray(ownedButtonId(holder, ID), useLabel(holder, ID, "Myrr Agent"), FactionEmojis.myrr);
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_USE)
    static void legacyUse(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_USE.length()));
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_USE_ON_ACTIVE_PLAYER)
    static void legacyUseOnActivePlayer(ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, "");
    }

    @Override
    public Optional<ShipsMoved> decode(Game game, Player user, String payload) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.of(
                    new ShipsMoved(game.getActivePlayer(), game.getTileByPosition(game.getActiveSystem()), false));
        }
        String[] parts = StringUtils.split(payload, SEPARATOR);
        if (parts == null || parts.length != 2) {
            return Optional.empty();
        }
        Player mover = game.getPlayerFromColorOrFaction(parts[0]);
        Tile system = game.getTileByPosition(parts[1]);
        if (mover == null || system == null) {
            return Optional.empty();
        }
        return Optional.of(new ShipsMoved(mover, system, true));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, ShipsMoved moved) {
        if (moved.mover() == null || moved.system() == null) {
            return Optional.of("There is no active system");
        }
        if (!moved.system().getPosition().equals(game.getActiveSystem()) || moved.mover() != game.getActivePlayer()) {
            return Optional.of("Kelron Dross can only be used on the active player in the active system");
        }
        if (eligibleShips(moved.mover(), moved.system()).isEmpty()) {
            return Optional.of(moved.mover().getRepresentationNoPing() + " has no eligible ship");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<ShipsMoved> use) {
        Player mover = use.payload().mover();
        AgentOutcome outcome = AgentOutcome.of(Message.addressedTo(
                mover,
                ", " + use.agentNameFor(mover) + " allows you to destroy 1 non-fighter ship with a cost of "
                        + MAX_SHIP_COST + " or less in the activated system.",
                destroyButtons(use.user(), mover, use.payload().system())));
        return use.payload().fromMoveOffer() ? outcome.deletingPressedMessage() : outcome;
    }

    static List<Button> destroyButtons(Player holder, Player mover, Tile system) {
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : eligibleShips(mover, system)) {
            buttons.add(Buttons.red(
                    mover.factionButtonChecker()
                            + DESTROY
                            + holder.getFaction()
                            + SEPARATOR
                            + system.getPosition()
                            + SEPARATOR
                            + unitKey.asyncID(),
                    "Destroy 1 " + unitKey.humanReadableName(),
                    unitKey.unitEmoji()));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        return buttons;
    }

    @ButtonHandler(DESTROY)
    static void destroyShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(DESTROY.length()).split("\\|", 3);
        Player holder = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[1]) : null;
        UnitHolder space = tile == null ? null : tile.getSpaceUnitHolder();
        UnitKey unitKey = space == null
                ? null
                : eligibleShips(player, tile).stream()
                        .filter(key -> key.asyncID().equals(payload[2]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), space);
        if (holder == null
                || unit == null
                || !holder.hasLeader(ID)
                || !tile.getPosition().equals(game.getActiveSystem())) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int production = Math.round(unit.getCost()) + PRODUCTION_BONUS;
        DestroyUnitService.destroyUnit(event, tile, game, unitKey, 1, space, false);
        game.setStoredValue(PRODUCTION + player.getFaction(), Integer.toString(production));
        game.setStoredValue(POSITION + player.getFaction(), tile.getPosition());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " destroyed 1 " + unit.getName() + " using Kelron Dross, the Myrr agent. "
                        + tile.getRepresentationForButtons(game, player) + " gained PRODUCTION " + production + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static int production(Game game, Player player, Tile tile) {
        if (game == null
                || player == null
                || tile == null
                || !tile.getPosition().equals(game.getStoredValue(POSITION + player.getFaction()))) {
            return 0;
        }
        String value = game.getStoredValue(PRODUCTION + player.getFaction());
        try {
            return value.isEmpty() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static void clearProduction(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(PRODUCTION + player.getFaction());
            game.removeStoredValue(POSITION + player.getFaction());
        }
    }

    static List<UnitKey> eligibleShips(Player player, Tile tile) {
        UnitHolder space = tile.getSpaceUnitHolder();
        return space.getUnitKeysForPlayer(player).stream()
                .filter(key -> space.getUnitCount(key) > 0)
                .filter(key -> key.unitType() != UnitType.Fighter)
                .filter(key -> {
                    UnitModel unit = player.getPriorityUnitByAsyncID(key.asyncID(), space);
                    return unit != null && unit.getIsShip() && unit.getCost() <= MAX_SHIP_COST;
                })
                .toList();
    }
}
