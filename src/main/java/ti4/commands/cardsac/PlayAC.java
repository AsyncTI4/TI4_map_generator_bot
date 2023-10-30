package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import java.util.Map;
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
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
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
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            sendMessage("Please select what Action Card to discard");
            return;
        }

        String reply = playAC(event, activeGame, player, option.getAsString().toLowerCase(), event.getChannel(), event.getGuild());
        if (reply != null) {
            sendMessage(reply);
        }
    }

    public static String playAC(GenericInteractionCreateEvent event, Game activeGame, Player player, String value, MessageChannel channel, Guild guild) {
        MessageChannel mainGameChannel = activeGame.getMainGameChannel() == null ? channel : activeGame.getMainGameChannel();

        String acID = null;
        int acIndex = -1;
        try {
            acIndex = Integer.parseInt(value);
            for (Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
                if (so.getValue().equals(acIndex)) {
                    acID = so.getKey();
                }
            }
        } catch (Exception e) {
            boolean foundSimilarName = false;
            String cardName = "";
            for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
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

        String activePlayerID = activeGame.getActivePlayer();
        if (player.isPassed() && activePlayerID != null) {
            Player activePlayer = activeGame.getPlayer(activePlayerID);
            if (activePlayer != null && activePlayer.hasTech("tp")) {
                return "You are passed and the active player has researched Transparasteel Plating. AC Play command cancelled.";
            }
        }
        if ("Action".equalsIgnoreCase(actionCardWindow) && activeGame.getPlayer(activePlayerID) != player) {
            return "You are trying to play a component action AC and the game does not think you are the active player. You can fix this with /player turn_start. Until then, you are #denied";
        }

        activeGame.discardActionCard(player.getUserID(), acIndex);
        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getGamePing(guild, activeGame)).append(" ").append(activeGame.getName()).append("\n");

        if (activeGame.isFoWMode()) {
            sb.append("Someone played an Action Card:\n");
        } else {
            sb.append(player.getRepresentation()).append(" played an Action Card:\n");
        }
        sb.append(actionCard.getRepresentation());
        List<Button> buttons = new ArrayList<>();
        Button sabotageButton = Button.danger("sabotage_ac_" + actionCardTitle, "Cancel AC With Sabotage").withEmoji(Emoji.fromFormatted(Emojis.Sabotage));
        buttons.add(sabotageButton);
        Player empy = Helper.getPlayerFromUnit(activeGame, "empyrean_mech");
        if (empy != null && ButtonHelperFactionSpecific.isNextToEmpyMechs(activeGame, player, empy) && !activeGame.getLaws().containsKey("articles_war")) {
            Button empyButton = Button.secondary("sabotage_empy_" + actionCardTitle, "Cancel " + actionCardTitle + " With Empyrean Mech ")
                .withEmoji(Emoji.fromFormatted(Emojis.mech));
            List<Button> empyButtons = new ArrayList<>();
            empyButtons.add(empyButton);
            Button refuse = Button.danger("deleteButtons", "Delete These Buttons");
            empyButtons.add(refuse);
            MessageHelper.sendMessageToChannelWithButtons(empy.getCardsInfoThread(),
                Helper.getPlayerRepresentation(empy, activeGame, activeGame.getGuild(), true) + "You have mechs adjacent to the player who played the AC. Use Buttons to decide whether to cancel.",
                empyButtons);

        }
        String instinctTrainingID = "it";
        for (Player player2 : activeGame.getPlayers().values()) {
            if (!player.equals(player2) && player2.hasTechReady(instinctTrainingID) && player2.getStrategicCC() > 0) {
                Button instinctButton = Button.secondary("sabotage_xxcha_" + actionCardTitle, "Cancel " + actionCardTitle + " With Instinct Training")
                    .withEmoji(Emoji.fromFormatted(Emojis.Xxcha));
                List<Button> xxchaButtons = new ArrayList<>();
                xxchaButtons.add(instinctButton);
                Button refuse = Button.danger("deleteButtons", "Delete These Buttons");
                xxchaButtons.add(refuse);
                MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(), Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)
                    + "You have Instinct Training unexhausted and a cc available. Use Buttons to decide whether to cancel", xxchaButtons);
            }

        }

        Button noSabotageButton = Button.primary("no_sabotage", "No Sabotage").withEmoji(Emoji.fromFormatted(Emojis.NoSabotage));
        buttons.add(noSabotageButton);
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannel(mainGameChannel, sb.toString());
        } else {
            if (Helper.isSaboAllowed(activeGame, player)) {
                MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, sb.toString(), activeGame, player, buttons, true);
            } else {
                MessageHelper.sendMessageToChannel(mainGameChannel, sb.toString());
                MessageHelper.sendMessageToChannel(mainGameChannel,
                    "Either all sabos were in the discard or active player had Transparasteel Plating and everyone was passed. Instinct training and watcher mechs may still be viable, who knows. ");
            }
            MessageChannel channel2 = ButtonHelper.getCorrectChannel(player, activeGame);
            if (actionCardTitle.contains("Manipulate Investments")) {
                List<Button> scButtons = new ArrayList<>();
                for (int sc = 1; sc < 9; sc++) {
                    Emoji scEmoji = Emoji.fromFormatted(Emojis.getSCBackEmojiFromInteger(sc));
                    Button button;
                    if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")) {
                        button = Button.secondary("FFCC_" + player.getFaction() + "_increaseTGonSC_" + sc, " ").withEmoji(scEmoji);
                    } else {
                        button = Button.secondary("FFCC_" + player.getFaction() + "_increaseTGonSC_" + sc, sc + " " + Helper.getSCName(sc, activeGame));
                    }
                    scButtons.add(button);
                }
                scButtons.add(Button.danger("deleteButtons", "Done adding TG"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, Helper.getPlayerRepresentation(player, activeGame, guild, false) + " Use buttons to increase tgs on SCs. Each press adds 1tg.",
                    scButtons);
            }
            if (actionCardTitle.contains("Archaeological Expedition")) {
                List<Button> scButtons = ButtonHelperActionCards.getArcExpButtons(activeGame, player);
                MessageHelper.sendMessageToChannelWithButtons(channel2,
                    Helper.getPlayerRepresentation(player, activeGame, guild, false) + " After checking for sabos, use buttons to explore a planet type x 3 and gain any frags", scButtons);
            }
            String codedName = "Plagiarize";
            String codedMessage = Helper.getPlayerRepresentation(player, activeGame, guild, false) + " After checking for sabos, use buttons to resolve ";
            List<Button> codedButtons = new ArrayList<Button>();
            if (actionCardTitle.contains(codedName)) {
                codedButtons.add(Button.success("getPlagiarizeButtons", "Resolve Plagiarize"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMessage+codedName, codedButtons);
            }

            codedName = "Mining Initiative";
            if (actionCardTitle.contains(codedName)) {
                codedButtons.add(Button.success("miningInitiative", "Resolve "+codedName));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMessage+codedName, codedButtons);
            }

            codedName = "Economic Initiative";
            if (actionCardTitle.contains(codedName)) {
                codedButtons.add(Button.success("economicInitiative", "Resolve "+codedName));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMessage+codedName, codedButtons);
            }

            codedName = "Industrial Initiative";
            if (actionCardTitle.contains(codedName)) {
                codedButtons.add(Button.success("industrialInitiative", "Resolve "+codedName));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMessage+codedName, codedButtons);
            }
            codedName = "Repeal Law";
            if (actionCardTitle.contains(codedName)) {
                codedButtons.add(Button.success("getRepealLawButtons", "Resolve "+codedName));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMessage+codedName, codedButtons);
            }

            codedName = "Divert Funding";
            if (actionCardTitle.contains(codedName)) {
                codedButtons.add(Button.success("getDivertFundingButtons", "Resolve "+codedName));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMessage+codedName, codedButtons);
            }

            codedName = "Focused Research";
            if (actionCardTitle.contains(codedName)) {
                codedButtons.add(Button.success("focusedResearch", "Resolve "+codedName));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMessage+codedName, codedButtons);
            }

            codedName = "Forward Supply Base";
            if (actionCardTitle.contains(codedName)) {
                codedButtons.add(Button.success("forwardSupplyBase", "Resolve "+codedName));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMessage+codedName, codedButtons);
            }

            codedName = "Rise of a Messiah";
            if (actionCardTitle.contains(codedName)) {
                codedButtons.add(Button.success("riseOfAMessiah", "Resolve "+codedName));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMessage+codedName, codedButtons);
            }

            codedName = "Veto";
            if (actionCardTitle.contains(codedName)) {
                codedButtons.add(Button.primary("flip_agenda", "Reveal next Agenda"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMessage+codedName, codedButtons);
            }

            codedName = "Fighter Conscription";
            if (actionCardTitle.contains(codedName)) {
                codedButtons.add(Button.success("fighterConscription", "Resolve "+codedName));
                MessageHelper.sendMessageToChannelWithButtons(channel2, codedMessage+codedName, codedButtons);
            }


            if (actionCardWindow.contains("After an agenda is revealed")) {

                List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no afters again.", activeGame, afterButtons, "after");
                Date newTime = new Date();
                activeGame.setLastActivePlayerPing(newTime);

                String finChecker = "FFCC_" + player.getFaction() + "_";
                if (actionCardTitle.contains("Rider") || actionCardTitle.contains("Sanction")) {
                    List<Button> riderButtons = AgendaHelper.getAgendaButtons(actionCardTitle, activeGame, finChecker);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Please select your rider target", activeGame, player, riderButtons);
                }
                if (actionCardTitle.contains("Hack Election")) {
                    activeGame.setHackElectionStatus(true);
                    Button setHack = Button.danger("hack_election", "Set the voting order as normal");
                    List<Button> hackButtons = List.of(setHack);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Voting order reversed. Please hit this button if hack election is sabod", activeGame, player, hackButtons);
                }

            }
            if (actionCardWindow.contains("When an agenda is revealed") && !actionCardTitle.contains("Veto")) {
                Date newTime = new Date();
                activeGame.setLastActivePlayerPing(newTime);
                List<Button> whenButtons = AgendaHelper.getWhenButtons(activeGame);
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no whens again.", activeGame, whenButtons, "when");
            }
            if ("Action".equalsIgnoreCase(actionCardWindow)) {
                String message = "Use buttons to end turn or do another action.";
                List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);            
                MessageHelper.sendMessageToChannelWithButtons(channel2, message, systemButtons);
                if (player.getLeaderIDs().contains("kelerescommander") && !player.hasLeaderUnlocked("kelerescommander")) {
                    String message2 = ButtonHelper.getTrueIdentity(player, activeGame)+" you can unlock keleres commander (if the AC isnt sabod) by paying 1tg.";
                    List<Button> buttons2 = new ArrayList<Button>();
                    buttons2.add(Button.success("pay1tgforKeleres", "Pay 1tg to unlock Commander"));
                    buttons2.add(Button.danger("deleteButtons", "Decline"));
                    MessageHelper.sendMessageToChannelWithButtons(channel2, message2, buttons2);
                }
                for(Player p2 : activeGame.getRealPlayers()){
                    if(p2 == player){
                        continue;
                    }
                    if(p2.getActionCards().keySet().contains("reverse_engineer")){
                        List<Button> reverseButtons = new ArrayList<Button>();
                        String key = "reverse_engineer";
                        String ac_name = Mapper.getActionCardName(key);
                        if (ac_name != null) {
                            reverseButtons.add(Button.success(Constants.AC_PLAY_FROM_HAND + p2.getActionCards().get(key) +"_reverse_"+actionCardTitle, "Reverse engineer "+ actionCardTitle));
                        }
                        reverseButtons.add(Button.danger("deleteButtons", "Decline"));
                        String cyberMessage = ""+Helper.getPlayerRepresentation(p2, activeGame, event.getGuild(), true)
                        + " reminder that you can use reverse engineer on "+actionCardTitle;
                        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
                            cyberMessage, reverseButtons);
                    }
                }
            }
        }

        //Fog of war ping
        if (activeGame.isFoWMode()) {
            String fowMessage = player.getRepresentation() + " played an Action Card: " + actionCardTitle;
            FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, fowMessage);
            MessageHelper.sendPrivateMessageToPlayer(player, activeGame, "Played action card: " + actionCardTitle);
        }

        ACInfo.sendActionCardInfo(activeGame, player);
        return null;
    }
}
