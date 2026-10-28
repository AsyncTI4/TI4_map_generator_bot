package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thurviali.ThurvialiAbilityHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.fow.PlanetTargetService;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.unit.MoveUnitService;

public final class HopeOfTheCosmosAgent extends AgentModule<HopeOfTheCosmosAgent.Pair> {

    static final String ID = "thurvialiagent";
    private static final String USE_ON_SELF = "useThurvialiAgentOnSelf";
    private static final String USE_ON_OTHER = "useThurvialiAgentOnOther";
    private static final String SELECT_TARGET = "selectThurvialiAgentTarget_";
    private static final String SELECT_SOURCE = "selectThurvialiAgentSource_";
    private static final String SELECT_DESTINATION = "selectThurvialiAgentDestination_";
    private static final String SELECTION_MODE = "thurvialiHopeSelectionMode_";
    private static final String FIRST_TARGET = "thurvialiHopeFirstTarget_";
    private static final String OWNER_MODE = "owner";
    private static final String OTHER_MODE = "other";
    private static final String SEPARATOR = "|";
    private static final String COEXIST_FLAG = "coexistFlag";

    public record Pair(Player firstMover, Player secondMover) {}

    public HopeOfTheCosmosAgent() {
        super(ID, "The Hope of the Cosmos, the Thurviali");
    }

    public static Button startTurnButton(Game game, Player player) {
        if (!player.hasUnexhaustedLeader(ID)
                || game.getRealPlayersExcludingThis(player).stream()
                        .noneMatch(target -> canUseBetween(game, player, target))) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + USE_ON_SELF,
                useLabel(player, ID, "The Hope of the Cosmos"),
                FactionEmojis.thurviali);
    }

    public static Button cardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_ON_OTHER,
                useLabel(player, ID, "Thurviali Agent", " on Two Players"),
                FactionEmojis.thurviali);
    }

    @ButtonHandler(USE_ON_SELF)
    static void useOnSelf(ButtonInteractionEvent event, Game game, Player player) {
        game.setStoredValue(SELECTION_MODE + player.getFaction(), OWNER_MODE);
        sendTargetButtons(event, game, player, null);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(USE_ON_OTHER)
    static void useOnOther(ButtonInteractionEvent event, Game game, Player player) {
        game.setStoredValue(SELECTION_MODE + player.getFaction(), OTHER_MODE);
        game.removeStoredValue(FIRST_TARGET + player.getFaction());
        sendTargetButtons(event, game, player, null);
        ButtonHelper.deleteTheOneButton(event);
    }

    private static void sendTargetButtons(
            ButtonInteractionEvent event, Game game, Player agentOwner, Player firstTarget) {
        if (!agentOwner.hasUnexhaustedLeader(ID)) {
            return;
        }
        List<Button> buttons = targetButtons(game, agentOwner, firstTarget);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No eligible pair of players can use **The Hope of the Cosmos**.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                agentOwner.getCorrectChannel(),
                agentOwner.getRepresentationNoPing()
                        + (firstTarget == null
                                ? ", choose the first player for **The Hope of the Cosmos**."
                                : ", choose the second player for **The Hope of the Cosmos**."),
                buttons);
    }

    static List<Button> targetButtons(Game game, Player agentOwner, Player firstTarget) {
        boolean ownerParticipates = OWNER_MODE.equals(game.getStoredValue(SELECTION_MODE + agentOwner.getFaction()));
        Player firstMover = ownerParticipates ? agentOwner : firstTarget;
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target == firstMover || !isEligibleTarget(game, firstMover, target)) {
                continue;
            }
            String id = firstMover == null
                    ? agentOwner.factionButtonChecker() + SELECT_TARGET + target.getFaction()
                    : ownedButtonId(agentOwner, ID, firstMover.getFaction() + SEPARATOR + target.getFaction());
            buttons.add(Buttons.gray(id, "Choose " + target.getColor(), target.getFactionEmojiOrColor()));
        }
        return buttons;
    }

    private static boolean isEligibleTarget(Game game, Player firstMover, Player target) {
        if (firstMover != null) {
            return canUseBetween(game, firstMover, target);
        }
        return game.getRealPlayers().stream().anyMatch(other -> other != target && canUseBetween(game, target, other));
    }

    @ButtonHandler(SELECT_TARGET)
    static void selectTarget(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_TARGET.length()));
        String mode = game.getStoredValue(SELECTION_MODE + agentOwner.getFaction());
        Player firstTarget =
                game.getPlayerFromColorOrFaction(game.getStoredValue(FIRST_TARGET + agentOwner.getFaction()));
        if (target == null
                || (OWNER_MODE.equals(mode) && target == agentOwner)
                || !agentOwner.hasUnexhaustedLeader(ID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (OTHER_MODE.equals(mode) && firstTarget == null) {
            game.setStoredValue(FIRST_TARGET + agentOwner.getFaction(), target.getFaction());
            sendTargetButtons(event, game, agentOwner, target);
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player firstMover = OWNER_MODE.equals(mode) ? agentOwner : firstTarget;
        if (firstMover == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AgentLifecycle.pressLegacyButton(
                event, game, agentOwner, ID, firstMover.getFaction() + SEPARATOR + target.getFaction());
    }

    @Override
    public Optional<Pair> decode(Game game, Player user, String payload) {
        Player firstMover = game.getPlayerFromColorOrFaction(StringUtils.substringBefore(payload, SEPARATOR));
        Player secondMover = game.getPlayerFromColorOrFaction(StringUtils.substringAfter(payload, SEPARATOR));
        if (firstMover == null || secondMover == null) {
            return Optional.empty();
        }
        return Optional.of(new Pair(firstMover, secondMover));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Pair pair) {
        if (pair.firstMover() == pair.secondMover()) {
            return Optional.of("The Hope of the Cosmos needs two different players");
        }
        if (!canUseBetween(game, pair.firstMover(), pair.secondMover())) {
            return Optional.of("Those players cannot move infantry into coexistence on each other's planets");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Pair> use) {
        Game game = use.game();
        Player owner = use.user();
        game.removeStoredValue(SELECTION_MODE + owner.getFaction());
        game.removeStoredValue(FIRST_TARGET + owner.getFaction());
        Player first = use.payload().firstMover();
        Player second = use.payload().secondMover();
        return AgentOutcome.of(moveOffer(use, first, second), moveOffer(use, second, first))
                .deletingPressedMessage();
    }

    private static Message moveOffer(AgentUse<Pair> use, Player mover, Player destinationOwner) {
        return Message.plain(
                mover,
                mover.getRepresentation() + ", " + use.user().getRepresentationNoPing() + " exhausted "
                        + use.agentNameFor(mover)
                        + ". Choose 1 infantry to move into coexistence on a planet controlled by "
                        + destinationOwner.getRepresentationNoPing() + ".",
                sourceButtons(use.game(), mover, destinationOwner));
    }

    @ButtonHandler(SELECT_SOURCE)
    static void selectSource(ButtonInteractionEvent event, Game game, Player mover, String buttonID) {
        String[] payload = buttonID.substring(SELECT_SOURCE.length()).split("\\|", 2);
        Player destinationOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Planet source = payload.length == 2 ? game.getUnitHolderFromPlanet(payload[1]) : null;
        if (destinationOwner == null
                || source == null
                || !mover.getPlanets().contains(source.getName())
                || source.getUnitCount(UnitType.Infantry, mover) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> destinationButtons = destinationButtons(game, destinationOwner, mover, source);
        if (destinationButtons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                mover.getRepresentationNoPing()
                        + ", choose a non-home planet controlled by "
                        + destinationOwner.getRepresentationNoPing()
                        + " that contains their ground forces.",
                destinationButtons);
    }

    @ButtonHandler(SELECT_DESTINATION)
    static void selectDestination(ButtonInteractionEvent event, Game game, Player mover, String buttonID) {
        String[] payload = buttonID.substring(SELECT_DESTINATION.length()).split("\\|", 3);
        Player destinationOwner = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Planet source = payload.length == 3 ? game.getUnitHolderFromPlanet(payload[1]) : null;
        Planet destination = payload.length == 3 ? game.getUnitHolderFromPlanet(payload[2]) : null;
        Tile sourceTile = source == null ? null : game.getTileFromPlanet(source.getName());
        Tile destinationTile = destination == null ? null : game.getTileFromPlanet(destination.getName());
        if (destinationOwner == null
                || source == null
                || destination == null
                || sourceTile == null
                || destinationTile == null
                || !mover.getPlanets().contains(source.getName())
                || source.getUnitCount(UnitType.Infantry, mover) < 1
                || !destinationOwner.getPlanets().contains(destination.getName())
                || destinationTile.isHomeSystem(game)
                || !destination.hasGroundForces(destinationOwner)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        moveIntoCoexistence(event, game, mover, source, sourceTile, destination, destinationTile);
        ThurvialiAbilityHandler.checkRadiantGrafting(game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                mover.getRepresentationNoPing()
                        + " moved 1 infantry from "
                        + Helper.getPlanetRepresentation(source.getName(), game)
                        + " into coexistence on "
                        + Helper.getPlanetRepresentation(destination.getName(), game)
                        + " with **The Hope of the Cosmos**.");
        ButtonHelper.deleteMessage(event);
    }

    private static void moveIntoCoexistence(
            ButtonInteractionEvent event,
            Game game,
            Player mover,
            Planet source,
            Tile sourceTile,
            Planet destination,
            Tile destinationTile) {
        String coexistenceFlag = game.getStoredValue(COEXIST_FLAG);
        game.setStoredValue(COEXIST_FLAG, "yes");
        try {
            MoveUnitService.moveUnits(
                    event,
                    sourceTile,
                    game,
                    mover.getColor(),
                    "1 gf " + source.getName(),
                    destinationTile,
                    destination.getName());
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue(COEXIST_FLAG);
            } else {
                game.setStoredValue(COEXIST_FLAG, coexistenceFlag);
            }
        }
    }

    static List<Button> sourceButtons(Game game, Player mover, Player destinationOwner) {
        List<String> planetNames = mover.getPlanets().stream()
                .filter(planetName -> {
                    Planet planet = game.getUnitHolderFromPlanet(planetName);
                    return planet != null && planet.getUnitCount(UnitType.Infantry, mover) >= 1;
                })
                .sorted()
                .toList();
        List<Button> buttons = new ArrayList<>();
        for (String planetName : planetNames) {
            buttons.add(Buttons.green(
                    mover.factionButtonChecker()
                            + SELECT_SOURCE
                            + destinationOwner.getFaction()
                            + SEPARATOR
                            + planetName,
                    "Move Infantry from " + Helper.getPlanetRepresentation(planetName, game),
                    FactionEmojis.thurviali));
        }
        return buttons;
    }

    private static List<Button> destinationButtons(Game game, Player destinationOwner, Player mover, Planet source) {
        List<String> candidatePlanets = PlanetTargetService.knownPlanetIds(game, mover, Set.of()).stream()
                .filter(destinationOwner.getPlanets()::contains)
                .sorted()
                .toList();
        List<Button> buttons = new ArrayList<>();
        for (String planetName : candidatePlanets) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            Tile tile = planet == null ? null : game.getTileFromPlanet(planetName);
            if (planet == null
                    || tile == null
                    || tile.isHomeSystem(game)
                    || !planet.hasGroundForces(destinationOwner)) {
                continue;
            }
            buttons.add(Buttons.green(
                    mover.factionButtonChecker()
                            + SELECT_DESTINATION
                            + destinationOwner.getFaction()
                            + SEPARATOR
                            + source.getName()
                            + SEPARATOR
                            + planetName,
                    "Move to " + Helper.getPlanetRepresentation(planetName, game),
                    FactionEmojis.thurviali));
        }
        return buttons;
    }

    static boolean canUseBetween(Game game, Player firstPlayer, Player secondPlayer) {
        return hasInfantry(game, firstPlayer)
                && hasInfantry(game, secondPlayer)
                && hasDestination(game, firstPlayer)
                && hasDestination(game, secondPlayer);
    }

    private static boolean hasDestination(Game game, Player player) {
        return player.getPlanets().stream().map(game::getUnitHolderFromPlanet).anyMatch(planet -> {
            Tile tile = planet == null ? null : game.getTileFromPlanet(planet.getName());
            return tile != null && !tile.isHomeSystem(game) && planet.hasGroundForces(player);
        });
    }

    private static boolean hasInfantry(Game game, Player player) {
        return player.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .anyMatch(planet -> planet != null && planet.getUnitCount(UnitType.Infantry, player) > 0);
    }
}
