package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.transaction.SendDebtService;
import ti4.service.turn.EndTurnService;

@UtilityClass
public class NetrunnersAbilitiesHandler {
    public static final String NEURAL_INSTRUMENTS_ABILITY = "neural_instruments";
    public static final String PROXY_NETWORK_ABILITY = "proxy_network";
    public static final String CONTROL_TOKEN_POOL = "hackerman";
    private static final String SHARED_NETWORK_ACCESS = "bepnnetrunners";
    private static final String PROXY_TECH = "netrunnersProxyTech";
    private static final String REVERSE_ENGINEERING_USED = "netrunnersReverseEngineeringUsed";

    public static void offerNeuralInstruments(Game game, Player techGainer) {
        if (game == null || techGainer == null) return;
        List<Player> netrunners = game.getRealPlayersExcludingThis(techGainer).stream()
                .filter(player -> player.hasAbility(NEURAL_INSTRUMENTS_ABILITY))
                .toList();
        if (netrunners.isEmpty()) return;
        if (game.getDebtPoolIcon(CONTROL_TOKEN_POOL) == null) {
            game.setDebtPoolIcon(CONTROL_TOKEN_POOL, FactionEmojis.netrunners.toString());
        }
        for (Player netrunner : netrunners) {
            if (netrunner.getDebtTokenCount(techGainer.getColor(), CONTROL_TOKEN_POOL) > 0) continue;
            if (netrunner.hasUnlockedBreakthrough("netrunnersbt")
                    && !techGainer.getBreakthroughIDs().isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                        netrunner.getCorrectChannel(),
                        netrunner.getRepresentationUnfogged() + ", you may use **Data Breach** instead of placing "
                                + techGainer.getRepresentation(false, true) + "'s token.",
                        List.of(
                                Buttons.gray(
                                        netrunner.factionButtonChecker() + "netrunnersDataBreachMove_"
                                                + techGainer.getFaction(),
                                        "Use Data Breach"),
                                Buttons.red(
                                        netrunner.factionButtonChecker() + "netrunnersDataBreachDecline_"
                                                + techGainer.getFaction(),
                                        "Decline")));
                continue;
            }
            SendDebtService.sendDebt(techGainer, netrunner, 1, CONTROL_TOKEN_POOL);
            MessageHelper.sendMessageToChannel(
                    netrunner.getCorrectChannel(),
                    netrunner.getRepresentationUnfogged() + ", you automatically placed 1 of "
                            + techGainer.getRepresentation(false, true)
                            + "'s command tokens on their faction sheet via **Neural Instruments**."
                            + "\n-# This optional effect was resolved automatically for convenience.");
        }
    }

    public static void offerBlackout(Game game, Player activator, ti4.game.Tile tile) {
        if (game == null || activator == null || tile == null) {
            return;
        }
        for (Player netrunner : game.getRealPlayersExcludingThis(activator)) {
            if (!netrunner.hasTech("benetrunnersbo") || !FoWHelper.playerHasUnitsInSystem(netrunner, tile)) continue;
            List<Button> buttons = activator.getTechs().stream()
                    .map(Mapper::getTech)
                    .filter(java.util.Objects::nonNull)
                    .filter(tech -> activator.hasTech(tech.getAlias()))
                    .filter(tech -> !tech.isUnitUpgrade())
                    .map(tech -> Buttons.gray(
                            netrunner.factionButtonChecker() + "netrunnersBlackout_" + activator.getFaction() + "_"
                                    + tech.getAlias(),
                            tech.getName()))
                    .toList();
            if (!buttons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(
                        netrunner.getCorrectChannel(),
                        netrunner.getRepresentationUnfogged()
                                + ", please choose a technology that cannot be used until the end of that player's turn due to **Blackout**.",
                        buttons);
            }
        }
    }

    @ButtonHandler("netrunnersBlackout_")
    public static void resolveBlackout(Game game, Player netrunner, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("netrunnersBlackout_", "").split("_", 2);
        if (parts.length != 2) return;
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        if (target == null
                || !netrunner.hasTech("benetrunnersbo")
                || !target.hasTech(parts[1])
                || Mapper.getTech(parts[1]) == null
                || Mapper.getTech(parts[1]).isUnitUpgrade()) return;
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                netrunner.getRepresentation() + ", " + target.getRepresentation() + ", **"
                        + Mapper.getTech(parts[1]).getName() + "** cannot be used until the end of "
                        + target.getRepresentation(false, true) + "'s turn due to **Blackout**."
                        + "\n-# This effect is player-enforced.");
    }

    @ButtonHandler("netrunnersDataBreachMove_")
    public static void moveDataBreachToken(Game game, Player netrunner, ButtonInteractionEvent event, String buttonID) {
        if (game == null || netrunner == null) return;
        String payload = buttonID.replace("netrunnersDataBreachMove_", "");
        String[] parts = payload.split("_", 2);
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        if (target == null
                || !netrunner.hasUnlockedBreakthrough("netrunnersbt")
                || !netrunner.hasAbility(NEURAL_INSTRUMENTS_ABILITY)) return;
        if (target.getBreakthroughIDs().isEmpty()) {
            SendDebtService.sendDebt(target, netrunner, 1, CONTROL_TOKEN_POOL);
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    netrunner.getRepresentationNoPing() + " placed 1 of "
                            + target.getRepresentation(false, true)
                            + "'s command tokens on their faction sheet via **Neural Instruments**.");
            return;
        }

        String message =
                netrunner.getRepresentationUnfogged() + ", choose the breakthrough for your **Data Breach** token.";
        String buttonPrefix =
                netrunner.factionButtonChecker() + "netrunnersDataBreachMove_" + target.getFaction() + "_";
        List<Button> buttons = target.getBreakthroughIDs().stream()
                .filter(bt -> Mapper.getBreakthrough(bt) != null)
                .map(Mapper::getBreakthrough)
                .map(breakthrough -> Buttons.green(
                        buttonPrefix + breakthrough.getAlias(),
                        NetrunnersBreakthroughHandler.getDataBreachBreakthroughLabel(breakthrough)))
                .toList();
        if (buttons.isEmpty()) {
            SendDebtService.sendDebt(target, netrunner, 1, CONTROL_TOKEN_POOL);
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    netrunner.getRepresentationNoPing() + " placed 1 of "
                            + target.getRepresentation(false, true)
                            + "'s command tokens on their faction sheet via **Neural Instruments**.");
            return;
        }
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) return;
        if (parts.length == 1) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
            return;
        }
        String breakthroughId = parts[1];
        if (!target.hasBreakthrough(breakthroughId) || Mapper.getBreakthrough(breakthroughId) == null) return;
        game.setStoredValue(
                "netrunnersDataBreach" + netrunner.getFaction(), target.getFaction() + "~" + breakthroughId);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                netrunner.getRepresentation() + " moved their **Data Breach** token onto "
                        + Mapper.getBreakthrough(breakthroughId).getNameRepresentation() + ".");
    }

    @ButtonHandler("netrunnersDataBreachDecline_")
    public static void declineDataBreachToken(
            Game game, Player netrunner, ButtonInteractionEvent event, String buttonID) {
        if (game == null || netrunner == null) return;
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("netrunnersDataBreachDecline_", ""));
        if (target == null
                || !netrunner.hasAbility(NEURAL_INSTRUMENTS_ABILITY)
                || netrunner.getDebtTokenCount(target.getColor(), CONTROL_TOKEN_POOL) > 0) {
            return;
        }
        SendDebtService.sendDebt(target, netrunner, 1, CONTROL_TOKEN_POOL);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                netrunner.getRepresentationNoPing() + " placed 1 of " + target.getRepresentation(false, true)
                        + "'s command tokens on their faction sheet via **Neural Instruments**.");
    }

    public static Button getProxyNetworkButton(Game game, Player netrunner) {
        if (game == null || netrunner == null || !netrunner.hasAbility(PROXY_NETWORK_ABILITY)) return null;
        boolean hasEligiblePlayer = game.getRealPlayersExcludingThis(netrunner).stream()
                .anyMatch(player -> netrunner.getDebtTokenCount(player.getColor(), CONTROL_TOKEN_POOL) > 0
                        && player.getTechs().stream()
                                .map(Mapper::getTech)
                                .anyMatch(tech ->
                                        tech != null && tech.getFaction().isEmpty()));
        if (!hasEligiblePlayer) return null;
        return Buttons.gray(
                netrunner.factionButtonChecker() + "proxyNetworkStart", "Use Proxy Network", FactionEmojis.netrunners);
    }

    @ButtonHandler("proxyNetworkStart")
    public static void startProxyNetwork(Game game, Player netrunner, ButtonInteractionEvent event) {
        if (game == null || netrunner == null || !netrunner.hasAbility(PROXY_NETWORK_ABILITY)) return;
        List<Button> buttons = game.getRealPlayersExcludingThis(netrunner).stream()
                .filter(player -> netrunner.getDebtTokenCount(player.getColor(), CONTROL_TOKEN_POOL) > 0)
                .filter(player -> player.getTechs().stream()
                        .map(Mapper::getTech)
                        .anyMatch(tech -> tech != null && tech.getFaction().isEmpty()))
                .map(player -> Buttons.green(
                        netrunner.factionButtonChecker() + "proxyNetworkPlayer_" + player.getFaction(),
                        "Copy a Technology from " + player.getColorDisplayName(),
                        FactionEmojis.netrunners))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                netrunner.getRepresentationUnfogged()
                        + ", please choose the player whose control token you will remove to copy one of their non-faction technologies.",
                buttons);
    }

    @ButtonHandler("proxyNetworkPlayer_")
    public static void chooseProxyNetworkTech(
            Game game, Player netrunner, ButtonInteractionEvent event, String buttonID) {
        Player source = game.getPlayerFromColorOrFaction(buttonID.replace("proxyNetworkPlayer_", ""));
        if (source == null
                || !netrunner.hasAbility(PROXY_NETWORK_ABILITY)
                || netrunner.getDebtTokenCount(source.getColor(), CONTROL_TOKEN_POOL) < 1) return;
        List<Button> buttons = source.getTechs().stream()
                .map(Mapper::getTech)
                .filter(tech -> tech != null && tech.getFaction().isEmpty() && !netrunner.hasTech(tech.getAlias()))
                .map(tech -> Buttons.green(
                        netrunner.factionButtonChecker() + "proxyNetworkTech_" + source.getFaction() + "_"
                                + tech.getAlias(),
                        tech.getName(),
                        FactionEmojis.netrunners))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    netrunner.getRepresentationUnfogged() + " has no eligible technology to copy from that player.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                netrunner.getRepresentationUnfogged() + ", please choose the technology to copy.",
                buttons);
    }

    @ButtonHandler("proxyNetworkTech_")
    public static void resolveProxyNetwork(Game game, Player netrunner, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("proxyNetworkTech_", "").split("_", 2);
        if (parts.length != 2) return;
        Player source = game.getPlayerFromColorOrFaction(parts[0]);
        String techId = parts[1];
        if (source == null
                || !netrunner.hasAbility(PROXY_NETWORK_ABILITY)
                || !source.hasTech(techId)
                || Mapper.getTech(techId) == null
                || Mapper.getTech(techId).getFaction().isPresent()
                || netrunner.hasTech(techId)
                || netrunner.getDebtTokenCount(source.getColor(), CONTROL_TOKEN_POOL) < 1) return;
        netrunner.clearDebt(source, 1, CONTROL_TOKEN_POOL);
        netrunner.addTech(techId);
        game.setStoredValue(PROXY_TECH + netrunner.getFaction(), techId);
        offerSharedNetworkAccess(game, netrunner, techId);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                netrunner.getRepresentation() + " copied "
                        + Mapper.getTech(techId).getNameRepresentation()
                        + " via **Proxy Network** until the end of their turn.");
    }

    public static boolean offerReverseEngineering(Game game, Player player) {
        if (game == null || player == null) {
            return false;
        }
        String techId = game.getStoredValue(PROXY_TECH + player.getFaction());
        if (!player.hasAbility("reverse_engineering")
                || techId.isEmpty()
                || !player.hasTech(techId)
                || player.getStrategicCC() < 1
                || Integer.toString(game.getRound())
                        .equals(game.getStoredValue(REVERSE_ENGINEERING_USED + player.getFaction()))) return false;
        var tech = Mapper.getTech(techId);
        if (tech == null) return false;
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", you may spend 1 strategy token to permanently gain "
                        + tech.getNameRepresentation() + " with **Reverse Engineering**.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + "netrunnersReverseEngineeringGain",
                                "Use Reverse Engineering",
                                FactionEmojis.netrunners),
                        Buttons.red(player.factionButtonChecker() + "netrunnersReverseEngineeringDecline", "Decline")));
        return true;
    }

    public static void clearReverseEngineering(Game game, Player player) {
        if (game != null && player != null) {
            game.removeStoredValue(REVERSE_ENGINEERING_USED + player.getFaction());
        }
    }

    public static void clearProxyNetwork(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }
        String techId = game.getStoredValue(PROXY_TECH + player.getFaction());
        if (!techId.isEmpty()) {
            player.removeTech(techId);
            game.removeStoredValue(PROXY_TECH + player.getFaction());
        }
    }

    @ButtonHandler("netrunnersReverseEngineeringGain")
    public static void resolveReverseEngineering(Game game, Player player, ButtonInteractionEvent event) {
        if (game == null || player == null) {
            return;
        }
        String techId = game.getStoredValue(PROXY_TECH + player.getFaction());
        if (!player.hasAbility("reverse_engineering")
                || techId.isEmpty()
                || !player.hasTech(techId)
                || player.getStrategicCC() < 1) return;
        player.setStrategicCC(player.getStrategicCC() - 1);
        game.setStoredValue(REVERSE_ENGINEERING_USED + player.getFaction(), Integer.toString(game.getRound()));
        game.removeStoredValue(PROXY_TECH + player.getFaction());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " spent 1 strategy token and permanently gained "
                        + Mapper.getTech(techId).getNameRepresentation() + " with **Reverse Engineering**.");
        EndTurnService.endTurnAndUpdateMap(event, game, player);
    }

    @ButtonHandler("netrunnersReverseEngineeringDecline")
    public static void declineReverseEngineering(Game game, Player player, ButtonInteractionEvent event) {
        if (game == null || player == null || !player.hasAbility("reverse_engineering")) {
            return;
        }
        game.setStoredValue(REVERSE_ENGINEERING_USED + player.getFaction(), Integer.toString(game.getRound()));
        ButtonHelper.deleteMessage(event);
        EndTurnService.endTurnAndUpdateMap(event, game, player);
    }

    private static void offerSharedNetworkAccess(Game game, Player netrunner, String techId) {
        if (game == null || netrunner == null || !netrunner.hasAbility(PROXY_NETWORK_ABILITY)) {
            return;
        }
        for (Player holder : game.getRealPlayersExcludingThis(netrunner)) {
            if (!holder.getPromissoryNotes().containsKey(SHARED_NETWORK_ACCESS) || holder.hasTech(techId)) continue;
            MessageHelper.sendMessageToChannelWithButtons(
                    holder.getCorrectChannel(),
                    holder.getRepresentationUnfogged() + ", " + netrunner.getRepresentation(false, true)
                            + " copied " + Mapper.getTech(techId).getNameRepresentation()
                            + ". You may play _Shared Network Access_ to gain that technology.",
                    List.of(Buttons.green(
                            holder.factionButtonChecker() + "sharedNetworkAccess_" + netrunner.getFaction() + "_"
                                    + techId,
                            "Play Shared Network Access")));
        }
    }

    @ButtonHandler("sharedNetworkAccess_")
    public static void resolveSharedNetworkAccess(
            Game game, Player holder, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("sharedNetworkAccess_", "").split("_", 2);
        if (parts.length != 2) return;
        Player netrunner = game.getPlayerFromColorOrFaction(parts[0]);
        String techId = parts[1];
        if (netrunner == null
                || !holder.getPromissoryNotes().containsKey(SHARED_NETWORK_ACCESS)
                || !netrunner.hasTech(techId)
                || Mapper.getTech(techId) == null
                || holder.hasTech(techId)) return;
        holder.addPromissoryNoteToPlayArea(SHARED_NETWORK_ACCESS);
        holder.addTech(techId);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                holder.getRepresentation() + " played _Shared Network Access_ and gained "
                        + Mapper.getTech(techId).getNameRepresentation() + ".");
    }
}
