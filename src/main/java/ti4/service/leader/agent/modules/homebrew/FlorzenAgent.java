package ti4.service.leader.agent.modules.homebrew;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class FlorzenAgent extends TargetedAgent {

    static final String ID = "florzenagent";
    private static final String ATTACHMENT_STEP = "florzenAgentStep2_";
    private static final String MOVE_STEP = "flzMove_";

    public FlorzenAgent() {
        super(ID, "Sal Gavda, the Florzen");
    }

    public static Optional<Button> button(Game game, Player florzen) {
        if (movableAttachments(game, florzen).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Buttons.green(
                ownedButtonId(florzen, ID, florzen.getFaction()),
                useLabel(florzen, ID, "Florzen Agent"),
                FactionEmojis.florzen));
    }

    public static boolean moveAttachment(Game game, String source, String destination, String attachment) {
        String token = "attachment_" + attachment.replace(";", "_") + ".png";
        UnitHolder from = ButtonHelper.getUnitHolderFromPlanetName(source, game);
        UnitHolder to = ButtonHelper.getUnitHolderFromPlanetName(destination, game);
        if (from == null || to == null || !from.getTokenList().contains(token)) {
            return false;
        }
        from.removeToken(token);
        to.addToken(token);
        return true;
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (movableAttachments(game, target).isEmpty()) {
            return Optional.of(target.getFactionEmojiOrColor() + " has no attachment that can be moved");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        List<Button> attachments = movableAttachments(use.game(), target).stream()
                .map(attachment -> Buttons.green(
                        ownedStepId(target, ATTACHMENT_STEP + attachment, use.viaYssaril()),
                        StringUtils.substringAfter(attachment, "_") + " on "
                                + Helper.getPlanetRepresentation(
                                        StringUtils.substringBefore(attachment, "_"), use.game())))
                .toList();
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", please choose the attachment you wish to move with " + use.agentNameFor(target) + ".",
                attachments));
    }

    @ButtonHandler(ATTACHMENT_STEP)
    static void chooseDestination(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AgentStepIds.Parsed step = AgentStepIds.parse(buttonID);
        String planetAndAttachment = StringUtils.substringAfter(step.id(), ATTACHMENT_STEP);
        String planet = StringUtils.substringBefore(planetAndAttachment, "_");
        String attachment = StringUtils.substringAfter(planetAndAttachment, "_");
        List<Button> buttons = destinations(game, player, planet).stream()
                .map(destination -> Buttons.green(
                        ownedStepId(
                                player, MOVE_STEP + planet + "_" + destination + "_" + attachment, step.viaYssaril()),
                        Helper.getPlanetRepresentation(destination, game)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", please choose the adjacent planet you wish to put the attachment on.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(MOVE_STEP)
    static void move(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AgentStepIds.Parsed step = AgentStepIds.parse(buttonID);
        String sourceDestinationAndAttachment = StringUtils.substringAfter(step.id(), MOVE_STEP);
        String source = StringUtils.substringBefore(sourceDestinationAndAttachment, "_");
        String destinationAndAttachment = StringUtils.substringAfter(sourceDestinationAndAttachment, "_");
        String destination = StringUtils.substringBefore(destinationAndAttachment, "_");
        String attachment = StringUtils.substringAfter(destinationAndAttachment, "_");
        if (!moveAttachment(game, source, destination, attachment)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That attachment is no longer there, please resolve manually.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmojiOrColor() + " moved " + attachment.replace(";", "_") + " from "
                        + Helper.getPlanetRepresentation(source, game) + " to "
                        + Helper.getPlanetRepresentation(destination, game) + " with "
                        + AgentLifecycle.stepAgentName(game, player, ID, step.viaYssaril()) + ".");
        ButtonHelper.deleteMessage(event);
    }

    static List<String> movableAttachments(Game game, Player player) {
        List<String> attachments = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            if (planet.contains("custodia") || planet.contains("ghoti")) {
                continue;
            }
            UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
            Tile tile = game.getTileFromPlanet(planet);
            if (unitHolder == null
                    || tile == null
                    || tile.isHomeSystem(game)
                    || destinations(game, player, planet).isEmpty()) {
                continue;
            }
            for (String token : unitHolder.getTokenList()) {
                if (!token.contains("attachment") || token.contains("sleeper") || token.contains("control")) {
                    continue;
                }
                String attachment = unitHolder.getName() + "_"
                        + token.replace(".png", "").replace("attachment_", "").replace("_", ";");
                if (!attachments.contains(attachment)) {
                    attachments.add(attachment);
                }
            }
        }
        return attachments;
    }

    static List<String> destinations(Game game, Player player, String originPlanet) {
        Tile origin = game.getTileFromPlanet(originPlanet);
        if (origin == null) {
            return List.of();
        }
        List<String> planets = new ArrayList<>();
        List<String> positions =
                new ArrayList<>(FoWHelper.getAdjacentTilesAndNotThisTile(game, origin.getPosition(), player, false));
        positions.add(origin.getPosition());
        for (String position : positions) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null || tile.isHomeSystem(game) || tile.isMecatol(game)) {
                continue;
            }
            for (UnitHolder planet : tile.getPlanetUnitHolders()) {
                String name = planet.getName();
                if (player.getPlanets().contains(name) && !name.equals(originPlanet)) {
                    planets.add(name);
                }
            }
        }
        return planets;
    }
}
