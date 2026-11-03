package ti4.service.leader.agent.modules.homebrew.beans;

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
import ti4.helpers.ButtonHelper;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.unit.AddUnitService;

public final class CrystellumAgent extends AgentModule<CrystellumAgent.Placement> {

    static final String ID = "crystellumagent";
    private static final String USE_ON_SELF = "useCrystellumAgent_";
    private static final String SELECT_OTHER_TARGET = "selectOtherCrystellumAgentTarget";
    private static final String SELECT_TARGET = "selectCrystellumAgentTarget_";
    private static final String PLACE_FIGHTERS = "placeCrystellumAgentFighters_";
    private static final String SEPARATOR = "|";

    public record Placement(Player target, Tile system, int amount) {}

    public CrystellumAgent() {
        super(ID, "Shardwright Veyla, the Crystellum");
    }

    public static void addEndTurnButton(List<Button> buttons, Game game, Player player) {
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (!player.hasUnexhaustedLeader(ID)
                || activeSystem == null
                || ButtonHelper.checkNumberNonFighterShips(player, activeSystem, false) < 1) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_ON_SELF + player.getFaction(),
                useLabel(player, ID, "Crystellum Agent"),
                FactionEmojis.crystellum));
    }

    public static void addCardsInfoButton(List<Button> buttons, Player player) {
        if (player.hasUnexhaustedLeader(ID)) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + SELECT_OTHER_TARGET,
                    useLabel(player, ID, "Crystellum Agent"),
                    FactionEmojis.crystellum));
        }
    }

    @ButtonHandler(USE_ON_SELF)
    static void useOnSelf(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace(USE_ON_SELF, ""));
        if (target == null || !target.getFaction().equals(player.getFaction())) {
            return;
        }
        sendFighterPlacementButtons(event, game, player, target);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_OTHER_TARGET)
    static void offerOtherTargets(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnexhaustedLeader(ID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_TARGET + target.getFaction(),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the player on whom you wish to use Shardwright Veyla, the Crystellum agent.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_TARGET)
    static void selectTarget(ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace(SELECT_TARGET, ""));
        if (target == null || target.getFaction().equals(agentOwner.getFaction())) {
            return;
        }
        sendFighterPlacementButtons(event, game, agentOwner, target);
        ButtonHelper.deleteMessage(event);
    }

    private static void sendFighterPlacementButtons(
            ButtonInteractionEvent event, Game game, Player agentOwner, Player target) {
        if (!agentOwner.hasUnexhaustedLeader(ID)) {
            return;
        }
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There is no active system for Shardwright Veyla.");
            return;
        }
        int nonFighterShips = ButtonHelper.checkNumberNonFighterShips(target, activeSystem, false);
        if (nonFighterShips < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    target.getRepresentationUnfoggedNoPing()
                            + " has no non-fighter ships in the active system for Shardwright Veyla.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + ", choose how many fighters to place in "
                        + activeSystem.getRepresentationForButtons(game, target)
                        + " with Shardwright Veyla, the Crystellum agent.",
                placementButtons(agentOwner, target, activeSystem, nonFighterShips));
    }

    static List<Button> placementButtons(Player agentOwner, Player target, Tile activeSystem, int nonFighterShips) {
        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= nonFighterShips; amount++) {
            buttons.add(Buttons.green(
                    target.factionButtonChecker()
                            + PLACE_FIGHTERS
                            + String.join(
                                    SEPARATOR,
                                    agentOwner.getFaction(),
                                    target.getFaction(),
                                    activeSystem.getPosition(),
                                    String.valueOf(amount)),
                    "Place " + StringHelper.pluralize(amount, "Fighter")));
        }
        buttons.add(Buttons.red(target.factionButtonChecker() + "deleteButtons", "Decline"));
        return buttons;
    }

    @ButtonHandler(PLACE_FIGHTERS)
    static void placeFighters(ButtonInteractionEvent event, Game game, Player presser, String buttonID) {
        String[] parts = buttonID.replace(PLACE_FIGHTERS, "").split("\\|", 2);
        Player agentOwner = parts.length == 2 ? game.getPlayerFromColorOrFaction(parts[0]) : null;
        if (agentOwner == null || !parts[1].startsWith(presser.getFaction() + SEPARATOR)) {
            return;
        }
        if (AgentLifecycle.use(game, agentOwner, ID, parts[1], event)) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @Override
    public Optional<Placement> decode(Game game, Player user, String payload) {
        String[] parts = StringUtils.split(payload, SEPARATOR);
        if (parts == null || parts.length != 3 || !StringUtils.isNumeric(parts[2])) {
            return Optional.empty();
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        Tile system = game.getTileByPosition(parts[1]);
        if (target == null || system == null) {
            return Optional.empty();
        }
        return Optional.of(new Placement(target, system, Integer.parseInt(parts[2])));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Placement placement) {
        int nonFighterShips = ButtonHelper.checkNumberNonFighterShips(placement.target(), placement.system(), false);
        if (placement.amount() < 1 || placement.amount() > nonFighterShips) {
            return Optional.of(placement.target().getRepresentationNoPing() + " cannot place that many fighters");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Placement> use) {
        Game game = use.game();
        Player owner = use.user();
        Placement placement = use.payload();
        Player target = placement.target();
        int amount = placement.amount();
        String fighters = StringHelper.pluralize(amount, "fighter");
        AddUnitService.addUnits(use.event(), placement.system(), game, target.getColor(), amount + " fighter");
        String message = owner.getRepresentation() + " exhausted " + use.agentNameFor(target) + ", allowing "
                + target.getRepresentation() + " to place " + fighters + " in "
                + placement.system().getRepresentationForButtons(game, target) + ".";
        if (target != owner) {
            AddUnitService.addUnits(use.event(), owner.getNomboxTile(), game, owner.getColor(), amount + " fighter");
            message += " " + owner.getRepresentation() + " captured " + fighters + " from the supply.";
        }
        return AgentOutcome.notifyInFog(game, target, message, owner);
    }
}
