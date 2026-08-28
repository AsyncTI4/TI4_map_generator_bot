package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.turn.EndTurnService;

@UtilityClass
public class NetrunnersBreakthroughHandler {
    private static final String DATA_BREACH = "netrunnersbt";

    public static BreakthroughModel getCopiedDataBreachBreakthrough(Game game, Player player) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough("netrunnersbt")) {
            return null;
        }
        String[] placement = game.getStoredValue("netrunnersDataBreach" + player.getFaction())
                .split("~", 2);
        if (placement.length != 2) {
            return null;
        }
        Player target = game.getPlayerFromColorOrFaction(placement[0]);
        BreakthroughModel copiedBreakthrough = Mapper.getBreakthrough(placement[1]);
        if (target == null || !target.hasBreakthrough(placement[1]) || copiedBreakthrough == null) {
            return null;
        }
        return copiedBreakthrough;
    }

    public static String getDataBreachTargetFaction(Game game, Player player) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough("netrunnersbt")) {
            return null;
        }
        String[] placement = game.getStoredValue("netrunnersDataBreach" + player.getFaction())
                .split("~", 2);
        if (placement.length != 2) {
            return null;
        }
        Player target = game.getPlayerFromColorOrFaction(placement[0]);
        return target != null && target.hasBreakthrough(placement[1]) ? target.getFaction() : null;
    }

    public static String getDataBreachBreakthroughLabel(BreakthroughModel breakthrough) {
        List<TechnologyType> synergies = breakthrough.getSynergy();
        if (synergies == null || synergies.isEmpty()) {
            return breakthrough.getName() + " - No Synergy";
        }
        StringBuilder colors = new StringBuilder();
        for (TechnologyType synergy : synergies) {
            if (colors.length() > 0) {
                colors.append('/');
            }
            switch (synergy) {
                case PROPULSION -> colors.append("Blue");
                case BIOTIC -> colors.append("Green");
                case CYBERNETIC -> colors.append("Yellow");
                case WARFARE -> colors.append("Red");
                default -> colors.append(synergy.readableName());
            }
        }
        return breakthrough.getName() + " - " + colors;
    }

    public static void offerDataBreachPlacement(Game game, Player player) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough("netrunnersbt")) {
            return;
        }
        List<Button> buttons = getDataBreachPlacementButtons(game, player);
        if (buttons.isEmpty()) return;
        String message =
                player.getRepresentationUnfogged() + ", please choose the breakthrough for your _Data Breach_ token.";
        String buttonPrefix = player.factionButtonChecker() + "netrunnersDataBreach_";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    public static boolean offerDataBreachTechnology(Game game, Player player) {
        if (game == null || player == null || !player.hasReadyBreakthrough(DATA_BREACH) || player.getStrategicCC() < 1) {
            return false;
        }
        String techId = game.getStoredValue(NetrunnersAbilitiesHandler.PROXY_TECH + player.getFaction());
        if (techId.isEmpty() || !player.hasTech(techId) || Mapper.getTech(techId) == null) {
            return false;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", you may exhaust _Data Breach_ and spend 1 command token from your strategy pool to permanently gain "
                        + Mapper.getTech(techId).getNameRepresentation() + ".",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + "netrunnersDataBreachGainTech",
                                "Use Data Breach"),
                        Buttons.red(player.factionButtonChecker() + "netrunnersDataBreachDeclineTech", "Decline")));
        return true;
    }

    @ButtonHandler("netrunnersDataBreachGainTech")
    public static void resolveDataBreachTechnology(Game game, Player player, ButtonInteractionEvent event) {
        if (game == null || player == null || !player.hasReadyBreakthrough(DATA_BREACH) || player.getStrategicCC() < 1) {
            return;
        }
        String techId = game.getStoredValue(NetrunnersAbilitiesHandler.PROXY_TECH + player.getFaction());
        if (techId.isEmpty() || !player.hasTech(techId) || Mapper.getTech(techId) == null) {
            return;
        }
        player.setStrategicCC(player.getStrategicCC() - 1);
        BreakthroughCommandHelper.exhaustBreakthrough(player, DATA_BREACH);
        game.removeStoredValue(NetrunnersAbilitiesHandler.PROXY_TECH + player.getFaction());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " spent 1 command token from their strategy pool and permanently gained "
                        + Mapper.getTech(techId).getNameRepresentation() + " with _Data Breach_.");
        EndTurnService.endTurnAndUpdateMap(event, game, player);
    }

    @ButtonHandler("netrunnersDataBreachDeclineTech")
    public static void declineDataBreachTechnology(Game game, Player player, ButtonInteractionEvent event) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough(DATA_BREACH)) {
            return;
        }
        String techId = game.getStoredValue(NetrunnersAbilitiesHandler.PROXY_TECH + player.getFaction());
        NetrunnersAbilitiesHandler.clearProxyNetwork(game, player);
        ButtonHelper.deleteMessage(event);
        if (!techId.isEmpty() && Mapper.getTech(techId) != null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " declined to use _Data Breach_ and lost temporary access to "
                            + Mapper.getTech(techId).getNameRepresentation() + ".");
        }
        EndTurnService.endTurnAndUpdateMap(event, game, player);
    }

    private static List<Button> getDataBreachPlacementButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player other : game.getRealPlayersExcludingThis(player)) {
            for (String breakthroughId : other.getBreakthroughIDs()) {
                BreakthroughModel breakthrough = Mapper.getBreakthrough(breakthroughId);
                if (breakthrough == null) {
                    continue;
                }
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "netrunnersDataBreach_" + other.getFaction() + "_"
                                + breakthrough.getAlias(),
                        getDataBreachBreakthroughLabel(breakthrough)));
            }
        }
        return buttons;
    }

    @ButtonHandler("netrunnersDataBreach_")
    public static void resolveDataBreachPlacement(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough("netrunnersbt")) {
            return;
        }
        List<Button> buttons = getDataBreachPlacementButtons(game, player);
        String message =
                player.getRepresentationUnfogged() + ", please choose the breakthrough for your _Data Breach_ token.";
        String buttonPrefix = player.factionButtonChecker() + "netrunnersDataBreach_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) return;
        String[] parts = buttonID.replace("netrunnersDataBreach_", "").split("_", 2);
        if (parts.length != 2) return;
        Player other = game.getPlayerFromColorOrFaction(parts[0]);
        if (other == null || !other.hasBreakthrough(parts[1]) || Mapper.getBreakthrough(parts[1]) == null) return;
        game.setStoredValue("netrunnersDataBreach" + player.getFaction(), other.getFaction() + "~" + parts[1]);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed their _Data Breach_ token on "
                        + Mapper.getBreakthrough(parts[1]).getNameRepresentation() + ".");
    }
}
