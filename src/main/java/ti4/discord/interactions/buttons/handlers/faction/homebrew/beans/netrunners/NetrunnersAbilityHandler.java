package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tech.PlayerTechService;
import ti4.service.transaction.SendDebtService;

@UtilityClass
public class NetrunnersAbilityHandler {
    private static final String PROXY_NETWORK_TOKEN = "proxyNetworkToken_";
    private static final String PROXY_NETWORK_TECH = "proxyNetworkTech_";
    private static final String PROXY_NETWORK_TECH_NAV = "proxyNetworkTechNav_";
    private static final String PROXY_NETWORK_TARGET = "proxyNetworkTarget_";

    public static void resolveNeuralInstrumentsDebt(Player player, Game game, GenericInteractionCreateEvent event) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2 != player
                    && p2.hasAbility("neural_instruments")
                    && p2.getDebtTokenCount(player.getColor(), Constants.NETRUNNERS_DEBT_POOL) == 0) {
                game.setDebtPoolIcon(Constants.NETRUNNERS_DEBT_POOL, FactionEmojis.netrunners.emojiString());
                SendDebtService.sendDebt(player, p2, 1, Constants.NETRUNNERS_DEBT_POOL);
                if (game.isFowMode()) {
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentationUnfogged() + ", you sent 1 debt token to "
                                    + p2.getFactionEmojiOrColor()
                                    + ", for their \"Hacker Man\" pool, due to their **Neural Instruments** ability.");
                    MessageHelper.sendMessageToChannel(
                            p2.getCorrectChannel(),
                            p2.getRepresentationUnfogged() + ", you collected 1 debt token from "
                                    + player.getFactionEmojiOrColor()
                                    + ", for your \"Hacker Man\" pool, due to your **Neural Instruments** ability.");
                } else {
                    MessageHelper.sendMessageToChannel(
                            p2.getCorrectChannel(),
                            p2.getRepresentationUnfogged() + ", you collected 1 debt token from "
                                    + player.getRepresentationUnfogged()
                                    + ", for your \"Hacker Man\" pool, due to your **Neural Instruments** ability.");
                }
                break;
            }
        }
    }

    public static Button getProxyNetworkButton(Player player) {
        if (!hasAvailableProxyNetwork(player)) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + "useProxyNetwork", "Use Proxy Network", FactionEmojis.netrunners);
    }

    @ButtonHandler("useProxyNetwork")
    public static void useProxyNetwork(ButtonInteractionEvent event, Game game, Player player) {
        offerProxyNetwork(game, player);
    }

    public static boolean offerProxyNetwork(Game game, Player player) {
        if (!hasAvailableProxyNetwork(player)) return false;
        List<Button> buttons = new ArrayList<>();
        for (Player tokenOwner : game.getRealPlayers()) {
            if (player.getDebtTokenCount(tokenOwner.getColor(), Constants.NETRUNNERS_DEBT_POOL) > 0) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + PROXY_NETWORK_TOKEN + tokenOwner.getColor(),
                        tokenOwner.getFactionNameOrColor(),
                        tokenOwner.getFactionEmojiOrColor()));
            }
        }
        if (buttons.isEmpty()) {
            return false;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose a control token to return with **Proxy Network**.",
                buttons);
        return true;
    }

    @ButtonHandler(PROXY_NETWORK_TOKEN)
    public static void chooseProxyNetworkToken(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasAbility("proxy_network")) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You do not have **Proxy Network**.");
            return;
        }
        Player tokenOwner = game.getPlayerFromColorOrFaction(StringUtils.substringAfter(buttonID, PROXY_NETWORK_TOKEN));
        if (tokenOwner == null || player.getDebtTokenCount(tokenOwner.getColor(), Constants.NETRUNNERS_DEBT_POOL) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That control token is no longer available.");
            return;
        }
        game.setStoredValue(PROXY_NETWORK_TARGET + player.getFaction(), tokenOwner.getUserID());
        ButtonHelper.deleteMessage(event);
        sendProxyNetworkTechChoices(game, player, tokenOwner);
    }

    @ButtonHandler(PROXY_NETWORK_TECH)
    public static void chooseProxyNetworkTech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player tokenOwner = getProxyNetworkTokenOwner(game, player);
        String techID = StringUtils.substringAfter(buttonID, PROXY_NETWORK_TECH);
        TechnologyModel tech = Mapper.getTech(techID);
        if (!player.hasAbility("proxy_network")
                || tokenOwner == null
                || player.getDebtTokenCount(tokenOwner.getColor(), Constants.NETRUNNERS_DEBT_POOL) < 1
                || tech == null
                || !tokenOwner.hasTech(techID)
                || tech.isFactionTech()
                || player.hasTech(techID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That technology is no longer eligible for **Proxy Network**.");
            return;
        }
        player.removeDebtTokens(tokenOwner.getColor(), 1, Constants.NETRUNNERS_DEBT_POOL);
        game.removeStoredValue(PROXY_NETWORK_TARGET + player.getFaction());
        PlayerTechService.getTech(game, player, event, "getTech_" + techID);
    }

    @ButtonHandler(PROXY_NETWORK_TECH_NAV)
    public static void turnProxyNetworkTechPage(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player tokenOwner = getProxyNetworkTokenOwner(game, player);
        if (tokenOwner == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There is no pending **Proxy Network** choice.");
            return;
        }
        NewStuffHelper.turnPage(
                event,
                buttonID,
                proxyNetworkTechMessage(player, tokenOwner),
                () -> getProxyNetworkTechButtons(player, tokenOwner),
                List.of(),
                player.factionButtonChecker() + PROXY_NETWORK_TECH_NAV);
    }

    private static void sendProxyNetworkTechChoices(Game game, Player player, Player tokenOwner) {
        List<Button> buttons = getProxyNetworkTechButtons(player, tokenOwner);
        if (buttons.isEmpty()) {
            game.removeStoredValue(PROXY_NETWORK_TARGET + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + " has no eligible non-faction technology to gain from "
                            + tokenOwner.getFactionEmojiOrColor()
                            + ".");
            return;
        }
        NewStuffHelper.sendPaginated(
                player.getCardsInfoThread(),
                proxyNetworkTechMessage(player, tokenOwner),
                buttons,
                List.of(),
                player.factionButtonChecker() + PROXY_NETWORK_TECH_NAV);
    }

    private static String proxyNetworkTechMessage(Player player, Player tokenOwner) {
        return player.getRepresentationUnfogged()
                + ", choose a non-faction technology owned by "
                + tokenOwner.getFactionEmojiOrColor()
                + " to gain with **Proxy Network**.";
    }

    private static List<Button> getProxyNetworkTechButtons(Player player, Player tokenOwner) {
        return tokenOwner.getTechs().stream()
                .map(Mapper::getTech)
                .filter(tech -> tech != null && !tech.isFactionTech() && !player.hasTech(tech.getAlias()))
                .sorted(TechnologyModel.sortByTechRequirements)
                .map(tech -> Buttons.gray(
                        player.factionButtonChecker() + PROXY_NETWORK_TECH + tech.getAlias(),
                        tech.getName(),
                        tech.getCondensedReqsEmojis(true)))
                .toList();
    }

    private static Player getProxyNetworkTokenOwner(Game game, Player player) {
        String userID = game.getStoredValue(PROXY_NETWORK_TARGET + player.getFaction());
        return userID.isEmpty() ? null : game.getPlayer(userID);
    }

    private static boolean hasAvailableProxyNetwork(Player player) {
        return player.hasAbility("proxy_network")
                && player.getDebtTokens(Constants.NETRUNNERS_DEBT_POOL).values().stream()
                        .anyMatch(count -> count > 0);
    }
}
