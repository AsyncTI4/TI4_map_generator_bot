package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class OblivionTechHandler {
    private static final String MM = "thobliviong";
    private static final String PLAY_DISCARD_COMPONENT_AC = "playMirroredMemoriesAC_";

    public static boolean canUseMirroredMemories(Game game, Player player) {
        return game != null
                && player != null
                && player.hasTechReady(MM)
                && !isCensured(game, player)
                && hasEligibleComponentAction(game);
    }

    public static void offerACPlayFromDiscardButtons(GenericInteractionCreateEvent event, Player player, Game game) {
        if (event == null
                || player == null
                || !player.hasTech(MM)
                || isCensured(game, player)
                || !hasEligibleComponentAction(game)) {
            return;
        }

        List<Button> buttons = getDiscardComponentActionButtons(game, player);
        String buttonPrefix = player.factionButtonChecker() + PLAY_DISCARD_COMPONENT_AC;
        String message = player.getRepresentation()
                + ", choose an action card with a component action to play and purge using _Mirrored Memories_.";
        List<Button> displayedButtons = buttons.size() <= 25
                ? buttons
                : NewStuffHelper.buttonPagination(buttons, null, buttonPrefix, 24, 0, true);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, displayedButtons);
    }

    @ButtonHandler(PLAY_DISCARD_COMPONENT_AC)
    public static void playDiscardComponentAction(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || player != game.getActivePlayer()
                || !player.hasTech(MM)
                || isCensured(game, player)
                || !player.getExhaustedTechs().contains(MM)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getDiscardComponentActionButtons(game, player);
        String buttonPrefix = player.factionButtonChecker() + PLAY_DISCARD_COMPONENT_AC;
        String message = player.getRepresentation()
                + ", choose an action card with a component action to play and purge using _Mirrored Memories_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        int acIndex;
        try {
            acIndex = Integer.parseInt(buttonID.substring(PLAY_DISCARD_COMPONENT_AC.length()));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String acId = ActionCardHelper.getDiscardedAcID(game, acIndex);
        if (!isEligibleComponentAction(game, acId) || !game.pickActionCard(player.getUserID(), acIndex)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "That action card is no longer available.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Integer handIndex = player.getActionCards().get(acId);
        if (handIndex == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not prepare that action card to play.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        // Mark it before the normal resolver offers response windows, including Reverse Engineer.
        game.getDiscardACStatus().put(acId, ActionCardHelper.ACStatus.purged);
        String error =
                ActionCardHelper.playAC(event, game, player, String.valueOf(handIndex), event.getMessageChannel());
        if (error != null) {
            player.removeActionCard(handIndex);
            game.getDiscardActionCards().put(acId, acIndex);
            game.getDiscardACStatus().remove(acId);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), error);
        } else {
            DSHelperBreakthroughs.doLanefirBtCheck(game, player);
            OblivionUnitHandler.doOblivionMechCheck(game, player);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getDiscardComponentActionButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        game.getDiscardActionCards().entrySet().stream()
                .filter(entry -> isEligibleComponentAction(game, entry.getKey()))
                .sorted(Comparator.comparing(
                        entry -> Mapper.getActionCard(entry.getKey()).getName()))
                .forEach(entry -> {
                    ActionCardModel actionCard = Mapper.getActionCard(entry.getKey());
                    buttons.add(Buttons.red(
                            player.factionButtonChecker() + PLAY_DISCARD_COMPONENT_AC + entry.getValue(),
                            "(" + entry.getValue() + ") " + actionCard.getName(),
                            CardEmojis.getACEmoji(game)));
                });
        return buttons;
    }

    private static boolean isEligibleComponentAction(Game game, String acId) {
        if (game == null || acId == null || game.getDiscardACStatus().get(acId) != null) {
            return false;
        }
        ActionCardModel actionCard = Mapper.getActionCard(acId);
        return actionCard != null && "action".equalsIgnoreCase(actionCard.getWindow());
    }

    private static boolean hasEligibleComponentAction(Game game) {
        return game != null
                && game.getDiscardActionCards().keySet().stream()
                        .anyMatch(acId -> isEligibleComponentAction(game, acId));
    }

    private static boolean isCensured(Game game, Player player) {
        return IsPlayerElectedService.isPlayerElected(game, player, "censure")
                || IsPlayerElectedService.isPlayerElected(game, player, "absol_censure");
    }
}
