package ti4.service;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTwilightsFallActionCards;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.helpers.Units;
import ti4.image.DrawingUtil;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

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
                return Optional.of(ABILITY);
            }
            if (Mapper.getUnit(card) != null) {
                return Optional.of(UNIT);
            }
            LeaderModel leaderModel = Mapper.getLeader(card);
            if (leaderModel != null) {
                if (Constants.AGENT.equalsIgnoreCase(leaderModel.getType())) {
                    return Optional.of(GENOME);
                }
                if (Constants.HERO.equalsIgnoreCase(leaderModel.getType())) {
                    return Optional.of(PARADIGM);
                }
            }
            return Optional.empty();
        }

        static Optional<VeiledCardType> fromString(String str) {
            str = str.toLowerCase();
            if (str.contains("abil") || str.contains("tech")) {
                return Optional.of(ABILITY);
            }
            if (str.contains("unit") || str.contains("upgr")) {
                return Optional.of(UNIT);
            }
            if (str.contains("genome") || str.contains("agent")) {
                return Optional.of(GENOME);
            }
            if (str.contains("para") || str.contains("hero")) {
                return Optional.of(PARADIGM);
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

    public static void addVeiledCard(Player player, String card) {
        setStoredValue(player, getStoredValue(player) + card + "_");
    }

    public static void removeVeiledCard(Player player, String card) {
        setStoredValue(player, getStoredValue(player).replace(card + "_", ""));
    }

    private static Stream<String> getVeiledCards(Player player) {
        return Arrays.stream(getStoredValue(player).split("_")).filter(card -> card.length() > 1);
    }

    private static Stream<String> getVeiledCards(VeiledCardType type, Player player) {
        return getVeiledCards(player).filter(type::matches);
    }

    private static List<String> getVeiledCards(Game game, VeiledCardType type) {
        List<String> veiledCards = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            veiledCards.addAll(getVeiledCards(type, player).toList());
        }
        return veiledCards;
    }

    public static int countVeiledCards(Player player) {
        return (int) getVeiledCards(player).count();
    }

    public static int countVeiledCards(VeiledCardType type, Player player) {
        return (int) getVeiledCards(type, player).count();
    }

    public static int countVeiledCards(Game game, String typeStr) {
        return VeiledCardType.fromString(typeStr)
                .map(type -> getVeiledCards(game, type).size())
                .orElse(0);
    }

    private static Map<VeiledCardType, List<String>> getVeiledCardsByType(Player player) {
        Map<VeiledCardType, List<String>> veiledCardsByType = new HashMap<>();
        for (VeiledCardType cardType : VeiledCardType.values()) {
            veiledCardsByType.put(cardType, new ArrayList<>());
        }

        getVeiledCards(player).forEach(card -> VeiledCardType.fromCard(card)
                .ifPresent(type -> veiledCardsByType.get(type).add(card)));
        return veiledCardsByType;
    }

    private static boolean hasVeiledCard(Player player, String card) {
        return getVeiledCards(player).anyMatch(card::equals);
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

    private static List<Button> getButtonsForChoosingForeignVeiledCard(
            VeiledCardType type, Player activePlayer, Player targetPlayer, String buttonIdFormat) {
        List<Button> buttons = new ArrayList<>();
        List<String> veiledCards =
                new ArrayList<>(getVeiledCards(type, targetPlayer).toList());

        if (veiledCards.isEmpty()) {
            return buttons;
        }
        if (veiledCards.size() == 1) {
            buttons.add(Buttons.gray(String.format(buttonIdFormat, veiledCards.getFirst()), "Veiled " + type));
            return buttons;
        }

        Collections.shuffle(veiledCards);

        StringBuilder msgForTarget = new StringBuilder(
                "Buttons to choose one of your veiled cards were sent to " + activePlayer.getRepresentation()
                        + ". If you want them to know which number is referring to which card (because of some deal you made, or whatever), you may share any of the following information:");
        int i = 1;
        for (String veiledCard : veiledCards) {
            buttons.add(Buttons.red(String.format(buttonIdFormat, veiledCard), "Veiled " + type + " " + i));
            msgForTarget.append(String.format("\nVeiled %s %d: %s", type, i, getRepresentation(type, veiledCard)));
            i++;
        }
        MessageHelper.sendMessageToChannel(targetPlayer.getCardsInfoThread(), msgForTarget.toString());
        return buttons;
    }

    public static List<Button> getVeiledDiscardButtonsForGenophage(Player activePlayer, Player targetPlayer) {
        return getButtonsForChoosingForeignVeiledCard(
                VeiledCardType.GENOME,
                activePlayer,
                targetPlayer,
                "veiled_discard_genome_%s_" + targetPlayer.getFaction());
    }

    public static List<Button> getVeiledGiveButtonsForCoerce(Player sender, Player recipient) {
        return getVeiledCards(VeiledCardType.ABILITY, sender)
                .map(veiledAbility -> Buttons.red(
                        sender.factionButtonChecker() + "coerceStep3_" + recipient.getFaction() + "_" + veiledAbility,
                        getRepresentation(VeiledCardType.ABILITY, veiledAbility)))
                .toList();
    }

    public static List<Button> getVeiledGiveButtonsForTranspose(Player activePlayer, Player targetPlayer) {
        return getVeiledCards(VeiledCardType.ABILITY, activePlayer)
                .map(veiledAbility -> Buttons.red(
                        "transposeStep3_" + targetPlayer.getFaction() + "_" + veiledAbility,
                        getRepresentation(VeiledCardType.ABILITY, veiledAbility)))
                .toList();
    }

    public static List<Button> getVeiledTakeButtonsForTranspose(
            Player activePlayer, Player targetPlayer, String abilityToGive) {
        return getButtonsForChoosingForeignVeiledCard(
                VeiledCardType.ABILITY,
                activePlayer,
                targetPlayer,
                "transposeStep4_" + targetPlayer.getFaction() + "_" + abilityToGive + "_%s");
    }

    private static String getRepresentation(VeiledCardType type, String card) {
        return switch (type) {
            case ABILITY -> Mapper.getTech(card).getName();
            case UNIT -> Mapper.getUnit(card).getName();
            case GENOME, PARADIGM -> Mapper.getLeader(card).getName();
        };
    }

    private static MessageEmbed getRepresentationEmbed(VeiledCardType type, String card) {
        return switch (type) {
            case ABILITY -> Mapper.getTech(card).getRepresentationEmbed();
            case UNIT -> Mapper.getUnit(card).getRepresentationEmbed();
            case GENOME, PARADIGM -> Mapper.getLeader(card).getRepresentationEmbed(false, true, false, false, true);
        };
    }

    public static void sendVeiledButtons(VeiledCardAction action, Player player) {
        for (VeiledCardType type : VeiledCardType.values()) {
            sendVeiledButtons(action, type, player);
        }
    }

    public static void sendVeiledButtons(VeiledCardAction action, String typeStr, Player player) {
        VeiledCardType.fromString(typeStr).ifPresent(type -> sendVeiledButtons(action, type, player));
    }

    private static void sendVeiledButtons(VeiledCardAction action, VeiledCardType type, Player player) {
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
                .ifPresent(action -> Stream.of(VeiledCardType.values())
                        .filter(type -> typeString.equalsIgnoreCase(type.toString()))
                        .findAny()
                        .ifPresent(type -> {
                            if (card.isEmpty()) {
                                sendVeiledButtons(action, type, player);
                            } else if (targetPlayer == null) {
                                doAction(action, type, player, card);
                            } else {
                                doAction(action, type, player, card, targetPlayer);
                            }
                        }));
        ButtonHelper.deleteMessage(event);
    }

    private static void doSilentAction(VeiledCardAction action, VeiledCardType type, Player player, String card) {
        switch (action) {
            case SPLICE, DRAW -> addVeiledCard(player, card);
            case DISCARD -> removeVeiledCard(player, card);
            case UNVEIL -> {
                switch (type) {
                    case ABILITY -> player.addTech(card);
                    case GENOME, PARADIGM -> {
                        player.addLeader(card);
                        player.getLeaderByID(card).ifPresent(leader -> leader.setLocked(false));
                    }
                    case UNIT -> {
                        UnitModel unitModel = Mapper.getUnit(card);
                        String asyncId = unitModel.getAsyncId();
                        if (!"fs".equalsIgnoreCase(asyncId) && !"mf".equalsIgnoreCase(asyncId)) {
                            List<UnitModel> unitsToRemove = player.getUnitsByAsyncID(asyncId).stream()
                                    .filter(unit -> unit.getFaction().isEmpty()
                                            || unit.getUpgradesFromUnitId().isEmpty())
                                    .toList();
                            for (UnitModel u : unitsToRemove) {
                                if (u.getAlias().contains("tf-") || u.getAlias().contains("tk-")) {
                                    List<Button> buttons = new ArrayList<>();
                                    buttons.add(Buttons.green("keepUnit_" + u.getAlias(), "Keep " + u.getName()));
                                    buttons.add(Buttons.red("deleteButtons", "Keep the New Unit"));
                                    MessageHelper.sendMessageToChannel(
                                            player.getCorrectChannel(),
                                            player.getRepresentation() + " you automatically lost the "
                                                    + u.getNameRepresentation()
                                                    + " unit upgrade. If you would like to keep it and lose the newly acquired unit upgrade, please click the green button.",
                                            buttons);
                                }
                                if ("tf-floatingfactory".equalsIgnoreCase(u.getAlias())) {
                                    Game game = player.getGame();
                                    for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(
                                            game, player, Units.UnitType.Spacedock)) {
                                        for (UnitHolder uh : tile.getPlanetUnitHolders()) {
                                            if (uh.getUnitCount(Units.UnitType.Spacedock, player) > 0) {
                                                RemoveUnitService.removeUnit(
                                                        null,
                                                        tile,
                                                        game,
                                                        player,
                                                        uh,
                                                        Units.UnitType.Spacedock,
                                                        1,
                                                        false);
                                                AddUnitService.addUnits(null, tile, game, player.getColor(), "sd");
                                            }
                                        }
                                    }
                                    MessageHelper.sendMessageToChannel(
                                            player.getCorrectChannel(),
                                            player.getRepresentation()
                                                    + " has transformed their Spacedocks into Floating Factories, and so their spacedocks have been moved to the space area.");
                                }
                                player.removeOwnedUnitByID(u.getId());
                            }
                        }
                        player.addOwnedUnitByID(card);
                    }
                }
                removeVeiledCard(player, card);
            }
        }
    }

    public static void doAction(VeiledCardAction action, String typeStr, Player player, String card) {
        VeiledCardType.fromString(typeStr).ifPresent(type -> doAction(action, type, player, card));
    }

    public static void doAction(VeiledCardAction action, VeiledCardType type, Player player, String card) {
        doSilentAction(action, type, player, card);

        String msg;
        switch (action) {
            case SPLICE ->
                msg = player.getRepresentation() + " has spliced a veiled "
                        + type.toString().toLowerCase()
                        + ". They may put it into play with a button in their `#cards-info` thread.";
            case DRAW ->
                msg = player.getRepresentation() + " has secretly drawn a veiled "
                        + type.toString().toLowerCase()
                        + ". They may put it into play with a button in their `#cards-info` thread.";
            case DISCARD ->
                msg = player.getRepresentation() + " has secretly discarded a veiled "
                        + type.toString().toLowerCase() + ".";
            case UNVEIL -> {
                msg = player.getRepresentation() + " has unveiled a "
                        + type.toString().toLowerCase() + ": " + getRepresentation(type, card);
                MessageHelper.sendMessageToChannelWithEmbed(
                        player.getCorrectChannel(), msg, getRepresentationEmbed(type, card));
                return;
            }
            default ->
                msg = player.getRepresentation() + " tried to "
                        + action.toString().toLowerCase() + " a veiled "
                        + type.toString().toLowerCase() + ", but was unable to.";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    public static void doAction(
            VeiledCardAction action, VeiledCardType type, Player activePlayer, String card, Player targetPlayer) {
        doAction(action, type, targetPlayer, card);
        MessageHelper.sendMessageToChannel(
                activePlayer.getCorrectChannel(),
                activePlayer.getRepresentation() + " made " + targetPlayer.getRepresentation() + " "
                        + action.toString().toLowerCase() + " a veiled "
                        + type.toString().toLowerCase() + "!");
        MessageHelper.sendMessageToChannelWithEmbed(
                targetPlayer.getCardsInfoThread(),
                activePlayer.getRepresentation() + " made you "
                        + action.toString().toLowerCase() + " the following veiled "
                        + type.toString().toLowerCase() + ": " + getRepresentation(type, card),
                getRepresentationEmbed(type, card));
    }

    public static void doManipulate(String typeStr, Player activePlayer, String card, Player targetPlayer) {
        VeiledCardType.fromString(typeStr).ifPresent(type -> {
            addVeiledCard(targetPlayer, card);

            String msgPublic = String.format(
                    "%s has been forced to splice a veiled %s. They may put it into play with a button in their `#cards-info` thread.",
                    targetPlayer.getRepresentation(), type.toString().toLowerCase());
            String msgForActivePlayer = String.format(
                    "You have forced %s to splice the veiled %s _'%s'_.",
                    targetPlayer.getRepresentation(), type.toString().toLowerCase(), getRepresentation(type, card));
            String msgForTargetPlayer = String.format(
                    "%s has forced you to splice the veiled %s _'%s'_:",
                    activePlayer.getRepresentation(), type.toString().toLowerCase(), getRepresentation(type, card));

            MessageHelper.sendMessageToChannel(activePlayer.getCorrectChannel(), msgPublic);
            MessageHelper.sendMessageToChannel(activePlayer.getCardsInfoThread(), msgForActivePlayer);
            MessageHelper.sendMessageToChannelWithEmbed(
                    targetPlayer.getCardsInfoThread(), msgForTargetPlayer, getRepresentationEmbed(type, card));
        });
    }

    public static void doCoerce(Player sender, Player recipient, String ability) {
        VeiledCardType type = VeiledCardType.ABILITY;
        boolean givingVeiled = !sender.hasTech(ability);

        sender.removeTech(ability);
        removeVeiledCard(sender, ability);
        addVeiledCard(recipient, ability);

        String msgPublic = String.format(
                "%s has _Coerced_ %s into giving them %s.\n",
                recipient.getRepresentation(),
                sender.getRepresentation(),
                givingVeiled ? "a veiled ability" : ("the ability _'" + getRepresentation(type, ability) + "'_"));
        if (givingVeiled) {
            msgPublic +=
                    "The ability remains veiled and may be put into play with a button in the `#cards-info` thread.";
        } else {
            msgPublic +=
                    "Because receiving abilities counts as gaining them, the ability has been turned face-down as if it had just been drawn. It may be put into play with a button in the `#cards-info` thread.";
        }
        String msgForSender = String.format(
                "You were _Coerced_ by %s into giving them the _'%s'_ ability.",
                recipient.getRepresentation(), getRepresentation(type, ability));
        String msgForRecipient = String.format(
                "You _Coerced_ %s into giving you the _'%s'_ ability:",
                sender.getRepresentation(), getRepresentation(type, ability));

        MessageHelper.sendMessageToChannel(sender.getCorrectChannel(), msgPublic);
        MessageHelper.sendMessageToChannel(sender.getCardsInfoThread(), msgForSender);
        MessageHelper.sendMessageToChannelWithEmbed(
                recipient.getCardsInfoThread(), msgForRecipient, getRepresentationEmbed(type, ability));

        if (!givingVeiled) {
            ButtonHelperTwilightsFallActionCards.checkForSingularityTransfer(sender, recipient, ability);
        }
    }

    public static void doTranspose(
            Player activePlayer, Player targetPlayer, String abilityToGive, String abilityToTake) {
        VeiledCardType type = VeiledCardType.ABILITY;
        boolean givingVeiled = !activePlayer.hasTech(abilityToGive);
        boolean takingVeiled = !targetPlayer.hasTech(abilityToTake);

        activePlayer.removeTech(abilityToGive);
        removeVeiledCard(activePlayer, abilityToGive);
        addVeiledCard(targetPlayer, abilityToGive);

        targetPlayer.removeTech(abilityToTake);
        removeVeiledCard(targetPlayer, abilityToTake);
        addVeiledCard(activePlayer, abilityToTake);

        String msgPublic = String.format(
                "%s has used _Transpose_ to give %s to %s and to take %s from %s in return.\n",
                activePlayer.getRepresentation(),
                givingVeiled ? "a veiled ability" : ("the ability _'" + getRepresentation(type, abilityToGive) + "'_"),
                targetPlayer.getRepresentation(),
                takingVeiled ? "a veiled ability" : ("the ability _'" + getRepresentation(type, abilityToTake) + "'_"),
                targetPlayer.getRepresentation());
        if (givingVeiled && takingVeiled) {
            msgPublic += "Both abilities remain veiled.";
        } else if (givingVeiled) {
            msgPublic += String.format(
                    "The veiled ability %s gave to %s remains veiled. Also, because taking abilities counts as gaining them, the _'%s'_ ability %s took from %s has been turned face-down as if it had just been drawn.",
                    activePlayer.getRepresentation(),
                    targetPlayer.getRepresentation(),
                    getRepresentation(type, abilityToTake),
                    activePlayer.getRepresentation(),
                    targetPlayer.getRepresentation());
        } else if (takingVeiled) {
            msgPublic += String.format(
                    "The veiled ability %s took from %s remains veiled. Also, because being given abilities counts as gaining them, the _'%s'_ ability %s gave to %s has been turned face-down as if it had just been drawn.",
                    activePlayer.getRepresentation(),
                    targetPlayer.getRepresentation(),
                    getRepresentation(type, abilityToGive),
                    activePlayer.getRepresentation(),
                    targetPlayer.getRepresentation());
        } else {
            msgPublic +=
                    "Because taking abilities counts as gaining them, both abilities have been turned face-down as if they had just been drawn.";
        }
        msgPublic +=
                "\nEach involved player may put their new ability into play using a button in their `#cards-info` thread.";

        String msgForActivePlayer = String.format(
                "Using _Transpose_, you've given _'%s'_ to %s and have taken _'%s'_ in return:",
                getRepresentation(type, abilityToGive),
                targetPlayer.getRepresentation(),
                getRepresentation(type, abilityToTake));
        String msgForTargetPlayer = String.format(
                "%s used _Transpose_ to take _'%s'_ from you! However, they did give you _'%s'_ in return:",
                activePlayer.getRepresentation(),
                getRepresentation(type, abilityToTake),
                getRepresentation(type, abilityToGive));

        MessageHelper.sendMessageToChannel(activePlayer.getCorrectChannel(), msgPublic);
        MessageHelper.sendMessageToChannelWithEmbed(
                activePlayer.getCardsInfoThread(), msgForActivePlayer, getRepresentationEmbed(type, abilityToTake));
        MessageHelper.sendMessageToChannelWithEmbed(
                targetPlayer.getCardsInfoThread(), msgForTargetPlayer, getRepresentationEmbed(type, abilityToGive));

        if (!givingVeiled) {
            ButtonHelperTwilightsFallActionCards.checkForSingularityTransfer(activePlayer, targetPlayer, abilityToGive);
        }
        if (!takingVeiled) {
            ButtonHelperTwilightsFallActionCards.checkForSingularityTransfer(targetPlayer, activePlayer, abilityToTake);
        }
    }

    public static void checkForAssigningTelepathic(Game game, Player player) {
        String card = "tf-telepathic";
        if (!hasVeiledCard(player, card)) {
            return;
        }
        String msg = player.getRepresentation()
                + ", you have the option to pre-reveal your veiled _Telepathic_ ability (Zero Token)."
                + " The end of the strategy phase is an awkward timing window for async, so if you intend to reveal it at the end of this strategy phase, it's best to pre-play it now."
                + " Feel free to ignore this message if you don't intend to reveal it any time soon.";
        PrePlayService.sendPrePlayButtons(player, card, msg, "Pre-Reveal Telepathic");
    }

    public static void resolveTelepathicPreset(Game game, Player player) {
        String card = "tf-telepathic";
        if (hasVeiledCard(player, card) && PrePlayService.isAssigned(game, card)) {
            VeiledHeartService.doAction(
                    VeiledHeartService.VeiledCardAction.UNVEIL, VeiledCardType.ABILITY, player, card);
            PrePlayService.unassign(game, card);
        }
        if (player.hasTech(card)) {
            game.setStoredValue("TFTelepathicHolder", player.getFaction());
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
