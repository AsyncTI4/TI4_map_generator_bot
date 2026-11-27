package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.brynfolk;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class BrynfolkAbilityHandler {
    private static final String BLOT_DEBT_POOL = "Blot";
    private static final int BLOT_TOKEN_LIMIT = 6;
    private static final String USE_THRALL_CONSCRIPTION = "useThrallConscription_";
    private static final String SELECT_THRALL_TOKEN = "selectThrallToken_";
    private static final String PLACE_THRALL_INFANTRY = "placeThrallInfantry_";
    private static final String USE_WAR_SPOILS = "useWarSpoils_";

    @ButtonHandler("selectBlotPlayer")
    public static List<Button> selectBlotPlayer(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        if (!hasBlotCapacity(player)) {
            return buttons;
        }
        for (Player tokenOwner : game.getRealPlayers()) {
            String id = player.factionButtonChecker() + "placeBlotToken_" + tokenOwner.getFaction();
            buttons.add(Buttons.gray(id, "Place " + tokenOwner.getFaction() + " token", tokenOwner.fogSafeEmoji()));
        }

        return buttons;
    }

    @ButtonHandler("placeBlotToken_")
    public static void placeBlotToken(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String placedTokenOwner = buttonID.replace("placeBlotToken_", "");
        Player tokenOwner = game.getPlayerFromColorOrFaction(placedTokenOwner);
        if (tokenOwner == null) {
            return;
        }
        if (!hasBlotCapacity(player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setDebtPoolIcon(BLOT_DEBT_POOL, FactionEmojis.brynfolk.emojiString());
        player.addDebtTokens(tokenOwner.getColor(), 1, BLOT_DEBT_POOL);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed "
                        + (tokenOwner == player ? "their own" : tokenOwner.getRepresentationNoPing() + "'s")
                        + " control token on Blot with _Gald-Tithe_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void offerThrallConscription(Player player, Game game, String planetName) {
        if (getBlotTokenButtons(player, game, SELECT_THRALL_TOKEN).isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButton(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", if you win this ground combat, you may use _Thrall Conscription_ to remove 1 control token from Blot and place up to 3 infantry on the combat planet.",
                Buttons.gray(
                        player.factionButtonChecker() + USE_THRALL_CONSCRIPTION + planetName,
                        "Use Thrall Conscription",
                        FactionEmojis.brynfolk));
    }

    public static void offerWarSpoils(Player player, Player formerController, Planet planet, Game game) {
        if (!player.hasAbility("war_spoils")
                || player.getDebtTokenCount(formerController.getColor(), BLOT_DEBT_POOL) < 1) {
            return;
        }
        int tradeGoods = planet.getResources();
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you gained control of " + planet.getRepresentation(game)
                        + " from " + formerController.getRepresentation(false, false)
                        + ". You may remove 1 of their control tokens from Blot to gain " + tradeGoods
                        + " trade goods with _War Spoils_.",
                Buttons.gray(
                        player.factionButtonChecker() + USE_WAR_SPOILS + planet.getName() + "|"
                                + formerController.getColor(),
                        "Use War Spoils",
                        FactionEmojis.brynfolk));
    }

    @ButtonHandler(USE_WAR_SPOILS)
    public static void useWarSpoils(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String payload = buttonID.substring(USE_WAR_SPOILS.length());
        int separator = payload.lastIndexOf('|');
        if (separator < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String planetName = payload.substring(0, separator);
        String color = payload.substring(separator + 1);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null || player.getDebtTokenCount(color, BLOT_DEBT_POOL) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.removeDebtTokens(color, 1, BLOT_DEBT_POOL);
        ButtonHelperStats.gainTGs(event, game, player, planet.getResources(), false);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " used _War Spoils_ to remove the planet's previous owner's control token from Blot to gain "
                        + planet.getResources() + " trade goods.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_THRALL_CONSCRIPTION)
    public static void useThrallConscription(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String planetName = buttonID.substring(USE_THRALL_CONSCRIPTION.length());
        List<Button> buttons = getBlotTokenButtons(player, game, SELECT_THRALL_TOKEN + planetName + "|");
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", choose a control token to remove from Blot.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_THRALL_TOKEN)
    public static void selectThrallToken(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String payload = buttonID.substring(SELECT_THRALL_TOKEN.length());
        int separator = payload.lastIndexOf('|');
        if (separator < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String planetName = payload.substring(0, separator);
        String color = payload.substring(separator + 1);
        if (player.getDebtTokenCount(color, BLOT_DEBT_POOL) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.removeDebtTokens(color, 1, BLOT_DEBT_POOL);
        List<Button> buttons = new ArrayList<>();
        for (int infantry = 1; infantry <= 3; infantry++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_THRALL_INFANTRY + planetName + "|" + infantry,
                    "Place " + infantry + " Infantry",
                    FactionEmojis.brynfolk));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", choose how many infantry to place on the combat planet.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_THRALL_INFANTRY)
    public static void placeThrallInfantry(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String payload = buttonID.substring(PLACE_THRALL_INFANTRY.length());
        int separator = payload.lastIndexOf('|');
        if (separator < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String planetName = payload.substring(0, separator);
        int infantry;
        try {
            infantry = Integer.parseInt(payload.substring(separator + 1));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (infantry < 1 || infantry > 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile planetTile = game.getTileFromPlanet(planetName);
        if (planetTile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, planetTile, game, player.getColor(), infantry + " infantry " + planetName);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " used _Thrall Conscription_ to place " + infantry + " infantry on "
                        + planetName + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getBlotTokenButtons(Player player, Game game, String prefix) {
        List<Button> buttons = new ArrayList<>();
        player.getDebtTokens(BLOT_DEBT_POOL).forEach((color, count) -> {
            Player tokenOwner = game.getPlayerFromColorOrFaction(color);
            if (tokenOwner != null && count != null && count > 0) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + prefix + color,
                        "Remove " + tokenOwner.getFaction() + " Token",
                        tokenOwner.fogSafeEmoji()));
            }
        });
        return buttons;
    }

    private static int getBlotTokenCount(Player player) {
        int count = 0;
        for (Integer tokenCount : player.getDebtTokens(BLOT_DEBT_POOL).values()) {
            if (tokenCount != null) {
                count += tokenCount;
            }
        }
        return count;
    }

    public static boolean hasBlotCapacity(Player player) {
        return getBlotTokenCount(player) < BLOT_TOKEN_LIMIT;
    }

    public static boolean hasOwnBlotToken(Player player) {
        return player.getDebtTokenCount(player.getColor(), BLOT_DEBT_POOL) > 0;
    }

    public static boolean returnOwnBlotToken(Player player, Game game) {
        if (!hasOwnBlotToken(player)) {
            return false;
        }
        player.removeDebtTokens(player.getColor(), 1, BLOT_DEBT_POOL);
        return true;
    }

    public static void syncBlotLegendaryAbility(Player player, Game game) {
        if (!player.getPlanets().contains("blot")) {
            return;
        }
        Planet blot = game.getUnitHolderFromPlanet("blot");
        if (blot == null) {
            return;
        }
        for (int value = 1; value <= BLOT_TOKEN_LIMIT; value++) {
            blot.removeToken("attachment_blot_positive_resource_" + value + ".png");
            blot.removeToken("attachment_blot_positive_influence_" + value + ".png");
        }
        int influence = player.getDebtTokenCount(player.getColor(), BLOT_DEBT_POOL);
        int resources = getBlotTokenCount(player) - influence;
        if (resources > 0) {
            blot.addToken("attachment_blot_positive_resource_" + resources + ".png");
        }
        if (influence > 0) {
            blot.addToken("attachment_blot_positive_influence_" + influence + ".png");
        }
        CommanderUnlockCheckService.checkPlayer(player, "brynfolk");
    }
}
