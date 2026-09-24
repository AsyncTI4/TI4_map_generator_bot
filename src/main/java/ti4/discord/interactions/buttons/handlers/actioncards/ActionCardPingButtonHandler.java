package ti4.discord.interactions.buttons.handlers.actioncards;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
import ti4.service.fow.GMService;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;

@UtilityClass
public class ActionCardPingButtonHandler {

    private static final String PLANET = "planet";
    private static final String SYSTEM = "system";
    private static final String PLAYER = "player";

    private static final String ROUTE_PUBLIC = "public";
    private static final String ROUTE_NON_PUBLIC = "local";

    private static final String PING_KEY_PREFIX = "acPingCard";
    private static final Pattern LEADING_DIGITS = Pattern.compile("^(\\d+)");

    private static final String PLANET_EMOJI = "🪐";
    private static final String SYSTEM_EMOJI = "🌌";
    private static final String PLAYER_EMOJI = "🎯";

    private static final int MAX_TITLE_LENGTH_IN_BUTTON_ID = 30;

    public static void sendPingPrompt(Player player, String actionCardMessageId, String actionCardTitle) {
        String base = player.factionButtonChecker() + Constants.AC_PING_PICK;
        String titleForButtonId = StringUtils.truncate(actionCardTitle, MAX_TITLE_LENGTH_IN_BUTTON_ID);
        String suffix = "_" + actionCardMessageId + "_" + titleForButtonId;
        List<Button> buttons = new ArrayList<>(List.of(
                Buttons.gray(base + PLANET + suffix, "Ping Planet Target", PLANET_EMOJI),
                Buttons.gray(base + SYSTEM + suffix, "Ping System Target", SYSTEM_EMOJI),
                Buttons.gray(base + PLAYER + suffix, "Ping Player Target", PLAYER_EMOJI),
                Buttons.red("deleteButtons", "No Target")));
        sendPrivately(
                player,
                player.getRepresentationUnfogged() + ", ping a target for _" + actionCardTitle + "_?\n"
                        + "> " + PLANET_EMOJI + " any planet, including your own.\n"
                        + "> " + SYSTEM_EMOJI + " any system.\n"
                        + "> " + PLAYER_EMOJI + " any other player.\n"
                        + "> Anything else: pick _No Target_ and use `/fow announce` instead.",
                buttons);
    }

    @ButtonHandler(Constants.AC_PING_PICK)
    public static void pickType(Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(Constants.AC_PING_PICK.length()).split("_", 3);
        if (payload.length < 3) {
            return;
        }
        String type = payload[0];
        String actionCardMessageId = payload[1];
        String token = flowToken(actionCardMessageId);
        switch (type) {
            case PLANET -> offerPlanetChoices(game, player, token);
            case SYSTEM -> offerSystemChoices(game, player, token);
            case PLAYER -> offerPlayerChoices(game, player, token);
            default -> {
                return;
            }
        }
        forgetAbandonedFlows(game, player);
        game.setStoredValue(pingKey(player, token), game.getRound() + "|" + payload[2]);
        markActionCardWithPingType(game, actionCardMessageId, type);
    }

    private static String flowToken(String actionCardMessageId) {
        return actionCardMessageId.length() <= 8
                ? actionCardMessageId
                : actionCardMessageId.substring(actionCardMessageId.length() - 8);
    }

    private static String pingKey(Player player, String token) {
        return PING_KEY_PREFIX + player.getFaction() + "_" + token;
    }

    private static void forgetAbandonedFlows(Game game, Player player) {
        String ownPrefix = PING_KEY_PREFIX + player.getFaction() + "_";
        List<String> stale = new ArrayList<>();
        for (var entry : game.getStoredValueMap().entrySet()) {
            if (!entry.getKey().startsWith(ownPrefix)) {
                continue;
            }
            String round = StringUtils.substringBefore(entry.getValue(), "|");
            if (StringUtils.isNumeric(round) && Integer.parseInt(round) < game.getRound()) {
                stale.add(entry.getKey());
            }
        }
        stale.forEach(game::removeStoredValue);
    }

    private static String peekCardTitle(Game game, Player player, String token) {
        String stored = game.getStoredValue(pingKey(player, token));
        return stored.isEmpty() ? "" : StringUtils.substringAfter(stored, "|");
    }

    private static String tokenFrom(String payload) {
        Matcher leadingDigits = LEADING_DIGITS.matcher(payload);
        return leadingDigits.find() ? leadingDigits.group(1) : "";
    }

    private static void markActionCardWithPingType(Game game, String actionCardMessageId, String type) {
        MessageChannel channel = game.getMainGameChannel();
        if (channel == null || actionCardMessageId.isEmpty()) {
            return;
        }
        channel.addReactionById(actionCardMessageId, pingEmoji(type)).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static PlanetTargetSpec planetSpec(Player player, String token) {
        return PlanetTargetSpec.of(player.factionButtonChecker() + Constants.AC_PING_PLANET + "_" + token);
    }

    private static void offerPlanetChoices(Game game, Player player, String token) {
        List<Button> buttons =
                PlanetTargetService.targetButtons(game, player, planetSpec(player, token), new ArrayList<>());
        sendPrivately(player, player.getRepresentationUnfogged() + ", choose the planet you're pinging.", buttons);
    }

    @ButtonHandler(Constants.AC_PING_PLANET)
    public static void resolvePlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(Constants.AC_PING_PLANET.length() + 1);
        String token = tokenFrom(payload);
        if (PlanetTargetService.handlePlanetPage(event, game, player, buttonID, planetSpec(player, token))) {
            return;
        }
        String planetId = payload.substring(token.length() + 1);
        if (!planetExists(game, planetId) || !actorCouldKnowPlanet(game, player, planetId)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        offerRouteChoice(player, PLANET, planetId, token);
    }

    private static void offerSystemChoices(Game game, Player player, String token) {
        String buttonPrefix = player.factionButtonChecker() + Constants.AC_PING_SYSTEM + "_" + token;
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            buttons.add(Buttons.gray(
                    buttonPrefix + "_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
        }
        BlindSelectionService.filterForBlindPositionSelection(game, player, buttons, buttonPrefix);
        sendPrivately(player, player.getRepresentationUnfogged() + ", choose the system you're pinging.", buttons);
    }

    @ButtonHandler(Constants.AC_PING_SYSTEM)
    public static void resolveSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(Constants.AC_PING_SYSTEM.length() + 1);
        String token = tokenFrom(payload);
        String position = payload.substring(token.length() + 1);
        if (!actorCanSeeSystem(game, player, position)) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        offerRouteChoice(player, SYSTEM, position, token);
    }

    private static void offerPlayerChoices(Game game, Player player, String token) {
        String buttonPrefix = player.factionButtonChecker() + Constants.AC_PING_PLAYER + "_" + token;
        List<Button> buttons = new ArrayList<>();
        for (Player candidate : game.getRealPlayers()) {
            if (candidate == player) continue;
            buttons.add(FoWHelper.fogSafeTargetButton(buttonPrefix + "_" + candidate.getFaction(), "gray", candidate));
        }
        if (buttons.isEmpty()) {
            sendPrivately(player, player.getRepresentationUnfogged() + ", there is nobody else to ping.");
            return;
        }
        sendPrivately(player, player.getRepresentationUnfogged() + ", choose the player you're pinging.", buttons);
    }

    @ButtonHandler(Constants.AC_PING_PLAYER)
    public static void resolvePlayer(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(Constants.AC_PING_PLAYER.length() + 1);
        String token = tokenFrom(payload);
        String faction = payload.substring(token.length() + 1);
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target == null || target == player) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        ButtonHelper.deleteMessage(event);
        offerRouteChoice(player, PLAYER, faction, token);
    }

    private static void offerRouteChoice(Player player, String type, String targetKey, String token) {
        String base = player.factionButtonChecker() + Constants.AC_PING_ROUTE;
        String suffix = "_" + type + "_" + token + "_" + targetKey;
        List<Button> buttons = new ArrayList<>(List.of(
                Buttons.blue(base + ROUTE_PUBLIC + suffix, "Public"),
                Buttons.gray(base + ROUTE_NON_PUBLIC + suffix, "Non-Public")));
        sendPrivately(
                player,
                player.getRepresentationUnfogged() + ", announce this in the main channel, or "
                        + nonPublicAudience(type) + "?",
                buttons);
    }

    private static String nonPublicAudience(String type) {
        return PLAYER.equals(type)
                ? "tell only the targeted player"
                : "tell only the players who can currently see the targeted system";
    }

    @ButtonHandler(value = Constants.AC_PING_ROUTE, save = false)
    public static void resolveRoute(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(Constants.AC_PING_ROUTE.length()).split("_", 4);
        if (parts.length < 4) {
            return;
        }
        boolean isPublic = ROUTE_PUBLIC.equals(parts[0]);
        String type = parts[1];
        String token = parts[2];
        String targetKey = parts[3];

        String cardTitle = peekCardTitle(game, player, token);
        boolean sent =
                switch (type) {
                    case PLANET -> pingPlanetTarget(game, player, targetKey, isPublic, cardTitle);
                    case SYSTEM -> pingSystemTarget(game, player, targetKey, isPublic, cardTitle);
                    case PLAYER -> pingPlayerTarget(game, player, targetKey, isPublic, cardTitle);
                    default -> false;
                };
        if (sent) {
            ButtonHelper.deleteMessage(event);
        } else {
            PlanetTargetService.fizzle(event, player);
        }
    }

    public static boolean pingPlanetTarget(
            Game game, Player actor, String planetId, boolean isPublic, String cardTitle) {
        return routePlanet(game, actor, planetId, isPublic, targetingLine(game, actor, cardTitle));
    }

    public static boolean pingSystemTarget(
            Game game, Player actor, String position, boolean isPublic, String cardTitle) {
        return routeSystem(game, actor, position, isPublic, targetingLine(game, actor, cardTitle));
    }

    public static boolean pingPlayerTarget(
            Game game, Player actor, String faction, boolean isPublic, String cardTitle) {
        return routePlayer(game, actor, faction, isPublic, targetingLine(game, actor, cardTitle));
    }

    private static boolean routePlanet(Game game, Player actor, String planetId, boolean isPublic, String actorLine) {
        Tile tile = game.getTileFromPlanet(planetId);
        if (tile == null || !planetExists(game, planetId) || !actorCouldKnowPlanet(game, actor, planetId)) {
            return false;
        }
        if (isPublic) {
            return postPublicly(game, planetPingFor(game, null, planetId, actorLine));
        }
        for (Player recipient : playersSeeing(game, actor, tile)) {
            sendPrivately(
                    recipient,
                    recipient.getRepresentationUnfogged() + " - "
                            + planetPingFor(game, recipient, planetId, actorLine));
        }
        confirmRevealedPing(actor, PLANET_EMOJI + " " + planetLabelFor(game, actor, planetId));
        return true;
    }

    private static String planetPingFor(Game game, Player viewer, String planetId, String actorLine) {
        return actorLine + " " + PLANET_EMOJI + " " + planetLabelFor(game, viewer, planetId) + ".";
    }

    private static String planetLabelFor(Game game, Player viewer, String planetId) {
        return PlanetTargetService.fogSafeLabeller(game, viewer).apply(planetId);
    }

    private static boolean routeSystem(Game game, Player actor, String position, boolean isPublic, String actorLine) {
        if (!actorCanSeeSystem(game, actor, position)) {
            return false;
        }
        Tile tile = game.getTileByPosition(position);
        if (isPublic) {
            return postPublicly(game, systemPingFor(game, null, tile, actorLine));
        }
        for (Player recipient : playersSeeing(game, actor, tile)) {
            sendPrivately(
                    recipient,
                    recipient.getRepresentationUnfogged() + " - " + systemPingFor(game, recipient, tile, actorLine));
        }
        confirmRevealedPing(actor, SYSTEM_EMOJI + " " + tile.getRepresentationForButtons(game, actor));
        return true;
    }

    private static String systemPingFor(Game game, Player viewer, Tile tile, String actorLine) {
        return actorLine + " " + SYSTEM_EMOJI + " " + tile.getRepresentationForButtons(game, viewer) + ".";
    }

    private static List<Player> playersSeeing(Game game, Player actor, Tile tile) {
        return game.getRealPlayers().stream()
                .filter(candidate -> candidate != actor)
                .filter(candidate ->
                        FoWHelper.getTilePositionsToShow(game, candidate).contains(tile.getPosition()))
                .toList();
    }

    private static void confirmRevealedPing(Player actor, String targetLabel) {
        sendPrivately(
                actor,
                actor.getRepresentationUnfogged() + ", any players who can currently see " + targetLabel
                        + " have been privately notified that it is being targeted.");
    }

    private static boolean routePlayer(Game game, Player actor, String faction, boolean isPublic, String actorLine) {
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target == null || target == actor) {
            return false;
        }
        String targetLabel = target.fogSafeEmoji() + " " + target.getFactionNameOrColor();
        if (isPublic) {
            return postPublicly(game, actorLine + " " + PLAYER_EMOJI + " " + targetLabel + ".");
        }
        return pingTargetPrivately(actor, target, targetLabel, actorLine);
    }

    private static boolean postPublicly(Game game, String message) {
        MessageChannel mainChannel = game.getMainGameChannel();
        if (mainChannel == null) {
            return false;
        }
        MessageHelper.sendMessageToChannel(mainChannel, message);
        return true;
    }

    private static boolean pingTargetPrivately(Player actor, Player target, String targetLabel, String actorLine) {
        String targetMessage = target.getRepresentationUnfogged() + " - " + actorLine + " you " + PLAYER_EMOJI + ".";
        if (!sendPrivately(target, targetMessage)) {
            return false;
        }
        sendPrivately(
                actor,
                actor.getRepresentationUnfogged() + ", " + PLAYER_EMOJI + " " + targetLabel
                        + " has been privately notified that they are being targeted.");
        return true;
    }

    private static boolean sendPrivately(Player player, String message) {
        TextChannel privateChannel = privateChannelOrReport(player);
        if (privateChannel == null) {
            return false;
        }
        MessageHelper.sendMessageToChannel(privateChannel, message);
        return true;
    }

    private static boolean sendPrivately(Player player, String message, List<Button> buttons) {
        TextChannel privateChannel = privateChannelOrReport(player);
        if (privateChannel == null) {
            return false;
        }
        MessageHelper.sendMessageToChannelWithButtons(privateChannel, message, buttons);
        return true;
    }

    private static TextChannel privateChannelOrReport(Player player) {
        TextChannel privateChannel = player.getPrivateChannel();
        if (privateChannel == null) {
            GMService.logActivity(
                    player.getGame(),
                    "⚠️ Action card target ping could not be delivered: "
                            + player.getRepresentationUnfogged()
                            + " has no private channel.",
                    true);
        }
        return privateChannel;
    }

    private static String targetingLine(Game game, Player actor, String cardTitle) {
        String actorName = FoWHelper.actorOrAnon(game, actor, "Someone");
        if (StringUtils.isBlank(cardTitle)) {
            return actorName + " is targeting";
        }
        return actorName + "'s action card _" + cardTitle + "_ is targeting";
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
