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
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.image.DrawingUtil;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.*;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;

@UtilityClass
public class VeiledHeartService {
    private static final String VEILED_CARDS = "veiledCards";

    public enum VeiledCardType {
        ABILITY,
        UNIT,
        GENOME,
        PARADIGM;

        static Optional<VeiledCardType> fromCard(String card) {
            if (Mapper.getTech(card) != null) {
                return Optional.of(VeiledCardType.ABILITY);
            }
            if (Mapper.getUnit(card) != null) {
                return Optional.of(VeiledCardType.UNIT);
            }
            LeaderModel leaderModel = Mapper.getLeader(card);
            if (leaderModel != null) {
                if (Constants.AGENT.equalsIgnoreCase(leaderModel.getType())) {
                    return Optional.of(VeiledCardType.GENOME);
                }
                if (Constants.HERO.equalsIgnoreCase(leaderModel.getType())) {
                    return Optional.of(VeiledCardType.PARADIGM);
                }
            }
            return Optional.empty();
        }

        boolean matches(String card) {
            return Optional.of(this).equals(fromCard(card));
        }

        Button toButton(String buttonId, String buttonLabel) {
            return switch (this) {
                case ABILITY -> Buttons.green(buttonId, buttonLabel);
                case UNIT -> Buttons.gray(buttonId, buttonLabel);
                case GENOME -> Buttons.blue(buttonId, buttonLabel);
                case PARADIGM -> Buttons.red(buttonId, buttonLabel);
            };
        }
    }

    private enum VeiledCardAction {
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

    private static Stream<String> getVeiledCards(VeiledCardType type, Player player) {
        return getVeiledCards(player).filter(type::matches);
    }

    private static Map<VeiledCardType, List<String>> getVeiledCardsByType(Player player) {
        Map<VeiledCardType, List<String>> veiledCardsByType = new HashMap<>();
        for (VeiledCardType cardType : VeiledCardType.values()) {
            veiledCardsByType.put(cardType, new ArrayList<>());
        }

        getVeiledCards(player).forEach(card -> {
            VeiledCardType.fromCard(card).ifPresent(type -> {
                veiledCardsByType.get(type).add(card);
            });
        });
        return veiledCardsByType;
    }

    private static boolean hasVeiledCard(VeiledCardType type, Player player) {
        return getVeiledCards(player).anyMatch(type::matches);
    }

    public static List<Button> getVeiledDiscardButtonsForRedDeploy(Player player) {
        return Stream.of(VeiledCardType.ABILITY, VeiledCardType.GENOME)
                .filter(type -> hasVeiledCard(type, player))
                .map(type -> type.toButton("veiled_discard_" + type, "Veiled " + toTitleCase(type.toString())))
                .toList();
    }

    private static String getRepresentation(VeiledCardType type, String card) {
        return switch (type) {
            case ABILITY -> Mapper.getTech(card).getName();
            case UNIT -> Mapper.getUnit(card).getName();
            case GENOME, PARADIGM -> Mapper.getLeader(card).getName();
        };
    }

    private static void sendVeiledButtons(VeiledCardAction action, VeiledCardType type, Player player) {
        String buttonIdPrefix = "veiled_" + action + "_" + type + "_";
        List<Button> buttons = new ArrayList<>(getVeiledCards(type, player)
                .map(card -> type.toButton(buttonIdPrefix + card, getRepresentation(type, card)))
                .toList());
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
        Stream.of(VeiledCardAction.values())
                .filter(action -> actionString.equalsIgnoreCase(action.toString()))
                .findAny()
                .ifPresent(action -> {
                    Stream.of(VeiledCardType.values())
                            .filter(type -> typeString.equalsIgnoreCase(type.toString()))
                            .findAny()
                            .ifPresent(type -> {
                                if (card.isEmpty()) {
                                    sendVeiledButtons(action, type, player);
                                } else {
                                    doAction(action, type, player, card);
                                }
                            });
                });
        ButtonHelper.deleteMessage(event);
    }

    private static void doAction(VeiledCardAction action, VeiledCardType type, Player player, String card) {
        if (action.equals(VeiledCardAction.DISCARD)) {
            setStoredValue(player, getStoredValue(player).replace(card + "_", ""));

            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has secretly discarded a veiled "
                            + type.toString().toLowerCase() + ".");
        }
    }

    public static int veiledField(Graphics graphics, int x, int y, VeiledCardType type, int deltaX, Player player) {
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
