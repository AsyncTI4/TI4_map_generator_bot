package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.transaction.SendDebtService;

@UtilityClass
public class NetrunnersAbilitiesHandler {
    public static final String NEURAL_INSTRUMENTS_ABILITY = "neural_instruments";
    public static final String PROXY_NETWORK_ABILITY = "proxy_network";
    public static final String CONTROL_TOKEN_POOL = "hackerman";
    private static final String SHARED_NETWORK_ACCESS = "bepnnetrunners";
    public static final String PROXY_TECH = "netrunnersProxyTech";

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
            SendDebtService.sendDebt(techGainer, netrunner, 1, CONTROL_TOKEN_POOL);
            MessageHelper.sendMessageToChannel(
                    netrunner.getCorrectChannel(),
                    netrunner.getRepresentationUnfogged() + ", you automatically placed 1 of "
                            + techGainer.getRepresentation(false, true)
                            + "'s command tokens on their faction sheet via **Neural Instruments**."
                            + "\n-# This optional effect was resolved automatically for convenience.");
        }
    }

    public static void announceMimeticOverride(Player player, Player opponent, MessageChannel channel) {
        if (player == null || opponent == null || channel == null || !player.hasTech("benetrunnersmo")) return;
        String technologies = player.getTechs().stream()
                .filter(opponent::hasTech)
                .map(Mapper::getTech)
                .filter(java.util.Objects::nonNull)
                .map(tech -> "_" + tech.getName() + "_")
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        if (!technologies.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    player.getRepresentation() + "'s _Mimetic Override_ means "
                            + opponent.getRepresentation() + " treats " + technologies
                            + " as having no ability text for this combat.\n-# This effect is player-enforced.");
        }
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
                        "Copy a Technology from " + player.getColorDisplayName()))
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
                        tech.getName()))
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
