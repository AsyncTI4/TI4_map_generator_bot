package ti4.discord.interactions.buttons.handlers.actioncards;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.fow.BlindSelectionService;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;

@UtilityClass
public class ActionCardPingButtonHandler {

    private static final String PLANET = "planet";
    private static final String SYSTEM = "system";
    private static final String PLAYER = "player";

    private static final String ROUTE_PUBLIC = "public";
    private static final String ROUTE_LOCAL = "local";

    private static final String PLANET_EMOJI = "🪐";
    private static final String SYSTEM_EMOJI = "🌌";
    private static final String PLAYER_EMOJI = "🎯";

    public static List<Button> pingButtons(Player player) {
        String base = player.factionButtonChecker() + Constants.AC_PING_PICK;
        return new ArrayList<>(List.of(
                Buttons.gray(base + PLANET, "Ping Planet Target", PLANET_EMOJI),
                Buttons.gray(base + SYSTEM, "Ping System Target", SYSTEM_EMOJI),
                Buttons.gray(base + PLAYER, "Ping Player Target", PLAYER_EMOJI)));
    }

    @ButtonHandler(value = Constants.AC_PING_PICK, save = false)
    public static void pickType(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String type = buttonID.substring(Constants.AC_PING_PICK.length());
        switch (type) {
            case PLANET -> offerPlanetChoices(game, player);
            case SYSTEM -> offerSystemChoices(game, player);
            case PLAYER -> offerPlayerChoices(game, player);
            default -> {
                return;
            }
        }
        markActionCardWithPingType(event, type);
    }

    private static void markActionCardWithPingType(ButtonInteractionEvent event, String type) {
        event.getChannel()
                .addReactionById(event.getMessageId(), pingEmoji(type))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static PlanetTargetSpec planetSpec(Player player) {
        return PlanetTargetSpec.of(player.factionButtonChecker() + Constants.AC_PING_PLANET);
    }

    private static void offerPlanetChoices(Game game, Player player) {
        List<Button> buttons = PlanetTargetService.targetButtons(game, player, planetSpec(player), new ArrayList<>());
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the planet you're pinging.",
                buttons);
    }

    @ButtonHandler(value = Constants.AC_PING_PLANET, save = false)
    public static void resolvePlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (PlanetTargetService.handlePlanetPage(event, game, player, buttonID, planetSpec(player))) {
            return;
        }
        String planetId = targetFrom(buttonID);
        if (!planetExists(game, planetId) || !actorCouldKnowPlanet(game, player, planetId)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        offerRouteChoice(player, PLANET, planetId);
    }

    private static void offerSystemChoices(Game game, Player player) {
        String buttonPrefix = player.factionButtonChecker() + Constants.AC_PING_SYSTEM;
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            buttons.add(Buttons.gray(
                    buttonPrefix + "_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
        }
        BlindSelectionService.filterForBlindPositionSelection(game, player, buttons, buttonPrefix);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the system you're pinging.",
                buttons);
    }

    @ButtonHandler(value = Constants.AC_PING_SYSTEM, save = false)
    public static void resolveSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = targetFrom(buttonID);
        if (!actorCanSeeSystem(game, player, position)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        offerRouteChoice(player, SYSTEM, position);
    }

    private static void offerPlayerChoices(Game game, Player player) {
        String buttonPrefix = player.factionButtonChecker() + Constants.AC_PING_PLAYER;
        List<Button> buttons = new ArrayList<>();
        for (Player candidate : game.getRealPlayers()) {
            if (candidate == player) continue;
            buttons.add(FoWHelper.fogSafeTargetButton(buttonPrefix + "_" + candidate.getFaction(), "gray", candidate));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentationUnfogged() + ", there is nobody else to ping.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the player you're pinging.",
                buttons);
    }

    @ButtonHandler(value = Constants.AC_PING_PLAYER, save = false)
    public static void resolvePlayer(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String faction = targetFrom(buttonID);
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target == null || target == player) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        offerRouteChoice(player, PLAYER, faction);
    }

    private static void offerRouteChoice(Player player, String type, String targetKey) {
        String base = player.factionButtonChecker() + Constants.AC_PING_ROUTE;
        List<Button> buttons = new ArrayList<>(List.of(
                Buttons.blue(base + ROUTE_PUBLIC + "_" + type + "_" + targetKey, "Public"),
                Buttons.gray(base + ROUTE_LOCAL + "_" + type + "_" + targetKey, "Local")));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", announce this to the whole table, or keep it to yourself?",
                buttons);
    }

    @ButtonHandler(value = Constants.AC_PING_ROUTE, save = false)
    public static void resolveRoute(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(Constants.AC_PING_ROUTE.length()).split("_", 3);
        if (parts.length < 3) {
            return;
        }
        boolean isPublic = ROUTE_PUBLIC.equals(parts[0]);
        String type = parts[1];
        String targetKey = parts[2];

        String actorLine = FoWHelper.actorOrAnon(game, player, "Someone") + " pinged a target";
        boolean sent =
                switch (type) {
                    case PLANET -> routePlanet(game, player, targetKey, isPublic, actorLine);
                    case SYSTEM -> routeSystem(game, player, targetKey, isPublic, actorLine);
                    case PLAYER -> routePlayer(game, player, targetKey, isPublic, actorLine);
                    default -> false;
                };
        if (sent) {
            ButtonHelper.deleteMessage(event);
        } else {
            PlanetTargetService.fizzle(event, player);
        }
    }

    private static boolean routePlanet(Game game, Player actor, String planetId, boolean isPublic, String actorLine) {
        if (!planetExists(game, planetId)) {
            return false;
        }
        if (isPublic) {
            sendPlanetPingToEachViewerThatKnowsIt(game, planetId, actorLine);
        } else {
            MessageHelper.sendMessageToChannel(
                    actor.getCorrectChannel(), planetPingFor(game, actor, planetId, actorLine));
        }
        return true;
    }

    private static void sendPlanetPingToEachViewerThatKnowsIt(Game game, String planetId, String actorLine) {
        for (Player viewer : game.getRealPlayers()) {
            if (actorCouldKnowPlanet(game, viewer, planetId)) {
                MessageHelper.sendMessageToChannel(
                        viewer.getCorrectChannel(), planetPingFor(game, viewer, planetId, actorLine));
            }
        }
    }

    private static String planetPingFor(Game game, Player viewer, String planetId, String actorLine) {
        String label = PlanetTargetService.fogSafeLabeller(game, viewer).apply(planetId);
        return actorLine + ": " + PLANET_EMOJI + " " + label + ".";
    }

    private static boolean routeSystem(Game game, Player actor, String position, boolean isPublic, String actorLine) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            return false;
        }
        if (isPublic) {
            FoWHelper.pingSystem(game, position, actorLine + ".", false);
        } else {
            MessageHelper.sendMessageToChannel(
                    actor.getCorrectChannel(),
                    actorLine + ": " + SYSTEM_EMOJI + " " + tile.getRepresentationForButtons(game, actor) + ".");
        }
        return true;
    }

    private static boolean routePlayer(Game game, Player actor, String faction, boolean isPublic, String actorLine) {
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target == null) {
            return false;
        }
        String label = target.fogSafeEmoji() + " " + target.getFactionNameOrColor();
        MessageChannel channel = isPublic ? game.getMainGameChannel() : actor.getCorrectChannel();
        MessageHelper.sendMessageToChannel(channel, actorLine + ": " + label + ".");
        return true;
    }

    private static String targetFrom(String buttonID) {
        return StringUtils.substringAfter(buttonID, "_");
    }

    private static boolean planetExists(Game game, String planetId) {
        return ButtonHelper.getUnitHolderFromPlanetName(planetId, game) != null;
    }

    private static boolean actorCouldKnowPlanet(Game game, Player actor, String planetId) {
        return !game.isFowMode()
                || PlanetTargetService.knownPlanetIds(game, actor, null).contains(planetId);
    }

    private static boolean actorCanSeeSystem(Game game, Player actor, String position) {
        if (game.getTileByPosition(position) == null) {
            return false;
        }
        return !game.isFowMode()
                || FoWHelper.getTilePositionsToShow(game, actor).contains(position);
    }

    private static Emoji pingEmoji(String type) {
        return switch (type) {
            case PLANET -> Emoji.fromUnicode(PLANET_EMOJI);
            case SYSTEM -> Emoji.fromUnicode(SYSTEM_EMOJI);
            default -> Emoji.fromUnicode(PLAYER_EMOJI);
        };
    }
}
