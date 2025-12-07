package ti4.service;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Storage;
import ti4.helpers.twilightsfall.TfCardType;
import ti4.image.DrawingUtil;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.*;
import ti4.message.MessageHelper;

@UtilityClass
public class VeiledHeartService {
    private static final String VEILED_CARDS = "veiledCards";

    public enum VeiledCardAction {
        DISCARD,
        DRAW,
        SPLICE,
        SEND,
        UNVEIL
    }

    private static String toTitleCase(String s) {
        return StringUtils.capitalize(s.toLowerCase());
    }

    private static String getKey(Player player) {
        return VEILED_CARDS + player.getFaction();
    }

    private static String getStoredValue(Player player) {
        return player.getGame().getStoredValue(getKey(player));
    }

    private static void setStoredValue(Player player, String value) {
        player.getGame().setStoredValue(getKey(player), value);
    }

    private static Stream<String> getVeiledCards(Player player) {
        return Arrays.stream(getStoredValue(player).split("_"));
    }

    private static Stream<String> getVeiledCards(TfCardType type, Player player) {
        return getVeiledCards(player).filter(type::matches);
    }

    private static Map<TfCardType, List<String>> getVeiledCardsByType(Player player) {
        Map<TfCardType, List<String>> veiledCardsByType = new HashMap<>();
        for (TfCardType cardType : TfCardType.values()) {
            veiledCardsByType.put(cardType, new ArrayList<>());
        }

        getVeiledCards(player).forEach(card -> TfCardType.fromCard(card)
                .ifPresent(type -> veiledCardsByType.get(type).add(card)));
        return veiledCardsByType;
    }

    private static boolean hasVeiledCard(TfCardType type, Player player) {
        return getVeiledCards(player).anyMatch(type::matches);
    }

    public static List<Button> getVeiledDiscardButtonsForRedDeploy(Player player) {
        return Stream.of(TfCardType.ABILITY, TfCardType.GENOME)
                .filter(type -> hasVeiledCard(type, player))
                .map(type -> type.toButton("veiled_discard_" + type, "Veiled " + toTitleCase(type.toString())))
                .toList();
    }

    public static List<Button> getVeiledDiscardButtonsForGenophage(Player activePlayer, Player targetPlayer) {
        List<Button> buttons = new ArrayList<>();
        String buttonIdFormat = "veiled_discard_genome_%d_" + targetPlayer.getFaction();
        long cardCount = getVeiledCards(TfCardType.GENOME, targetPlayer).count();
        for (long i = 0; i < cardCount; i++) {
            buttons.add(Buttons.gray(String.format(buttonIdFormat, i), "Veiled Genome " + (i + 1)));
        }
        return buttons;
    }

    private static String getRepresentation(TfCardType type, String card) {
        return switch (type) {
            case ABILITY -> Mapper.getTech(card).getName();
            case UNIT -> Mapper.getUnit(card).getName();
            case GENOME, PARADIGM -> Mapper.getLeader(card).getName();
        };
    }

    public static void sendVeiledButtons(VeiledCardAction action, Player player) {
        for (TfCardType type : TfCardType.values()) {
            sendVeiledButtons(action, type, player);
        }
    }

    public static void sendVeiledButtons(VeiledCardAction action, String typeStr, Player player) {
        TfCardType.fromString(typeStr).ifPresent(type -> sendVeiledButtons(action, type, player));
    }

    private static void sendVeiledButtons(VeiledCardAction action, TfCardType type, Player player) {
        String buttonIdPrefix = "veiled_" + action + "_" + type + "_";
        List<Button> buttons = new ArrayList<>(getVeiledCards(type, player)
                .map(card -> type.toButton(buttonIdPrefix + card, getRepresentation(type, card)))
                .toList());
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + " select a veiled "
                        + type.toString().toLowerCase() + " to "
                        + action.toString().toLowerCase() + ":",
                buttons);
    }

    @ButtonHandler("veiled")
    public static void veiledButton(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String[] splitID = buttonID.split("_");
        String actionString = splitID[1];
        String typeString = splitID[2];
        String card = splitID.length > 3 ? splitID[3] : "";
        Player targetPlayer = splitID.length > 4 ? game.getPlayerFromColorOrFaction(splitID[4]) : null;
        Stream.of(VeiledCardAction.values())
                .filter(action -> actionString.equalsIgnoreCase(action.toString()))
                .findAny()
                .ifPresent(action -> Stream.of(TfCardType.values())
                        .filter(type -> typeString.equalsIgnoreCase(type.toString()))
                        .findAny()
                        .ifPresent(type -> {
                            if (card.isEmpty()) {
                                sendVeiledButtons(action, type, player);
                            } else if (targetPlayer == null) {
                                doAction(action, type, player, card);
                            } else {
                                doAction(action, type, player, Integer.parseInt(card), targetPlayer);
                            }
                        }));
        ButtonHelper.deleteMessage(event);
    }

    public static void doAction(VeiledCardAction action, String typeStr, Player player, String card) {
        TfCardType.fromString(typeStr).ifPresent(type -> doAction(action, type, player, card));
    }

    public static void doAction(VeiledCardAction action, TfCardType type, Player player, String card) {
        String msg;
        switch (action) {
            case VeiledCardAction.DRAW -> {
                setStoredValue(player, getStoredValue(player) + card + "_");
                msg = player.getRepresentation() + " has secretly drawn a veiled "
                        + type.toString().toLowerCase()
                        + ". They may put it into play with a button in their cards info.";
            }
            case VeiledCardAction.DISCARD -> {
                setStoredValue(player, getStoredValue(player).replace(card + "_", ""));
                msg = player.getRepresentation() + " has secretly discarded a veiled "
                        + type.toString().toLowerCase() + ".";
            }
            default ->
                msg = player.getRepresentation() + " tried to "
                        + action.toString().toLowerCase() + " a veiled "
                        + type.toString().toLowerCase() + ", but was unable to.";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static void doAction(
            VeiledCardAction action, TfCardType type, Player activePlayer, int cardIndex, Player targetPlayer) {
        List<String> cards = getVeiledCards(type, targetPlayer).toList();
        if (cards.size() <= cardIndex) {
            return;
        }
        doAction(action, type, targetPlayer, cards.get(cardIndex));
        MessageHelper.sendMessageToChannel(
                activePlayer.getCorrectChannel(),
                activePlayer.getRepresentation() + " made " + targetPlayer.getRepresentation() + " "
                        + action.toString().toLowerCase() + " a veiled "
                        + type.toString().toLowerCase() + "!");
    }

    public static void showVeiledAndRemaining(Game game, String typeStr, Player player, ButtonInteractionEvent event) {}

    public static int veiledField(Graphics graphics, int x, int y, TfCardType type, int deltaX, Player player) {
        graphics.setColor(Color.LIGHT_GRAY);
        graphics.setFont(Storage.getFont18());
        String text = "VEILED\n" + type.toString();
        long cardCount = getVeiledCards(type, player).count();
        for (long l = 0; l < cardCount; ++l) {
            DrawingUtil.drawOneOrTwoLinesOfTextVertically(graphics, text, x + deltaX + 7, y + 116, 116);
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            deltaX += 48;
        }
        return deltaX;
    }
}
