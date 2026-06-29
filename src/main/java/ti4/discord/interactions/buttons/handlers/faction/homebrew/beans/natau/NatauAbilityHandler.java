package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.natau;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.AbilityModel;

@UtilityClass
public class NatauAbilityHandler {
    private static final List<String> DOCTRINE_LIST = List.of(
            "doctrine_wrath",
            "doctrine_strength",
            "doctrine_revelation",
            "doctrine_knowledge",
            "doctrine_discovery",
            "doctrine_stability");
    private static final String SHOW_DOCTRINES = "showNatauDoctrines";
    private static final String DOCTRINE = "doctrine";
    private static final String PARADIGM = "paradigm";
    private static final String DECREE = "natau_decree";
    private static final String CHOOSE_DOCTRINE = "natauChooseDoctrine_";
    private static final String CHOOSE_SWAP_DOCTRINE = "natauChooseSwapDoctrine_";
    private static final String REPLACE_DOCTRINE = "natauReplaceDoctrine_";

    private static boolean hasNatauDoctrinePackage(Player player) {
        return player != null
                && player.hasAbility(DOCTRINE)
                && player.hasAbility(DECREE)
                && player.hasAbility(PARADIGM);
    }

    public static List<AbilityModel> getActiveDoctrinesModels(Player player) {
        if (player == null) {
            return new ArrayList<>();
        }

        List<AbilityModel> activeDoctrines = new ArrayList<>();
        for (String doctrineId : DOCTRINE_LIST) {
            if (!player.hasAbility(doctrineId)) {
                continue;
            }
            if (Mapper.getAbility(doctrineId) == null) {
                continue;
            }

            activeDoctrines.add(Mapper.getAbility(doctrineId));
        }

        return activeDoctrines;
    }

    public static List<AbilityModel> getInactiveDoctrinesModels(Player player) {
        if (player == null) {
            return new ArrayList<>();
        }

        List<AbilityModel> inactiveDoctrines = new ArrayList<>();
        for (String doctrineId : DOCTRINE_LIST) {
            if (player.hasAbility(doctrineId)) {
                continue;
            }
            if (Mapper.getAbility(doctrineId) == null) {
                continue;
            }

            inactiveDoctrines.add(Mapper.getAbility(doctrineId));
        }

        return inactiveDoctrines;
    }

    public static Button getShowDoctrinesButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + SHOW_DOCTRINES, "Show Doctrines");
    }

    @ButtonHandler(SHOW_DOCTRINES)
    public static void showDoctrines(ButtonInteractionEvent event, Game game, Player player) {
        if (event == null || game == null || player == null) {
            return;
        }

        List<AbilityModel> activeDoctrines = getActiveDoctrinesModels(player);
        List<AbilityModel> inactiveDoctrines = getInactiveDoctrinesModels(player);

        StringBuilder sb = new StringBuilder();

        sb.append(player.getRepresentation()).append("## Doctrines\n\n");
        sb.append("**In Play Area**");
        sb.append("\n");
        if (activeDoctrines.isEmpty()) {
            sb.append("None");
            sb.append("\n\n");
        } else {
            for (AbilityModel doctrine : activeDoctrines) {
                sb.append(doctrine.getRepresentation());
                sb.append("\n\n");
            }
        }
        sb.append("**In Reinforcements**");
        sb.append("\n");
        if (inactiveDoctrines.isEmpty()) {
            sb.append("None");
            sb.append("\n\n");
        } else {
            for (AbilityModel doctrine : inactiveDoctrines) {
                sb.append(doctrine.getRepresentation());
                sb.append("\n\n");
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    public static void offerDoctrineSetupButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!hasNatauDoctrinePackage(player)) {
            return;
        }

        int activeDoctrineCount = getActiveDoctrinesModels(player).size();
        int doctrinesRemaining = 2 - activeDoctrineCount;
        if (doctrinesRemaining <= 0) {
            return;
        }

        List<AbilityModel> inactiveDoctrines = getInactiveDoctrinesModels(player);
        List<Button> buttons = new ArrayList<>();
        for (AbilityModel doctrine : inactiveDoctrines) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CHOOSE_DOCTRINE + doctrine.getAlias(), doctrine.getName()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose " + doctrinesRemaining + " doctrine"
                        + (doctrinesRemaining == 1 ? "" : "s") + " to place in your play area:",
                buttons);
    }

    @ButtonHandler(CHOOSE_DOCTRINE)
    public static void resolveChooseDoctrine(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null) {
            return;
        }
        String doctrineId = buttonID.replace(CHOOSE_DOCTRINE, "");

        if (!hasNatauDoctrinePackage(player)
                || !DOCTRINE_LIST.contains(doctrineId)
                || player.hasAbility(doctrineId)
                || Mapper.getAbility(doctrineId) == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Unable to add doctrine. Either an error occurred or the player already has the doctrine in their play area.");
            return;
        }
        if (getActiveDoctrinesModels(player).size() >= 2) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You have already chosen both doctrines for your play area.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.addAbility(doctrineId);
        player.removeExhaustedAbility(doctrineId);
        AbilityModel addedDoctrine = Mapper.getAbility(doctrineId);

        List<AbilityModel> activeDoctrines = getActiveDoctrinesModels(player);
        if (activeDoctrines.size() < 2) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " added " + addedDoctrine.getNameRepresentation()
                            + " to their play area.");
            ButtonHelper.deleteMessage(event);
            offerDoctrineSetupButtons(event, game, player);
            return;
        }

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " added " + addedDoctrine.getNameRepresentation()
                        + " to their play area. Both doctrines have now been chosen.");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveParadigmStartOfStrategy(GenericInteractionCreateEvent event, Game game, Player player) {
        if (event == null || game == null || !hasNatauDoctrinePackage(player)) {
            return;
        }

        List<AbilityModel> activeDoctrines = getActiveDoctrinesModels(player);
        List<AbilityModel> inactiveDoctrines = getInactiveDoctrinesModels(player);
        for (AbilityModel doctrine : activeDoctrines) {
            String doctrineId = doctrine.getAlias();
            player.removeExhaustedAbility(doctrineId);
        }

        if (activeDoctrines.isEmpty()) {
            return;
        }
        if (inactiveDoctrines.isEmpty()) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (AbilityModel doctrine : activeDoctrines) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + CHOOSE_SWAP_DOCTRINE + doctrine.getAlias(), doctrine.getName()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", your doctrines have been readied. Please choose which doctrine to replace:",
                buttons);
    }

    @ButtonHandler(CHOOSE_SWAP_DOCTRINE)
    public static void resolveSwapDoctrine(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null) {
            return;
        }
        String doctrineId = buttonID.replace(CHOOSE_SWAP_DOCTRINE, "");

        if (!hasNatauDoctrinePackage(player) || !DOCTRINE_LIST.contains(doctrineId) || !player.hasAbility(doctrineId)) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "You do not have that doctrine in your play area.");
            return;
        }

        List<AbilityModel> inactiveDoctrines = getInactiveDoctrinesModels(player);
        List<Button> buttons = new ArrayList<>();
        for (AbilityModel doctrine : inactiveDoctrines) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + REPLACE_DOCTRINE + doctrineId + "|" + doctrine.getAlias(),
                    doctrine.getName()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), "Choose which doctrine will be the replacement:", buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(REPLACE_DOCTRINE)
    public static void resolveReplaceDoctrine(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null) {
            return;
        }
        String payload = buttonID.substring(REPLACE_DOCTRINE.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            return;
        }
        String activeDoctrineId = parts[0];
        String inactiveDoctrineId = parts[1];

        if (!hasNatauDoctrinePackage(player)
                || !DOCTRINE_LIST.contains(activeDoctrineId)
                || !DOCTRINE_LIST.contains(inactiveDoctrineId)
                || Mapper.getAbility(activeDoctrineId) == null
                || Mapper.getAbility(inactiveDoctrineId) == null) {
            return;
        }
        if (player.hasAbility(inactiveDoctrineId)) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "You already have that doctrine in your play area.");
            return;
        }
        if (!player.hasAbility(activeDoctrineId)) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "You no longer have that doctrine in your play area.");
            return;
        }

        player.removeAbility(activeDoctrineId);
        player.removeExhaustedAbility(activeDoctrineId);
        player.addAbility(inactiveDoctrineId);
        player.removeExhaustedAbility(inactiveDoctrineId);

        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getRepresentation() + " replaced "
                        + Mapper.getAbility(activeDoctrineId).getNameRepresentation() + " with "
                        + Mapper.getAbility(inactiveDoctrineId).getNameRepresentation() + ".");

        ButtonHelper.deleteMessage(event);
    }
}
