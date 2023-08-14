package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

public class PlayAC extends ACCardsSubcommandData {
    public PlayAC() {
        super(Constants.PLAY_AC, "Play an Action Card");
        addOptions(new OptionData(OptionType.STRING, Constants.ACTION_CARD_ID, "Action Card ID that is sent between () or Name/Part of Name").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            sendMessage("Please select what Action Card to discard");
            return;
        }

        String reply = playAC(event, activeMap, player, option.getAsString().toLowerCase(), event.getChannel(), event.getGuild());
        if (reply != null) {
            sendMessage(reply);
        }
    }

    public static String playAC(GenericInteractionCreateEvent event, Map activeMap, Player player, String value, MessageChannel channel, Guild guild) {
        MessageChannel mainGameChannel = activeMap.getMainGameChannel() == null ? channel : activeMap.getMainGameChannel();

        String acID = null;
        int acIndex = -1;
        try {
            acIndex = Integer.parseInt(value);
            for (java.util.Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
                if (so.getValue().equals(acIndex)) {
                    acID = so.getKey();
                }
            }
        } catch (Exception e) {
            boolean foundSimilarName = false;
            String cardName = "";
            for (java.util.Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
                String actionCardName = Mapper.getActionCardName(ac.getKey());
                if (actionCardName != null) {
                    actionCardName = actionCardName.toLowerCase();
                    if (actionCardName.contains(value)) {
                        if (foundSimilarName && !cardName.equals(actionCardName)) {
                            return "Multiple cards with similar name founds, please use ID";
                        }
                        acID = ac.getKey();
                        acIndex = ac.getValue();
                        foundSimilarName = true;
                        cardName = actionCardName;
                    }
                }
            }
        }
        if (acID == null) {
            //sendMessage();
            return "No such Action Card ID found, please retry";
        }
        ActionCardModel actionCard = Mapper.getActionCard(acID);
        String actionCardTitle = actionCard.getName();
        String actionCardWindow = actionCard.getWindow();

        String activePlayerID = activeMap.getActivePlayer();
        if (player.isPassed() && activePlayerID != null) {
            Player activePlayer = activeMap.getPlayer(activePlayerID);
            if (activePlayer != null && activePlayer.hasTech("tp")) {
                return "You are passed and the active player has researched Transparasteel Plating. AC Play command cancelled.";
            }
        }

        activeMap.discardActionCard(player.getUserID(), acIndex);
        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getGamePing(guild, activeMap)).append(" ").append(activeMap.getName()).append("\n");

        if (activeMap.isFoWMode()) {
            sb.append("Someone played an Action Card:\n");
        } else {
            sb.append(Helper.getPlayerRepresentation(player, activeMap)).append(" played an Action Card:\n");
        }
        sb.append(actionCard.getRepresentation());
        List<Button> buttons = new ArrayList<Button>();
        Button sabotageButton = Button.danger("sabotage_ac_"+actionCardTitle, "Cancel AC With Sabotage").withEmoji(Emoji.fromFormatted(Emojis.Sabotage));
        buttons.add(sabotageButton);
        Player empy = Helper.getPlayerFromUnit(activeMap, "empyrean_mech");
        if (empy != null && ButtonHelperFactionSpecific.isNextToEmpyMechs(activeMap, player, empy)) {
            Player player2 = empy;
            Button empyButton = Button.secondary("sabotage_empy_"+actionCardTitle, "Cancel "+actionCardTitle+" With Empyrean Mech ").withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
            List<Button> empyButtons = new ArrayList<Button>();
            empyButtons.add(empyButton);
            Button refuse = Button.danger("deleteButtons", "Delete These Buttons");
            empyButtons.add(refuse);
            MessageHelper.sendMessageToChannelWithButtons((MessageChannel) player2.getCardsInfoThread(activeMap), Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)+"You have mechs adjacent to the player who played the AC. Use Buttons to decide whether to cancel.", empyButtons);

        }
        String instinctTrainingID = "it";
        for(Player player2 : activeMap.getPlayers().values())
        {
            if(player2.hasTechReady(instinctTrainingID) && player2.getStrategicCC() > 0)
            {
                Button instinctButton = Button.secondary("sabotage_xxcha_"+actionCardTitle, "Cancel "+actionCardTitle+" With Instinct Training").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("Xxcha")));
                List<Button> xxchaButtons = new ArrayList<Button>();
                xxchaButtons.add(instinctButton);
                Button refuse = Button.danger("deleteButtons", "Delete These Buttons");
                xxchaButtons.add(refuse);
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) player2.getCardsInfoThread(activeMap), Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)+"You have Instinct Training unexhausted and a cc available. Use Buttons to decide whether to cancel", xxchaButtons);
            }

        }
            
            

        
        Button noSabotageButton = Button.primary("no_sabotage", "No Sabotage").withEmoji(Emoji.fromFormatted(Emojis.NoSabotage));
        buttons.add(noSabotageButton);
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannel(mainGameChannel, sb.toString());
        } else {
            if(Helper.isSaboAllowed(activeMap, player)){
                MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, sb.toString(), activeMap, player, buttons);
            }else{
                MessageHelper.sendMessageToChannel(mainGameChannel, sb.toString());
                MessageHelper.sendMessageToChannel(mainGameChannel, "Either all sabos were in the discard or active player had Transparasteel Plating and everyone was passed. Instinct training and watcher mechs may still be viable, who knows. ");
            }
            if(actionCardTitle.contains("Manipulate Investments")){
                MessageChannel channel2 = ButtonHelper.getCorrectChannel(player, activeMap);
                List<Button> scButtons = new ArrayList<Button>();
                for(int sc = 1; sc < 9; sc++){
                    Emoji scEmoji = Emoji.fromFormatted(Helper.getSCBackEmojiFromInteger(sc));
                    Button button;
                    if (scEmoji != null && scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")) {
                        button = Button.secondary("FFCC_"+player.getFaction()+"_increaseTGonSC_" + sc, " ").withEmoji(scEmoji);
                    } else {
                        button = Button.secondary("FFCC_"+player.getFaction()+"_increaseTGonSC_" + sc, "" + sc);
                    }
                    scButtons.add(button);
                }
                scButtons.add(Button.danger("deleteButtons", "Done adding TG"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, Helper.getPlayerRepresentation(player, activeMap, guild, false)+" Use buttons to increase tgs on SCs. Each press adds 1tg.", scButtons);
            }
            if(actionCardTitle.contains("Archaeological Expedition")){
                MessageChannel channel2 = ButtonHelper.getCorrectChannel(player, activeMap);
                List<Button> scButtons = ButtonHelper.getArcExpButtons(activeMap, player);
                MessageHelper.sendMessageToChannelWithButtons(channel2, Helper.getPlayerRepresentation(player, activeMap, guild, false)+" After checking for sabos, use buttons to explore a planet type x 3 and gain any frags", scButtons);
            }

            if (actionCardWindow.contains("After an agenda is revealed")) {
                
                List<Button> afterButtons = AgendaHelper.getAfterButtons(activeMap);
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no afters again.", activeMap, afterButtons, "after");
                Date newTime = new Date();
                activeMap.setLastActivePlayerPing(newTime);

                String finChecker = "FFCC_"+player.getFaction() + "_";
                if (actionCardTitle.contains("Rider") || actionCardTitle.contains("Sanction") ) {
                    List<Button> riderButtons = AgendaHelper.getAgendaButtons(actionCardTitle, activeMap, finChecker);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Please select your rider target", activeMap, player, riderButtons);
                }
                if (actionCardTitle.contains("Hack Election") ) {
                    activeMap.setHackElectionStatus(true);
                    Button setHack = Button.danger("hack_election", "Set the voting order as normal");
                    List<Button> hackButtons =  List.of(setHack);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Voting order reversed. Please hit this button if hack election is sabod", activeMap, player, hackButtons);
                }

            }
            if (actionCardWindow.contains("When an agenda is revealed") && !actionCardTitle.contains("Veto")) {
                Date newTime = new Date();
                activeMap.setLastActivePlayerPing(newTime);
                List<Button> whenButtons = AgendaHelper.getWhenButtons(activeMap);
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no whens again.", activeMap, whenButtons, "when");

            }
            if(actionCardWindow.equalsIgnoreCase("Action")){
                String message = "Use buttons to end turn or do another action.";
                List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeMap, true, event);
                MessageChannel channel2 = activeMap.getMainGameChannel();
                if(activeMap.isFoWMode()){
                    channel2 = player.getPrivateChannel();
                }
                MessageHelper.sendMessageToChannelWithButtons(channel2, message, systemButtons);
            }
        }

        //Fog of war ping
		if (activeMap.isFoWMode()) {
            String fowMessage = Helper.getPlayerRepresentation(player, activeMap) + " played an Action Card: " + actionCardTitle;
			FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, fowMessage);
            MessageHelper.sendPrivateMessageToPlayer(player, activeMap, "Played action card: " + actionCardTitle);
		}

        ACInfo.sendActionCardInfo(activeMap, player);
        return null;
    }
}
