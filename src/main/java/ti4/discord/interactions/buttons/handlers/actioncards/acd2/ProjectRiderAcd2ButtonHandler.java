package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
class ProjectRiderAcd2ButtonHandler {

    private static final String PROJECT_RIDER_SELECTED_CARDS_PREFIX = "projectRiderSelectedCards_";
    private static final String PROJECT_RIDER_PICK_PREFIX = "projectRiderPickFromDiscard_";
    private static final int PROJECT_RIDER_MAX_SELECTIONS = 2;

    @ButtonHandler("resolveProjectRider")
    public static void resolveProjectRider(Player player, Game game, ButtonInteractionEvent event) {
        game.setStoredValue(getProjectRiderSelectionKey(player), "");
        ButtonHelper.deleteMessage(event);

        if (getProjectRiderSelectableCards(game).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " has no action cards in the discard pile to choose for _Project Rider_.");
            return;
        }

        sendProjectRiderSelectionButtons(player, game, new ArrayList<>());
    }

    @ButtonHandler("projectRiderCardPick_")
    public static void resolveProjectRiderCardPick(Player player, Game game, ButtonInteractionEvent event) {
        ActionCardHelper.pickACardFromDiscardStep1(
                game,
                player,
                PROJECT_RIDER_PICK_PREFIX,
                player.getRepresentationUnfogged()
                        + ", choose an action card from the discard pile for _Project Rider_.",
                player.getCorrectChannel());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("projectRiderPickFromDiscard_")
    public static void resolveProjectRiderPickFromDiscard(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String acId = buttonID.replace(PROJECT_RIDER_PICK_PREFIX, "");
        List<String> selectedCards = new ArrayList<>(getProjectRiderSelections(game, player));
        ButtonHelper.deleteMessage(event);
        if (selectedCards.contains(acId)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " already selected _"
                            + Mapper.getActionCard(acId).getName() + "_ for _Project Rider_.");
            sendProjectRiderSelectionButtons(player, game, selectedCards);
            return;
        }
        if (!isProjectRiderCardSelectable(game, acId)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " cannot select that action card because it is no longer in the discard pile.");
            sendProjectRiderSelectionButtons(player, game, selectedCards);
            return;
        }
        if (selectedCards.size() >= PROJECT_RIDER_MAX_SELECTIONS) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " already selected the maximum number of cards for _Project Rider_.");
            sendProjectRiderSelectionButtons(player, game, selectedCards);
            return;
        }

        selectedCards.add(acId);
        setProjectRiderSelections(game, player, selectedCards);
        sendProjectRiderSelectionButtons(player, game, selectedCards);
    }

    @ButtonHandler("projectRiderDone")
    public static void resolveProjectRiderDone(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        sendProjectRiderSelectionSummary(player, getProjectRiderSelections(game, player));
    }

    @ButtonHandler("resolveProjectRiderReward")
    public static void resolveProjectRiderReward(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        List<String> selectedCards = new ArrayList<>(getProjectRiderSelections(game, player));
        game.setStoredValue(getProjectRiderSelectionKey(player), "");
        if (selectedCards.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " had no action cards selected for _Project Rider_.");
            return;
        }

        List<String> retrievedCards = new ArrayList<>();
        List<String> unavailableCards = new ArrayList<>();
        for (String acId : selectedCards) {
            Integer acIndex = game.getDiscardActionCards().get(acId);
            if (acIndex == null
                    || !isProjectRiderCardSelectable(game, acId)
                    || !game.pickActionCard(player.getUserID(), acIndex)) {
                unavailableCards.add(Mapper.getActionCard(acId).getName());
                continue;
            }
            retrievedCards.add(Mapper.getActionCard(acId).getName());
        }

        if (!retrievedCards.isEmpty()) {
            ActionCardHelper.sendActionCardInfo(game, player);
            ButtonHelper.checkACLimit(game, player);
        }

        StringBuilder message =
                new StringBuilder(player.getRepresentationUnfogged()).append(" resolved _Project Rider_.");
        if (retrievedCards.isEmpty()) {
            message.append(" None of the selected action cards were still available in the discard pile.");
        } else {
            message.append(" Retrieved ")
                    .append(formatProjectRiderCardNames(retrievedCards))
                    .append(" from the discard pile.");
        }
        if (!unavailableCards.isEmpty()) {
            message.append(" These selected cards were unavailable: ")
                    .append(formatProjectRiderCardNames(unavailableCards))
                    .append(".");
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
    }

    private static void sendProjectRiderSelectionButtons(Player player, Game game, List<String> selectedCards) {
        boolean canPickMore = selectedCards.size() < PROJECT_RIDER_MAX_SELECTIONS
                && !getProjectRiderSelectableCards(game).isEmpty();

        List<Button> buttons = new ArrayList<>();
        if (canPickMore) {
            buttons.add(Buttons.green(player.factionButtonChecker() + "projectRiderCardPick_1", "Pick a Card"));
        }
        buttons.add(Buttons.blue(player.factionButtonChecker() + "projectRiderDone", "Done"));

        StringBuilder message = new StringBuilder(player.getRepresentationUnfogged());
        if (canPickMore) {
            message.append(", choose up to ")
                    .append(PROJECT_RIDER_MAX_SELECTIONS)
                    .append(" action cards in the discard pile for _Project Rider_, then click Done.");
        } else {
            message.append(
                    ", you have selected the maximum number of cards for _Project Rider_. Click Done to finish.");
        }
        if (!selectedCards.isEmpty()) {
            message.append(" Selected so far: ")
                    .append(formatProjectRiderCardIds(selectedCards))
                    .append('.');
        }

        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message.toString(), buttons);
    }

    private static void sendProjectRiderSelectionSummary(Player player, List<String> selectedCards) {
        String summary = selectedCards.isEmpty()
                ? "selected no action cards"
                : "selected " + formatProjectRiderCardIds(selectedCards);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " " + summary + " for _Project Rider_.");
    }

    private static List<String> getProjectRiderSelections(Game game, Player player) {
        String storedValue = game.getStoredValue(getProjectRiderSelectionKey(player));
        if (storedValue.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(storedValue.split(",")));
    }

    private static void setProjectRiderSelections(Game game, Player player, List<String> selectedCards) {
        game.setStoredValue(getProjectRiderSelectionKey(player), String.join(",", selectedCards));
    }

    private static String getProjectRiderSelectionKey(Player player) {
        return PROJECT_RIDER_SELECTED_CARDS_PREFIX + player.getUserID();
    }

    private static List<String> getProjectRiderSelectableCards(Game game) {
        return game.getDiscardActionCards().keySet().stream()
                .filter(acId -> isProjectRiderCardSelectable(game, acId))
                .toList();
    }

    private static boolean isProjectRiderCardSelectable(Game game, String acId) {
        return game.getDiscardActionCards().containsKey(acId)
                && game.getDiscardACStatus().get(acId) == null;
    }

    private static String formatProjectRiderCardIds(List<String> actionCards) {
        return formatProjectRiderCardNames(actionCards.stream()
                .map(acId -> Mapper.getActionCard(acId).getName())
                .toList());
    }

    private static String formatProjectRiderCardNames(List<String> actionCards) {
        return actionCards.stream().map(name -> "_" + name + "_").collect(java.util.stream.Collectors.joining(", "));
    }
}
