package ti4.commands.cardsso;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;

public class SOInfo extends SOCardsSubcommandData {
    public SOInfo() {
        super(Constants.INFO, "Sent scored and unscored Secret Objectives to your Cards Info thread");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveMap();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        sendSecretObjectiveInfo(activeGame, player, event);
        sendMessage("SO Info Sent");
    }

    public static void sendSecretObjectiveInfo(Game activeGame, Player player, SlashCommandInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendSecretObjectiveInfo(activeGame, player);
    }
    public static void sendSecretObjectiveInfo(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " used something";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendSecretObjectiveInfo(activeGame, player);
    }

    public static void sendSecretObjectiveInfo(Game activeGame, Player player, ButtonInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " pressed button: " + event.getButton().getLabel();
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendSecretObjectiveInfo(activeGame, player);
    }

    public static void sendSecretObjectiveInfo(Game activeGame, Player player) {
        //SO INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, getSecretObjectiveCardInfo(activeGame, player));

        //BUTTONS
        String secretMsg = "_ _\nClick a button to either score or discard a secret objective";
        List<Button> soButtons = getUnscoredSecretObjectiveButtons(activeGame, player);
        if (soButtons != null && !soButtons.isEmpty()) {
            List<Button> buttons = new ArrayList<>();
            Button scoreB = Button.primary("get_so_score_buttons", "Score an SO");
            Button discardB = Button.danger("get_so_discard_buttons", "Discard an SO");
            ThreadChannel cardsInfoThreadChannel = player.getCardsInfoThread(activeGame);
            buttons.add(scoreB);
            buttons.add(discardB);
            if(activeGame.getActionCards().size() > 130 && ButtonHelper.getButtonsToSwitchWithAllianceMembers(player, activeGame, false).size() > 0){
                buttons.addAll(ButtonHelper.getButtonsToSwitchWithAllianceMembers(player, activeGame, false));
            }
            MessageHelper.sendMessageToChannelWithButtons(cardsInfoThreadChannel, secretMsg, buttons);


        }
    }

    public static String getSecretObjectiveRepresentationShort(String soID) {
        return getSecretObjectiveRepresentationShort(soID, null);
    }

    public static String getSecretObjectiveRepresentationShort(String soID, Integer soUniqueID) {
        StringBuilder sb = new StringBuilder();
        SecretObjectiveModel so = Mapper.getSecretObjective(soID);
        String soName = so.getName();
        sb.append(Emojis.SecretObjective).append("__").append(soName).append("__").append("\n");
        return sb.toString();
    }

    public static String getSecretObjectiveRepresentation(String soID) {
        return getSecretObjectiveRepresentation(soID, null);
    }

    private static String getSecretObjectiveRepresentation(String soID, Integer soUniqueID) {
        StringBuilder sb = new StringBuilder();
        SecretObjectiveModel so = Mapper.getSecretObjective(soID);
        String soName = so.getName();
        String soPhase = so.getPhase();
        String soDescription = so.getText();
        sb.append(Emojis.SecretObjective).append("__**").append(soName).append("**__").append(" *(").append(soPhase).append(" Phase)*: ").append(soDescription).append("\n");
        return sb.toString();
    }

    private static String getSecretObjectiveCardInfo(Game activeGame, Player player) {
        LinkedHashMap<String, Integer> secretObjective = activeGame.getSecretObjective(player.getUserID());
        LinkedHashMap<String, Integer> scoredSecretObjective = new LinkedHashMap<>(activeGame.getScoredSecretObjective(player.getUserID()));
        for (String id : activeGame.getSoToPoList()) {
            scoredSecretObjective.remove(id);
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;

        //SCORED SECRET OBJECTIVES
        sb.append("**Scored Secret Objectives:**").append("\n");
        if (scoredSecretObjective != null) {
            if (scoredSecretObjective.isEmpty()) {
                sb.append("> None");
            } else {
                for (java.util.Map.Entry<String, Integer> so : scoredSecretObjective.entrySet()) {
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + so.getValue(), 4)).append(")`");
                    sb.append(getSecretObjectiveRepresentationShort(so.getKey()));
                    index++;
                }
            }
        }
        sb.append("\n");

        //UNSCORED SECRET OBJECTIVES
        sb.append("**Unscored Secret Objectives:**").append("\n");
        if (secretObjective != null) {
            if (secretObjective.isEmpty()) {
                sb.append("> None");
            } else {
                for (java.util.Map.Entry<String, Integer> so : secretObjective.entrySet()) {
                    Integer idValue = so.getValue();
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + idValue, 4)).append(")`");
                    sb.append(getSecretObjectiveRepresentation(so.getKey()));
                    index++;
                }
            }
        }
        return sb.toString();
    }

    public static List<Button> getUnscoredSecretObjectiveButtons(Game activeGame, Player player) {
        LinkedHashMap<String, Integer> secretObjective = activeGame.getSecretObjective(player.getUserID());
        List<Button> soButtons = new ArrayList<>();
        if (secretObjective != null && !secretObjective.isEmpty()) {
            for (java.util.Map.Entry<String, Integer> so : secretObjective.entrySet()) {
                SecretObjectiveModel so_ = Mapper.getSecretObjective(so.getKey());
                String soName = so_.getName();
                Integer idValue = so.getValue();
                if (soName != null) {
                    soButtons.add(Button.primary(Constants.SO_SCORE_FROM_HAND + idValue, "(" + idValue + ") " + soName).withEmoji(Emoji.fromFormatted(Emojis.SecretObjective)));
                }
            }
        }
        return soButtons;
    }
    public static List<Button> getUnscoredSecretObjectiveDiscardButtons(Game activeGame, Player player) {
        LinkedHashMap<String, Integer> secretObjective = activeGame.getSecretObjective(player.getUserID());
        List<Button> soButtons = new ArrayList<>();
        if (secretObjective != null && !secretObjective.isEmpty()) {
            for (java.util.Map.Entry<String, Integer> so : secretObjective.entrySet()) {
                SecretObjectiveModel so_ = Mapper.getSecretObjective(so.getKey());
                String soName = so_.getName();
                Integer idValue = so.getValue();
                if (soName != null) {
                    soButtons.add(Button.danger("SODISCARD_" + idValue, "(" + idValue + ") " + soName).withEmoji(Emoji.fromFormatted(Emojis.SecretObjective)));
                }
            }
        }
        return soButtons;
    }
}
