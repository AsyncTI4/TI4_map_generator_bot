package ti4.commands2.cardsso;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.status.ListPlayerInfoButton;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;

public class SOInfo extends GameStateSubcommand {

    public SOInfo() {
        super(Constants.INFO, "Sent scored and unscored Secret Objectives to your Cards Info thread", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        sendSecretObjectiveInfo(getGame(), getPlayer(), event);
        MessageHelper.sendMessageToEventChannel(event, "SO Info Sent");
    }

    public static void sendSecretObjectiveInfo(Game game, Player player, SlashCommandInteractionEvent event) {
        String headerText = player.getRepresentationUnfogged() + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendSecretObjectiveInfo(game, player);
    }

    public static void sendSecretObjectiveInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentationUnfogged() + " used something";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendSecretObjectiveInfo(game, player);
    }

    @ButtonHandler("refreshSOInfo")
    public static void sendSecretObjectiveInfo(Game game, Player player, ButtonInteractionEvent event) {
        String headerText = player.getRepresentationUnfogged() + " pressed button: " + event.getButton().getLabel();
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendSecretObjectiveInfo(game, player);
    }

    public static void sendSecretObjectiveInfo(Game game, Player player) {
        //SO INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, getSecretObjectiveCardInfo(game, player));

        if (player.getSecretsUnscored().isEmpty()) return;

        // SCORE/DISCARD BUTTONS
        String secretMsg = "_ _\nClick a button to either score or discard a secret objective";
        List<Button> buttons = new ArrayList<>();
        Button scoreB = Buttons.blue("get_so_score_buttons", "Score an SO");
        Button discardB = Buttons.red("get_so_discard_buttons", "Discard an SO");
        ThreadChannel cardsInfoThreadChannel = player.getCardsInfoThread();
        buttons.add(scoreB);
        buttons.add(discardB);
        MessageHelper.sendMessageToChannelWithButtons(cardsInfoThreadChannel, secretMsg, buttons);
    }

    public static String getSecretObjectiveRepresentationShort(String soID) {
        StringBuilder sb = new StringBuilder();
        SecretObjectiveModel so = Mapper.getSecretObjective(soID);
        String soName = so.getName();
        sb.append(Emojis.SecretObjective).append("__").append(soName).append("__").append("\n");
        return sb.toString();
    }

    public static String getSecretObjectiveRepresentation(String soID) {
        return getSecretObjectiveRepresentation(soID, true);
    }

    public static String getSecretObjectiveRepresentationNoNewLine(String soID) {
        return getSecretObjectiveRepresentation(soID, false);
    }

    private static String getSecretObjectiveRepresentation(String soID, boolean newLine) {
        StringBuilder sb = new StringBuilder();
        SecretObjectiveModel so = Mapper.getSecretObjective(soID);
        String soName = so.getName();
        String soPhase = so.getPhase();
        String soDescription = so.getText();
        if (newLine) {
            sb.append(Emojis.SecretObjective).append("__**").append(soName).append("**__").append(" *(").append(soPhase).append(" Phase)*: ").append(soDescription).append("\n");
        } else {
            sb.append(Emojis.SecretObjective).append("__**").append(soName).append("**__").append(" *(").append(soPhase).append(" Phase)*: ").append(soDescription);
        }
        return sb.toString();
    }

    private static String getSecretObjectiveCardInfo(Game game, Player player) {
        Map<String, Integer> secretObjective = player.getSecrets();
        Map<String, Integer> scoredSecretObjective = new LinkedHashMap<>(player.getSecretsScored());
        for (String id : game.getSoToPoList()) {
            scoredSecretObjective.remove(id);
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;

        //SCORED SECRET OBJECTIVES
        sb.append("**Scored Secret Objectives (").append(player.getSoScored()).append("/").append(player.getMaxSOCount()).append("):**").append("\n");
        if (scoredSecretObjective.isEmpty()) {
            sb.append("> None");
        } else {
            for (Map.Entry<String, Integer> so : scoredSecretObjective.entrySet()) {
                sb.append("`").append(index).append(".").append(Helper.leftpad("(" + so.getValue(), 4)).append(")`");
                sb.append(getSecretObjectiveRepresentationShort(so.getKey()));
                index++;
            }
        }
        sb.append("\n");

        //UNSCORED SECRET OBJECTIVES
        sb.append("**Unscored Secret Objectives:**").append("\n");
        if (secretObjective != null) {
            if (secretObjective.isEmpty()) {
                sb.append("> None");
            } else {
                for (Map.Entry<String, Integer> so : secretObjective.entrySet()) {
                    Integer idValue = so.getValue();
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + idValue, 4)).append(")`");

                    if (ListPlayerInfoButton.getObjectiveThreshold(so.getKey(), game) > 0) {
                        sb.append(getSecretObjectiveRepresentationNoNewLine(so.getKey()));
                        sb.append(" (").append(ListPlayerInfoButton.getPlayerProgressOnObjective(so.getKey(), game, player)).append("/").append(ListPlayerInfoButton.getObjectiveThreshold(so.getKey(), game)).append(")\n");
                    } else {
                        sb.append(getSecretObjectiveRepresentation(so.getKey()));
                    }
                    index++;
                }
            }
        }
        return sb.toString();
    }

    public static List<Button> getUnscoredSecretObjectiveButtons(Player player) {
        Map<String, Integer> secretObjectives = player.getSecrets();
        List<Button> soButtons = new ArrayList<>();
        if (secretObjectives != null && !secretObjectives.isEmpty()) {
            for (Map.Entry<String, Integer> so : secretObjectives.entrySet()) {
                SecretObjectiveModel so_ = Mapper.getSecretObjective(so.getKey());
                String soName = so_.getName();
                Integer idValue = so.getValue();
                if (soName != null) {
                    soButtons.add(Buttons.blue(Constants.SO_SCORE_FROM_HAND + idValue, "(" + idValue + ") " + soName, Emojis.SecretObjective));
                }
            }
        }
        return soButtons;
    }

    public static void sendSODiscardButtons(Player player) {
        sendSODiscardButtons(player, "");
    }

    public static void sendSODiscardButtons(Player player, String suffix) {
        List<Button> buttons = getSODiscardButtonsWithSuffix(player, suffix);
        String message = "Use buttons to discard a Secret Objective:";
        if ("redraw".equals(suffix)) {
            message += "\n> - A new secret will be automatically drawn for you";
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
    }

    public static List<Button> getUnscoredSecretObjectiveDiscardButtons(Player player) {
        return getSODiscardButtonsWithSuffix(player, "");
    }

    public static List<Button> getSODiscardButtonsWithSuffix(Player player, String suffix) {
        Map<String, Integer> secretObjectives = player.getSecrets();
        List<Button> soButtons = new ArrayList<>();
        if (secretObjectives != null && !secretObjectives.isEmpty()) {
            for (Map.Entry<String, Integer> so : secretObjectives.entrySet()) {
                SecretObjectiveModel so_ = Mapper.getSecretObjective(so.getKey());
                String soName = so_.getName();
                Integer idValue = so.getValue();
                if (soName != null) {
                    soButtons.add(Buttons.red("discardSecret_" + idValue + suffix, "(" + idValue + ") " + soName, Emojis.SecretObjective));
                }
            }
        }
        return soButtons;
    }
}
