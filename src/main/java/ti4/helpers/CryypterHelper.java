package ti4.helpers;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.leader.UnlockLeaderService;

public class CryypterHelper 
{
    //Revised Politics SC
    public static List<Button> getCryypterSC3Buttons(int sc) 
    {
        Button followButton = Buttons.green("sc_follow_" + sc, "Spend A Strategy Token");
        Button noFollowButton = Buttons.blue("sc_no_follow_" + sc, "Not Following");
        Button drawCards = Buttons.gray("cryypterSC3Draw", "Draw Action Cards", CardEmojis.ActionCard);
        return List.of(drawCards, followButton, noFollowButton);
    }

    @ButtonHandler("cryypterSC3Draw")
    public static void resolveCryypterSC3Draw(ButtonInteractionEvent event, Game game, Player player) 
    {
        drawXPickYActionCards(game, player, 3, true);
    }

    private static void drawXPickYActionCards(Game game, Player player, int draw, boolean addScheming) 
    {
        if (draw > 10) 
        {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "You probably shouldn't need to ever draw more than 10 cards, double check what you're doing please.");
            return;
        }
        String message = player.getRepresentation() + " drew " + draw + " action card" + (draw == 1 ? "" : "s") + ".";
        if (addScheming && player.hasAbility("scheming")) 
        {
            draw++;
            message = player.getRepresentation() + " drew " + draw + " action card" + (draw == 1 ? "" : "s") 
                + " (**Scheming** increases this from the normal " + (draw-1) + " action card" + (draw == 2 ? "" : "s") + ").";
        }

        for (int i = 0; i < draw; i++) 
        {
            game.drawActionCard(player.getUserID());
        }
        ActionCardHelper.sendActionCardInfo(game, player);

        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + " use buttons to discard 1 of the " + draw + " cards just drawn.",
            ActionCardHelper.getDiscardActionCardButtons(player, false));

        ButtonHelper.checkACLimit(game, player);
        if (addScheming && player.hasAbility("scheming")) ActionCardHelper.sendDiscardActionCardButtons(player, false);
        if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) 
        {
            CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
    }

    //VotC Setup
    public static void votcSetup(Game game, ButtonInteractionEvent event)
    {
        game.setVotcMode(true);
        game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_cryypter"));
        game.setTechnologyDeckID("techs_cryypter");
        game.swapInVariantTechs();
        game.setStrategyCardSet("votc");
        //TODO: Implement swap function to only replace specific ACs
        game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_deck_2"));
        //TODO: swap Xxcha and Keleres!Xxcha heroes
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set game to Voices of the Council mode.");
    }

    //Envoys
    public static void checkEnvoyUnlocks(Game game) 
    {
        //if (!game.isVotcMode()) 
        //{
        //    return;
        //}
        for (Player player : game.getRealPlayers()) 
        {
            Leader envoy = player.getLeaderByType("envoy").orElse(null);
            if (envoy != null && envoy.isLocked()) 
            {
                UnlockLeaderService.unlockLeader(envoy.getId(), game, player);
            }
        }
    }

    public static String argentEnvoyReminder(Player player, Game game)
    {
        Player argent = Helper.getPlayerFromUnlockedLeader(game, "argentenvoy");
        if (argent != null && argent != player) 
        {
            return " Reminder that Argent's Envoy is in play, and you may not wish to abstain.";
        }
        else
        {
            return "";
        }
    }


    public static void checkForAssigningMentakEnvoy(Game game) 
    {
        for (Player player : game.getRealPlayers()) {
            game.setStoredValue("Mentak Envoy " + player.getFaction(), "");
            if (player.hasUnexhaustedLeader("mentakenvoy")) {
                String msg = player.getRepresentation()
                    + " you have the option to pre-assign the declaration of using your Envoy on someone."
                    + " When they are up to vote, it will ping them saying that you wish to use your Envoy, and then it will be your job to clarify."
                    + " Feel free to not preassign if you don't wish to use it on this agenda.";
                List<Button> buttons2 = new ArrayList<>();
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    if (!game.isFowMode()) {
                        buttons2.add(Buttons.gray(
                            "resolvePreassignment_Mentak Envoy " + player.getFaction() + "_"
                                + p2.getFaction(),
                            p2.getFaction()));
                    } else {
                        buttons2.add(Buttons.gray(
                            "resolvePreassignment_Mentak Envoy " + player.getFaction() + "_"
                                + p2.getFaction(),
                            p2.getColor()));
                    }
                }
                buttons2.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons2);
            }
        }
    }
    
    public static void checkForMentakEnvoy(Player voter, Game game) {
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == voter) {
                continue;
            }
            Leader playerLeader = p2.getLeader("mentakenvoy").orElse(null);
            if (playerLeader != null) 
            {
                if (game.getStoredValue("Mentak Envoy " + p2.getFaction()).contains(voter.getFaction())) 
                {
                    ExhaustLeaderService.exhaustLeader(game, p2, playerLeader); //p2.exhaustTech("gr");
                    String msg = p2.getRepresentation(false, true) + " is using the Mentak Envoy to force "
                        + voter.getRepresentation(false, true)
                        + " to vote a particular way. The Envoy has been exhausted, the owner should elaborate on which way to vote.";
                    MessageHelper.sendMessageToChannel(voter.getCorrectChannel(), msg);
                    boolean hasPNs = voter.getPnCount() > 0;
                    boolean hasEnoughTGs = voter.getTg() > 1;
                    if (hasPNs || hasEnoughTGs) 
                    {
                        msg = voter.getRepresentation(false, true) +
                            " has the option to give 1 promissory note or 2 trade goods to ignore the effect of Mentak Envoy.";
                        List<Button> conclusionButtons = new ArrayList<>();
                        String finChecker = "FFCC_" + voter.getFaction() + "_";
                        Button accept = Buttons.blue(finChecker
                            + "mentakEnvoy_"
                            + p2.getUserID() + "_"
                            + "accept", "Vote For Chosen Outcome");
                        conclusionButtons.add(accept);
                        if(hasPNs)
                        {
                            Button PNdecline = Buttons.red(finChecker
                            + "mentakEnvoy_"
                            + p2.getUserID() + "_"
                            + "PNdecline", "Send 1 promissory note");
                            conclusionButtons.add(decline);
                        }
                        else
                        {
                            Button TGdecline = Buttons.red(finChecker
                            + "mentakEnvoy_"
                            + p2.getUserID() + "_"
                            + "TGdecline", "Send 2 trade goods");
                            conclusionButtons.add(decline);
                        }
                        MessageHelper.sendMessageToChannelWithButtons(voter.getCorrectChannel(), msg,
                        conclusionButtons);
                    }
                }
            }
        }
    }

    @ButtonHandler("mentakEnvoy")
    public static void resolveMentakEnvoy(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String[] fields = buttonID.split("_");
        Player mentakPlayer = game.getPlayer(fields[1]);
        String choice = fields[2];
        if (choice.equals("accept")) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getRepresentation()
                    + " has chosen to vote for the outcome indicated by "
                    + mentakPlayer.getRepresentation()
                    + ".");
        } 
        else if (choice.equals("PNdecline"))
        {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getRepresentation()
                    + " has chosen to send a promissory note and may vote in any manner that they wish.");
            List<Button> stuffToTransButtons = getForcedPNSendButtons(game, mentakPlayer, player);
            String message = player.getRepresentationUnfogged() + ", you have been forced to give a promissory note. Please select which promissory note you would like to send.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, stuffToTransButtons);
        }
        else 
        {
            player.setTg(player.getTg() - 2);
            mentakPlayer.setTg(mentakPlayer.getTg() + 2);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getRepresentation()
                    + " has given 2 trade goods and may vote in any manner that they wish.");
        }
        event.getMessage().delete().queue();
    }

    
    
    //TODO: ActionCardHelper.resolveActionCard(), 376
    public static void checkForAssigningYssarilEnvoy(GenericInteractionCreateEvent event, Game game, Player player, String acID)
    {
        if (player.hasUnexhaustedLeader("yssarilenvoy") && game.getPhaseOfGame() != null && game.getPhaseOfGame().startsWith("agenda"))
        {
            String msg = player.getRepresentation()
                    + " you have the option to user your Envoy on someone."
                    + " It will ping them saying that they may copy the effect of the AC you just played.";
                List<Button> buttons2 = new ArrayList<>();
                for (Player p2 : game.getRealPlayers()) {
                    if (p2 == player) {
                        continue;
                    }
                    String buttonText = "offerYssarilEnvoy_Yssaril Envoy " + player.getFaction() + "_"
                                + p2.getFaction() + "_"
                                + acID;
                    if (!game.isFowMode()) {
                        buttons2.add(Buttons.gray(buttonText, p2.getFaction()));
                    } else {
                        buttons2.add(Buttons.gray(buttonText, p2.getColor()));
                    }
                }
                buttons2.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons2);
        }
    }

    @ButtonHandler("offerYssarilEnvoy")
    public static void offerYssarilEnvoy(String buttonID, ButtonInteractionEvent event, Game game, Player player) 
    {
        String[] fields = buttonID.split("_");
        Player yssarilPlayer = game.getPlayer(fields[1]);
        Player targetPlayer = game.getPlayer(fields[2]);

        String msg = yssarilPlayer.getRepresentation(false, true) + " is using the Yssaril Envoy to allow "
                        + targetPlayer.getRepresentation(false, true)
                        + " to copy the effect of their AC. The Envoy has been exhausted.";
        MessageHelper.sendMessageToChannel(targetPlayer.getCorrectChannel(), msg);
        
        msg = targetPlayer.getRepresentation(false, true) +
            " has the option to accept or ignore the effect of the Yssaril Envoy.";
        List<Button> conclusionButtons = new ArrayList<>();
        String finChecker = "FFCC_" + voter.getFaction() + "_";
        Button accept = Buttons.blue(finChecker
            + "yssarilEnvoy_"
            + yssarilPlayer.getUserID() + "_"
            + "accept" + "_"               
            + fields[2], "Copy the AC");
        conclusionButtons.add(accept);
        
        Button decline = Buttons.red(finChecker
            + "yssarilEnvoy_"
            + yssarilPlayer.getUserID() + "_"
            + "decline", "Decline");
        conclusionButtons.add(decline);
        
        MessageHelper.sendMessageToChannelWithButtons(targetPlayer.getCorrectChannel(), msg,
        conclusionButtons);
        //event.getMessage().delete().queue();
    }

    @ButtonHandler("yssarilEnvoy")
    public static void resolveYssarilEnvoy(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String[] fields = buttonID.split("_");
        Player yssarilPlayer = game.getPlayer(fields[1]);
        String choice = fields[2];
        if (choice.equals("decline")) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getRepresentation()
                    + " has chosen not to resolve the effect of the Yssaril Envoy.");
        } 
        else 
        {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                player.getRepresentation()
                    + " has chosen to resolve the effect of the Yssaril Envoy.");
            ActionCardHelper.resolveActionCard(event, game, player, fields[3], -1, null);
        }
        
        event.getMessage().delete().queue();
    }



    

    //for later
    
    //TODO: Add "CryypterHelper.handleVotCRiders(event, game, player, riderName);" to near end of AgendaHelper.play_after(), currently line 3370
    public static void handleVotCRiders(ButtonInteractionEvent event, Game game, Player player, String riderName)
    {
        String votcRiderName = riderName.replace("votc_", "");

        if ("keleresxhero".equalsIgnoreCase(votcRiderName)) 
        {
            Leader playerLeader = player.getLeader("votc_keleresheroxxcha").orElse(null);
            if (playerLeader != null) 
            {
                String message = player.getRepresentation() + " played " +
                    Helper.getLeaderFullRepresentation(playerLeader);
                boolean purged = player.removeLeader(playerLeader);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    message + " - Odlynn Myrr, the Keleres (Xxcha) hero, has been purged.");
            }
        }
        else if (votcRiderName.contains("envoy"))
        {
            Leader playerLeader = player.getLeader(votcRiderName).orElse(null);
            if (playerLeader != null) 
            {
                ExhaustLeaderService.exhaustLeader(game, player, playerLeader);
            }
        }
    }
    
    //TODO: Add "CryypterHelper.addVotCAfterButtons(game, afterButtons);" to near end of AgendaHelper.getAfterButtons(), currently line 1650
    public static void addVotCAfterButtons(Game game, List<Button> afterButtons)
    {
        for (Player player : game.getPlayers().values()) 
        {
            votcRiderButtons(player, afterButtons, true);
        }
    }

    //TODO: Add "CryypterHelper.addVotCRiderQueueButtons(player, buttons);" to end of AgendaHelper.getPossibleAferButtons(), currently line 397
    public static void addVotCRiderQueueButtons(Player player, List<Button> buttons)
    {
        votcRiderButtons(player, buttons, false);
    }
    
    private static void votcRiderButtons(Player player, List<Button> buttons, boolean play)
    {
        for (Leader leader : player.getLeaders())
        {
            LeaderModel leaderModel = leader.getLeaderModel().orElse(null);
            if(!leader.isLocked() && leaderModel.getAbilityWindow() == "After an agenda is revealed:")
            {
                FactionModel factionModel = Mapper.getFaction(leaderModel.getFaction());
                String buttonID = "votc_" + leaderModel.getFaction() + leaderModel.getType();
                String buttonLabel = leaderModel.getName() + " (" + factionModel.getShortName() + " " + leaderModel.getType() + ")";
                if(play)
                {
                    String finChecker = "FFCC_" + player.getFaction() + "_";
                    buttons.add(Buttons.gray(finChecker + "play_after_" + buttonID, "Play " + buttonLabel, factionModel.getFactionEmoji()));
                }
                else
                {
                    buttons.add(Buttons.red("queue_after_" + buttonID, buttonLabel));
                }
            }
        }
    }

    public static void handleAdditionalVoteSources(Player player)
    {
        if (player.hasTechReady("cryypter_pi")) 
        {
            additionalVotesAndSources.put(TechEmojis.CyberneticTech + "_Predictive Intelligence_", 4);
        }
        //Nekro envoy
        if(player.getLeader("winnuenvoy").orElse(null) != null )
        {
            if(player.getExhaustedPlanets().contains("mr"))
            {
                additionalVotesAndSources.put(FactionEmojis.Winnu + " envoy with Mecatol Rex", 6);
            }
            else if(player.getExhaustedPlanets().contains("winnu"))
            {
                additionalVotesAndSources.put(FactionEmojis.Winnu + " envoy with Winnu", 4);
            }
        }
    }

    //TODO: Add "CryypterHelper.exhaustForVotes(player, thing);" to end of "if" clause of AgendaHelper.exhaustForVotes(), currently line 2469
    public static void exhaustForVotes(Player player, String component)
    {
        //Yin envoy exhausting other player's planet
        if(component.contains("yinenvoy"))
        {
            if (!prevoting) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentation()
                        + " please remove 1 infantry to pay for the Yin Envoy.",
                    ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, "infantry"));
            }
        }
    }

    //getPlanetButtonsVersion2 2574
    public static void getVoteSourceButtons()
    {
        if (player.hasTechReady("cryypter_pi")) 
        {
             planetButtons.add(Buttons.blue("exhaustForVotes_cryypterpi_4", "Use Predictive Intelligence Votes (4)",
                    TechEmojis.CyberneticTech));
        }
    }

    

    //AgendaHelper.getWinningRiders(), currently line 1832
    public static void handleVotCRiders()
    {
        
    }
}
