package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.xytheris.XytherisLeadersHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class XytherisAgent extends AgentModule<XytherisAgent.Activation> {

    static final String ID = "xytherisagent";
    private static final String LEGACY_USE = "useMyrixAgent";
    private static final String SELECT_SHIP = "myrixAgentShip_";
    private static final String LOCATION_SEPARATOR = ";";
    private static final int BUTTONS_PER_MESSAGE = 25;

    public record Activation(Player activePlayer, boolean fromActivationOffer) {}

    public XytherisAgent() {
        super(ID, "Myrix Nevom, the Xytheris");
    }

    public static void offerAfterActivation(Game game, Player activePlayer) {
        game.setStoredValue(XytherisLeadersHandler.MYRIX_AGENT_WINDOW, activePlayer.getFaction());
        if (!activePlayer.hasUnexhaustedLeader(ID)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                activePlayer.getCorrectChannel(),
                activePlayer.getRepresentationUnfogged() + ", you may exhaust Myrix, the Xytheris agent.",
                List.of(
                        Buttons.green(
                                ownedButtonId(activePlayer, ID, activePlayer.getFaction()),
                                useLabel(activePlayer, ID, "Myrix")),
                        Buttons.red(activePlayer.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    public static Button cardsInfoButton(Player holder) {
        return Buttons.gray(
                ownedButtonId(holder, ID), useLabel(holder, ID, "Myrix", " on Another Player"), FactionEmojis.xytheris);
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_USE)
    static void legacyUse(ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, "");
    }

    @Override
    public Optional<Activation> decode(Game game, Player user, String payload) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.ofNullable(game.getPlayerFromColorOrFaction(
                            game.getStoredValue(XytherisLeadersHandler.MYRIX_AGENT_WINDOW)))
                    .map(activePlayer -> new Activation(activePlayer, false));
        }
        return AgentTargets.player(game, payload).map(activePlayer -> new Activation(activePlayer, true));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Activation activation) {
        Player target = activation.activePlayer();
        if (target != game.getActivePlayer()
                || !target.getFaction().equals(game.getStoredValue(XytherisLeadersHandler.MYRIX_AGENT_WINDOW))) {
            return Optional.of("Myrix can only be used immediately after a player activates a system");
        }
        if (shipButtons(game, target).isEmpty()) {
            return Optional.of(target.getRepresentationNoPing() + " has no ships to select");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Activation> use) {
        Game game = use.game();
        Player target = use.payload().activePlayer();
        game.removeStoredValue(XytherisLeadersHandler.MYRIX_AGENT_WINDOW);
        game.setStoredValue(XytherisLeadersHandler.MYRIX_AGENT_PENDING, target.getFaction());
        List<Button> buttons = shipButtons(game, target);
        String prompt = ", please choose a ship to gain capacity and PRODUCTION from " + use.agentNameFor(target) + ".";
        List<Message> messages = new ArrayList<>();
        for (int index = 0; index < buttons.size(); index += BUTTONS_PER_MESSAGE) {
            messages.add(Message.addressedTo(
                    target, prompt, buttons.subList(index, Math.min(index + BUTTONS_PER_MESSAGE, buttons.size()))));
        }
        AgentOutcome outcome = AgentOutcome.of(messages.toArray(Message[]::new));
        return use.payload().fromActivationOffer() ? outcome.deletingPressedMessage() : outcome;
    }

    static List<Button> shipButtons(Game game, Player target) {
        Map<String, Button> buttonsByUnitLocation = new LinkedHashMap<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : holder.getUnitKeysForPlayer(target)) {
                    UnitModel unit = target.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
                    if (unit == null || !unit.getIsShip()) {
                        continue;
                    }
                    String unitLocation =
                            String.join(LOCATION_SEPARATOR, tile.getPosition(), holder.getName(), unitKey.asyncID());
                    buttonsByUnitLocation.put(
                            unitLocation,
                            Buttons.green(
                                    target.factionButtonChecker() + SELECT_SHIP + unitLocation,
                                    "Choose 1 " + unitKey.humanReadableName() + " in " + tile.getPosition(),
                                    unitKey.unitEmoji()));
                }
            }
        }
        return new ArrayList<>(buttonsByUnitLocation.values());
    }

    @ButtonHandler(SELECT_SHIP)
    static void selectShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game.getActivePlayer() != player
                || !player.getFaction().equals(game.getStoredValue(XytherisLeadersHandler.MYRIX_AGENT_PENDING))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String[] unitLocation = buttonID.substring(SELECT_SHIP.length()).split(LOCATION_SEPARATOR, 3);
        Tile tile = unitLocation.length == 3 ? game.getTileByPosition(unitLocation[0]) : null;
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(unitLocation[1]);
        UnitKey unitKey = holder == null
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(unitLocation[2]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
        if (unit == null || !unit.getIsShip()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int abilityCount = XytherisLeadersHandler.getUnitAbilityCount(unit);
        game.setStoredValue(
                XytherisLeadersHandler.MYRIX_AGENT_UNIT + player.getFaction(),
                String.join(LOCATION_SEPARATOR, unitLocation));
        game.removeStoredValue(XytherisLeadersHandler.MYRIX_AGENT_PENDING);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " selected " + unit.getUnitEmoji() + " " + unit.getName()
                        + " for Myrix, the Xytheris agent (+" + abilityCount
                        + " capacity and PRODUCTION; it will move before identical ships).");
        ButtonHelper.deleteMessage(event);
    }
}
