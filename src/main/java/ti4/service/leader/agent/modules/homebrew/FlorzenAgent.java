package ti4.service.leader.agent.modules.homebrew;

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
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class FlorzenAgent extends TargetedAgent {

    static final String ID = "florzenagent";
    private static final String ATTACHMENT_STEP = "florzenAgentStep2_";
    private static final String MOVE_STEP = "florzenAgentStep3_";

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

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (movableAttachments(game, target).isEmpty()) {
            return Optional.of(target.getFactionEmojiOrColor() + " has no attachment to move");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        List<Button> attachments = movableAttachments(use.game(), target).stream()
                .map(attachment -> Buttons.green(
                        target.factionButtonChecker() + ATTACHMENT_STEP + attachment,
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
        String planetAndAttachment = StringUtils.substringAfter(buttonID, ATTACHMENT_STEP);
        String planet = StringUtils.substringBefore(planetAndAttachment, "_");
        String attachment = StringUtils.substringAfter(planetAndAttachment, "_");
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that planet.");
            return;
        }
        List<Button> buttons = destinations(game, player, tile, planet).stream()
                .map(destination -> Buttons.green(
                        player.factionButtonChecker() + MOVE_STEP + planet + "_" + destination + "_" + attachment,
                        Helper.getPlanetRepresentation(destination, game)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", please choose the adjacent planet you wish to put the attachment on.",
                buttons);
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
            if (unitHolder == null || tile == null || tile.isHomeSystem(game)) {
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

    private static List<String> destinations(Game game, Player player, Tile origin, String originPlanet) {
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
