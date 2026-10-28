package ti4.service.leader.agent.modules.homebrew.theodisi;

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
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.unit.AddUnitService;

public final class ThronesAgent extends AgentModule<ThronesAgent.Placement> {

    static final String ID = "thronesagent";
    private static final String SELECT_TARGET = "thronesAgentSelectTarget";
    private static final String USE_ON_TARGET = "thronesAgentUseOn_";
    private static final String CHOOSE_SHIP = "thronesAgentChooseShip_";
    private static final String PLACE_SHIP = "thronesAgentPlaceShip_";
    private static final String SEPARATOR = "~";
    private static final int MAX_UNIT_COST = 4;
    private static final String NO_LONGER_AVAILABLE = "That **Malrik the Defiler** choice is no longer available.";

    public record Placement(Player target, UnitModel ship, Tile destination) {}

    public ThronesAgent() {
        super(ID, "Malrik the Defiler, the Thrones");
    }

    public static Button button(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + SELECT_TARGET,
                useLabel(player, ID, "Malrik the Defiler"),
                FactionEmojis.thrones);
    }

    @ButtonHandler(SELECT_TARGET)
    static void selectTarget(ButtonInteractionEvent event, Game game, Player agentOwner) {
        if (!agentOwner.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Malrik the Defiler** is no longer available.");
            return;
        }
        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> canBeTargeted(game, target))
                .map(target -> Buttons.gray(
                        agentOwner.factionButtonChecker() + USE_ON_TARGET + target.getFaction(),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No player currently has both a ship choice and an eligible space-dock system.");
            return;
        }
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event,
                agentOwner.getRepresentationUnfogged()
                        + ", please choose the player whose destroyed ship Malrik the Defiler, the Thrones agent, should place.",
                buttons);
    }

    @ButtonHandler(USE_ON_TARGET)
    static void useOnTarget(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        if (!agentOwner.hasUnexhaustedLeader(ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Malrik the Defiler** is no longer available.");
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(USE_ON_TARGET.length()));
        if (target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find that player.");
            return;
        }
        if (!canBeTargeted(game, target)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That player no longer has an eligible ship choice and space-dock system.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + ", please choose which destroyed unit Malrik the Defiler, the Thrones agent, should place.",
                shipButtons(agentOwner, target));
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Sent ship-choice buttons to " + target.getRepresentationUnfoggedNoPing() + ".");
    }

    static List<Button> shipButtons(Player agentOwner, Player target) {
        return eligibleShips(target).stream()
                .map(ship -> Buttons.gray(
                        target.factionButtonChecker()
                                + CHOOSE_SHIP
                                + agentOwner.getFaction()
                                + SEPARATOR
                                + ship.getAsyncId(),
                        "Place " + ship.getName(),
                        ship.getUnitEmoji()))
                .toList();
    }

    @ButtonHandler(CHOOSE_SHIP)
    static void chooseShip(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] parts = buttonID.substring(CHOOSE_SHIP.length()).split(SEPARATOR, 2);
        Player agentOwner = parts.length == 2 ? game.getPlayerFromColorOrFaction(parts[0]) : null;
        UnitModel ship = parts.length == 2 ? target.getUnitFromAsyncID(parts[1]) : null;
        if (agentOwner == null
                || !agentOwner.hasUnexhaustedLeader(ID)
                || !isEligibleShip(ship)
                || eligibleDestinations(game, target).isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, NO_LONGER_AVAILABLE);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                destinationPrompt(target, ship),
                NewStuffHelper.buttonPagination(
                        destinationButtons(game, target, agentOwner, ship), placePrefix(target, agentOwner, ship), 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_SHIP)
    static void placeShip(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] parts = buttonID.substring(PLACE_SHIP.length()).split(SEPARATOR, 3);
        Player agentOwner = parts.length == 3 ? game.getPlayerFromColorOrFaction(parts[0]) : null;
        UnitModel ship = parts.length == 3 ? target.getUnitFromAsyncID(parts[1]) : null;
        if (agentOwner == null || !agentOwner.hasUnexhaustedLeader(ID) || !isEligibleShip(ship)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, NO_LONGER_AVAILABLE);
            return;
        }
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                destinationButtons(game, target, agentOwner, ship),
                destinationPrompt(target, ship),
                placePrefix(target, agentOwner, ship),
                buttonID)) {
            return;
        }
        String payload = String.join(SEPARATOR, target.getFaction(), ship.getAsyncId(), parts[2]);
        if (AgentLifecycle.use(game, agentOwner, ID, payload, event)) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @Override
    public Optional<Placement> decode(Game game, Player user, String payload) {
        String[] parts = StringUtils.splitByWholeSeparator(payload, SEPARATOR);
        if (parts == null || parts.length != 3) {
            return Optional.empty();
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        Tile destination = game.getTileByPosition(parts[2]);
        UnitModel ship = target == null ? null : target.getUnitFromAsyncID(parts[1]);
        if (ship == null || destination == null) {
            return Optional.empty();
        }
        return Optional.of(new Placement(target, ship, destination));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Placement placement) {
        if (!isEligibleShip(placement.ship())) {
            return Optional.of("Malrik the Defiler can only place a non-structure unit that costs 4 or less");
        }
        boolean eligible = eligibleDestinations(game, placement.target()).stream()
                .anyMatch(tile ->
                        tile.getPosition().equals(placement.destination().getPosition()));
        if (!eligible) {
            return Optional.of("That space-dock system is no longer eligible");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Placement> use) {
        Game game = use.game();
        Player owner = use.user();
        Placement placement = use.payload();
        Player target = placement.target();
        AddUnitService.addUnits(
                use.event(),
                placement.destination(),
                game,
                target.getColor(),
                "1 " + placement.ship().getAsyncId());
        String message = owner.getRepresentation() + " exhausted " + use.agentNameFor(target) + " to place "
                + target.getRepresentation() + "'s " + placement.ship().getName() + " in "
                + placement.destination().getRepresentationForButtons(game, target) + ".";
        return AgentOutcome.notifyInFog(game, target, message, owner);
    }

    private static boolean canBeTargeted(Game game, Player target) {
        return !eligibleShips(target).isEmpty()
                && !eligibleDestinations(game, target).isEmpty();
    }

    private static String destinationPrompt(Player target, UnitModel ship) {
        return target.getRepresentationUnfogged() + ", please choose the space dock system in which to place your "
                + ship.getName() + ".";
    }

    private static String placePrefix(Player target, Player agentOwner, UnitModel ship) {
        return target.factionButtonChecker()
                + PLACE_SHIP
                + agentOwner.getFaction()
                + SEPARATOR
                + ship.getAsyncId()
                + SEPARATOR;
    }

    static List<UnitModel> eligibleShips(Player player) {
        return player.getUnitModels().stream()
                .filter(ThronesAgent::isEligibleShip)
                .sorted(Comparator.comparing(UnitModel::getName))
                .toList();
    }

    private static boolean isEligibleShip(UnitModel unitModel) {
        return unitModel != null && !unitModel.getIsStructure() && unitModel.getCost() <= MAX_UNIT_COST;
    }

    static List<Tile> eligibleDestinations(Game game, Player target) {
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, target, UnitType.Spacedock)) {
            boolean otherPlayerHasUnits = game.getPlayers().values().stream()
                    .anyMatch(other -> other != target && FoWHelper.playerHasUnitsInSystem(other, tile));
            if (!otherPlayerHasUnits) {
                tiles.add(tile);
            }
        }
        return tiles;
    }

    private static List<Button> destinationButtons(Game game, Player target, Player agentOwner, UnitModel ship) {
        return eligibleDestinations(game, target).stream()
                .map(tile -> Buttons.gray(
                        placePrefix(target, agentOwner, ship) + tile.getPosition(),
                        "Place " + ship.getName() + " in " + tile.getRepresentationForButtons(game, target)))
                .toList();
    }
}
