package ti4.commands.cardsac;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class ACInfo extends ACCardsSubcommandData {
    public ACInfo() {
        super(Constants.INFO + 2, "Send Action Cards to your Cards Info thread");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        sendActionCardInfo(activeGame, player, event);
        sendMessage("AC Info Sent");
    }

    public static void sendActionCardInfo(Game activeGame, Player player, SlashCommandInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " used `" + event.getCommandString() + "`";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendActionCardInfo(activeGame, player);
        sendTrapCardInfo(activeGame, player);
    }

    public static void sendActionCardInfo(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " used something";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendActionCardInfo(activeGame, player);
        sendTrapCardInfo(activeGame, player);
    }

    private static void sendTrapCardInfo(Game activeGame, Player player) {
        if (player.hasAbility("cunning") || player.hasAbility("subterfuge")) { //Lih-zo trap abilities
            MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, getTrapCardInfo(activeGame, player));
        }
    }

    private static String getTrapCardInfo(Game activeGame, Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("_ _\n");


        //ACTION CARDS
        sb.append("**Trap Cards:**").append("\n");
        int index = 1;

        LinkedHashMap<String, Integer> trapCards = player.getTrapCards();
        LinkedHashMap<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
        if (trapCards != null) {
            if (trapCards.isEmpty()) {
                sb.append("> None");
            } else {
                for (java.util.Map.Entry<String, Integer> trapCard : trapCards.entrySet()) {
                    Integer value = trapCard.getValue();
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + value, 4)).append(")`");
                    sb.append(getTrapCardRepresentation(trapCard.getKey(), trapCardsPlanets));
                    index++;
                }
            }
        }

        return sb.toString();
    }

    public static String getTrapCardRepresentation(String trapID, LinkedHashMap<String, String> trapCardsPlanets) {
        StringBuilder sb = new StringBuilder();
        java.util.Map<String, String> dsHandcards = Mapper.getDSHandcards();
        String info = dsHandcards.get(trapID);
        if (info == null) {
            return "";
        }
        String[] split = info.split(";");
        //String trapType = split[0];
        String trapName = split[1];
        String trapText = split[2];
        String planet = trapCardsPlanets.get(trapID);
        sb.append("__**").append(trapName).append("**__").append(" - ").append(trapText);
        if (planet != null) {
            java.util.Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
            String representation = planetRepresentations.get(planet);
            if (representation == null) {
                representation = planet;
            }
            sb.append("__**");
            sb.append(" Planet: ");
            sb.append(representation);
            sb.append("**__");
        }
        sb.append("\n");
        return sb.toString();
    }

    public static void sendActionCardInfo(Game activeGame, Player player, ButtonInteractionEvent event) {
        String headerText = Helper.getPlayerRepresentation(player, activeGame) + " pressed button: " + event.getButton().getLabel();
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendActionCardInfo(activeGame, player);
        sendTrapCardInfo(activeGame, player);
    }

    public static void sendActionCardInfo(Game activeGame, Player player) {
        //AC INFO
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, getActionCardInfo(activeGame, player));

        //BUTTONS
        String secretScoreMsg = "_ _\nClick a button below to play an Action Card";
        List<Button> acButtons = getPlayActionCardButtons(activeGame, player);
        if (acButtons != null && !acButtons.isEmpty()) {
            
            List<MessageCreateData> messageList = MessageHelper.getMessageCreateDataObjects(secretScoreMsg, acButtons);
            ThreadChannel cardsInfoThreadChannel = player.getCardsInfoThread(activeGame);
            for (MessageCreateData message : messageList) {
                cardsInfoThreadChannel.sendMessage(message).queue();
            }
        }

        sendTrapCardInfo(activeGame, player);
    }

    private static String getActionCardInfo(Game activeGame, Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("_ _\n");


        //ACTION CARDS
        sb.append("**Action Cards:**").append("\n");
        int index = 1;

        LinkedHashMap<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null) {
            if (actionCards.isEmpty()) {
                sb.append("> None");
            } else {
                for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                    Integer value = ac.getValue();
                    ActionCardModel actionCard = Mapper.getActionCard(ac.getKey());
                    
                    sb.append("`").append(index).append(".").append(Helper.leftpad("(" + value, 4)).append(")`");
                    if(actionCard == null){
                        sb.append("Something broke here");
                    }else{
                        sb.append(actionCard.getRepresentation());
                    }
                    
                    index++;
                }
            }
        }

        return sb.toString();
    }

    private static List<Button> getPlayActionCardButtons(Game activeGame, Player player) {
        List<Button> acButtons = new ArrayList<>();
        LinkedHashMap<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCardName(key);
                if (ac_name != null) {
                    acButtons.add(Button.danger(Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + ac_name).withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                }
            }
            acButtons.add(Button.primary("getDiscardButtonsACs", "Discard an AC"));
            if (player.hasUnexhaustedLeader("nekroagent", activeGame)) {
                Button nekroButton = Button.secondary("exhaustAgent_nekroagent", "Use Nekro Agent").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("nekro")));
                acButtons.add(nekroButton);
            }
            if (player.hasUnexhaustedLeader("hacanagent", activeGame)) {
                Button hacanButton = Button.secondary("exhaustAgent_hacanagent", "Use Hacan Agent").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("hacan")));
                acButtons.add(hacanButton);
            }
            if(player.hasRelicReady("e6-g0_network")){
                acButtons.add(Button.success("exhauste6g0network", "Exhaust E6-G0 Network Relic to Draw AC"));
            }
        }
        return acButtons;
    }
    public static List<Button> getActionPlayActionCardButtons(Game activeGame, Player player) {
        List<Button> acButtons = new ArrayList<>();
        LinkedHashMap<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCardName(key);
                ActionCardModel actionCard = Mapper.getActionCard(key);
                String actionCardWindow = actionCard.getWindow();
                if (ac_name != null && actionCardWindow.equalsIgnoreCase("Action")) {
                    acButtons.add(Button.danger(Constants.AC_PLAY_FROM_HAND + value, "(" + value + ") " + ac_name).withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                }
            }
        }
        return acButtons;
    }
    public static List<Button> getDiscardActionCardButtons(Game activeGame, Player player, boolean doingAction) {
        List<Button> acButtons = new ArrayList<>();
        LinkedHashMap<String, Integer> actionCards = player.getActionCards();
        String stall = "";
        if(doingAction){
            stall = "stall";
        }
        if (actionCards != null && !actionCards.isEmpty()) {
            for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCardName(key);
                if (ac_name != null) {
                    acButtons.add(Button.primary("ac_discard_from_hand_" + value + stall, "(" + value + ") " + ac_name).withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                }
            }
        }
        return acButtons;
    }
     public static List<Button> getYssarilHeroActionCardButtons(Game activeGame, Player yssaril, Player notYssaril) {
        List<Button> acButtons = new ArrayList<>();
        LinkedHashMap<String, Integer> actionCards = notYssaril.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCardName(key);
                if (ac_name != null) {
                    acButtons.add(Button.danger("yssarilHeroInitialOffering_" + value + "_"+yssaril.getFaction(), ac_name).withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                }
            }
        }
        return acButtons;
    }
    public static List<Button> getToBeStolenActionCardButtons(Game activeGame, Player player) {
        List<Button> acButtons = new ArrayList<>();
        LinkedHashMap<String, Integer> actionCards = player.getActionCards();
        if (actionCards != null && !actionCards.isEmpty()) {
            for (java.util.Map.Entry<String, Integer> ac : actionCards.entrySet()) {
                Integer value = ac.getValue();
                String key = ac.getKey();
                String ac_name = Mapper.getActionCardName(key);
                if (ac_name != null) {
                    acButtons.add(Button.danger("takeAC_" + value + "_"+player.getFaction(), ac_name).withEmoji(Emoji.fromFormatted(Emojis.ActionCard)));
                }
            }
        }
        return acButtons;
    }
}
