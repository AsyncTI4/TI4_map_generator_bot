package ti4.service.info;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;
import ti4.service.emoji.CardEmojis;

@UtilityClass
public class SecretObjectiveInfoService {

    public static void sendSecretObjectiveInfo(Game game, Player player, ButtonInteractionEvent event) {
        String headerText = player.getRepresentationUnfogged() + " pressed button: " + event.getButton().getLabel();
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        sendSecretObjectiveInfo(game, player);
    }

    public static void sendSecretObjectiveInfo(Game game, Player player, SlashCommandInteractionEvent event) {
        String headerText = player.getRepresentationUnfogged() + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        sendSecretObjectiveInfo(game, player);
    }

    public static void sendSecretObjectiveInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentationUnfogged() + " used something";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        sendSecretObjectiveInfo(game, player);
    }

    public static void sendSecretObjectiveInfo(Game game, Player player) {
        //SO INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, getSecretObjectiveCardInfo(game, player));

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

                    if (ListPlayerInfoService.getObjectiveThreshold(so.getKey(), game) > 0) {
                        sb.append(getSecretObjectiveRepresentationNoNewLine(so.getKey()));
                        sb.append(" (").append(ListPlayerInfoService.getPlayerProgressOnObjective(so.getKey(), game, player)).append("/").append(ListPlayerInfoService.getObjectiveThreshold(so.getKey(), game)).append(")\n");
                    } else {
                        sb.append(getSecretObjectiveRepresentation(so.getKey()));
                    }
                    index++;
                }
            }
        }
        return sb.toString();
    }

    public static String getSecretObjectiveRepresentationShort(String soID) {
        StringBuilder sb = new StringBuilder();
        SecretObjectiveModel so = Mapper.getSecretObjective(soID);
        String soName = so.getName();
        sb.append(CardEmojis.SecretObjective).append("__").append(soName).append("__").append("\n");
        return sb.toString();
    }

    public static String getSecretObjectiveRepresentationNoNewLine(String soID) {
        return getSecretObjectiveRepresentation(soID, false);
    }

    public static String getSecretObjectiveRepresentation(String soID) {
        return getSecretObjectiveRepresentation(soID, true);
    }

    private static String getSecretObjectiveRepresentation(String soID, boolean newLine) {
        StringBuilder sb = new StringBuilder();
        SecretObjectiveModel so = Mapper.getSecretObjective(soID);
        String soName = so.getName();
        String soPhase = so.getPhase();
        String soDescription = so.getText();
        if (newLine) {
            sb.append(CardEmojis.SecretObjective).append("__**").append(soName).append("**__").append(" *(").append(soPhase).append(" Phase)*: ").append(soDescription).append("\n");
        } else {
            sb.append(CardEmojis.SecretObjective).append("__**").append(soName).append("**__").append(" *(").append(soPhase).append(" Phase)*: ").append(soDescription);
        }
        return sb.toString();
    }
}
