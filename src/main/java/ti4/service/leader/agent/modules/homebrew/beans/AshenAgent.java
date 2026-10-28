package ti4.service.leader.agent.modules.homebrew.beans;

import java.util.ArrayList;
import java.util.Comparator;
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
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.unit.AddUnitService;

public final class AshenAgent extends AgentModule<AshenAgent.Placement> {

    static final String ID = "ashenagent";
    private static final String SELECT_TARGET = "ashenAgentSelectTarget";
    private static final String USE_ON_TARGET = "ashenAgentUseOn_";
    private static final String CHOOSE_UNIT = "ashenAgentChooseUnit_";
    private static final String PLACE_DESTINATION = "ashenAgentPlace_";
    private static final String STEP_SEPARATOR = "~";
    private static final String SEPARATOR = "|";
    private static final String NO_LONGER_AVAILABLE = "**Orrun** is no longer available.";

    public record Placement(Player target, UnitModel unit, Tile destination) {}

    public AshenAgent() {
        super(ID, "Orrun, the Ashen");
    }

    public static Button cardsInfoButton(Player holder) {
        return Buttons.gray(
                holder.factionButtonChecker() + SELECT_TARGET, useLabel(holder, ID, "Orrun"), FactionEmojis.ashen);
    }

    @ButtonHandler(SELECT_TARGET)
    static void offerTargets(ButtonInteractionEvent event, Game game, Player holder) {
        if (!holder.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, NO_LONGER_AVAILABLE);
            return;
        }
        List<Button> buttons = targetButtons(game, holder);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "No player currently has both an eligible **Orrun** unit choice and an eligible destination system.");
            return;
        }
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event, holder.getRepresentationUnfogged() + ", choose the player who may use **Orrun**.", buttons);
    }

    static List<Button> targetButtons(Game game, Player holder) {
        return game.getRealPlayers().stream()
                .filter(target -> !eligibleUnitModels(target).isEmpty())
                .filter(target -> !eligibleDestinations(game, target).isEmpty())
                .map(target -> Buttons.gray(
                        holder.factionButtonChecker()
                                + USE_ON_TARGET
                                + holder.getFaction()
                                + STEP_SEPARATOR
                                + target.getFaction(),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
    }

    @ButtonHandler(USE_ON_TARGET)
    static void useOnTarget(ButtonInteractionEvent event, Game game, Player holder, String buttonID) {
        if (!holder.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, NO_LONGER_AVAILABLE);
            return;
        }
        String[] parts = buttonID.substring(USE_ON_TARGET.length()).split(STEP_SEPARATOR, 2);
        Player target = parts.length == 2 ? game.getPlayerFromColorOrFaction(parts[1]) : null;
        if (target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find that player.");
            return;
        }
        if (eligibleUnitModels(target).isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    target.getRepresentation(false, false)
                            + " has no eligible non-fighter, non-infantry ship units for **Orrun**.");
            return;
        }
        if (eligibleDestinations(game, target).isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    target.getRepresentation(false, false)
                            + " no longer has any eligible destination systems for **Orrun**.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", choose which eligible unit **Orrun** should place for you.",
                unitButtons(holder, target));
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Sent **Orrun** unit-choice buttons to " + target.getRepresentationUnfoggedNoPing() + ".");
    }

    static List<Button> unitButtons(Player holder, Player target) {
        List<Button> buttons = new ArrayList<>();
        for (UnitModel unitModel : eligibleUnitModels(target)) {
            buttons.add(Buttons.gray(
                    target.factionButtonChecker()
                            + CHOOSE_UNIT
                            + holder.getFaction()
                            + STEP_SEPARATOR
                            + unitModel.getAsyncId(),
                    unitModel.getName(),
                    unitModel.getUnitEmoji()));
        }
        return buttons;
    }

    @ButtonHandler(CHOOSE_UNIT)
    static void chooseUnit(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] parts = buttonID.substring(CHOOSE_UNIT.length()).split(STEP_SEPARATOR, 2);
        if (parts.length != 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve that **Orrun** unit choice.");
            return;
        }
        Player holder = game.getPlayerFromColorOrFaction(parts[0]);
        if (holder == null || !holder.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, NO_LONGER_AVAILABLE);
            return;
        }
        UnitModel selectedUnit = target.getUnitFromAsyncID(parts[1]);
        if (!isEligibleUnitModel(selectedUnit)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That unit is not eligible for **Orrun**.");
            return;
        }
        List<Tile> destinations = eligibleDestinations(game, target);
        if (destinations.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You no longer have any eligible destination systems for **Orrun**.");
            return;
        }
        List<Button> buttons = destinations.stream()
                .map(tile -> Buttons.gray(
                        target.factionButtonChecker()
                                + PLACE_DESTINATION
                                + String.join(
                                        STEP_SEPARATOR,
                                        holder.getFaction(),
                                        selectedUnit.getAsyncId(),
                                        tile.getPosition()),
                        "Place " + selectedUnit.getName() + " in " + tile.getRepresentationForButtons(game, target)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + ", choose which eligible system **Orrun** should place your "
                        + selectedUnit.getName()
                        + " in.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_DESTINATION)
    static void placeUnit(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] parts = buttonID.substring(PLACE_DESTINATION.length()).split(STEP_SEPARATOR, 3);
        if (parts.length != 3) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve that **Orrun** choice.");
            return;
        }
        Player holder = game.getPlayerFromColorOrFaction(parts[0]);
        if (holder == null || !holder.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, NO_LONGER_AVAILABLE);
            return;
        }
        String payload = String.join(SEPARATOR, target.getFaction(), parts[1], parts[2]);
        if (AgentLifecycle.use(game, holder, ID, payload, event)) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @Override
    public Optional<Placement> decode(Game game, Player user, String payload) {
        String[] parts = StringUtils.split(payload, SEPARATOR);
        if (parts == null || parts.length != 3) {
            return Optional.empty();
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        Tile destination = game.getTileByPosition(parts[2]);
        if (target == null || destination == null) {
            return Optional.empty();
        }
        UnitModel unit = target.getUnitFromAsyncID(parts[1]);
        if (unit == null) {
            return Optional.empty();
        }
        return Optional.of(new Placement(target, unit, destination));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Placement placement) {
        if (!isEligibleUnitModel(placement.unit())) {
            return Optional.of("That unit is not eligible for **Orrun**");
        }
        String position = placement.destination().getPosition();
        if (eligibleDestinations(game, placement.target()).stream()
                .noneMatch(tile -> tile.getPosition().equals(position))) {
            return Optional.of("That destination is no longer eligible for **Orrun**");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Placement> use) {
        Game game = use.game();
        Player holder = use.user();
        Placement placement = use.payload();
        Player target = placement.target();
        AddUnitService.addUnits(
                use.event(),
                placement.destination(),
                game,
                target.getColor(),
                "1 " + placement.unit().getAsyncId());
        String message = holder.getRepresentation() + " exhausted " + use.agentNameFor(target) + " to place "
                + target.getRepresentation() + "'s " + placement.unit().getName() + " in "
                + placement.destination().getRepresentationForButtons(game, target) + ".";
        return AgentOutcome.notifyInFog(game, target, message, holder);
    }

    static List<Tile> eligibleDestinations(Game game, Player target) {
        String activeSystem = game.getActiveSystem();
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!tile.getPosition().equalsIgnoreCase(activeSystem)
                    && FoWHelper.playerHasActualShipsInSystem(target, tile)) {
                tiles.add(tile);
            }
        }
        return tiles;
    }

    static List<UnitModel> eligibleUnitModels(Player target) {
        return target.getUnitModels().stream()
                .filter(AshenAgent::isEligibleUnitModel)
                .sorted(Comparator.comparing(UnitModel::getName))
                .toList();
    }

    private static boolean isEligibleUnitModel(UnitModel unitModel) {
        return unitModel != null
                && unitModel.getIsShip()
                && unitModel.getUnitType() != UnitType.Fighter
                && unitModel.getUnitType() != UnitType.Infantry;
    }
}
