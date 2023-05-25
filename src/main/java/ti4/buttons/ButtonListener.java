package ti4.buttons;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.*;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import ti4.MapGenerator;
import ti4.MessageListener;
import ti4.commands.agenda.DrawAgenda;
import ti4.commands.agenda.PutAgendaBottom;
import ti4.commands.agenda.PutAgendaTop;
import ti4.commands.agenda.RevealAgenda;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.map.UnitHolder;
import ti4.commands.cardsac.PlayAC;
import ti4.commands.cardsso.DiscardSO;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.explore.ExpPlanet;
import ti4.commands.status.Cleanup;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.status.ScorePublic;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.player.PlanetExhaust;
import ti4.commands.player.PlanetRefresh;
import ti4.commands.player.Stats;
import ti4.commands.player.SCPick;
import ti4.commands.player.Turn;
import ti4.helpers.Constants;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.map.Tile;
import java.util.*;
import java.util.concurrent.TimeUnit;
import ti4.generator.Mapper;


public class ButtonListener extends ListenerAdapter {
    public static HashMap<Guild, HashMap<String, Emoji>> emoteMap = new HashMap<>();
    private static HashMap<String, Set<Player>> playerUsedSC = new HashMap<>();

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        event.deferEdit().queue();
        String id = event.getUser().getId();
        MessageListener.setActiveGame(event.getMessageChannel(), id, "button");
        String buttonID = event.getButton().getId();
        String buttonLabel = event.getButton().getLabel();
        String lastchar = StringUtils.right(buttonLabel, 2).replace("#", "");
        String lastcharMod = StringUtils.right(buttonID, 1);
        if (buttonID == null) {
            event.getChannel().sendMessage("Button command not found").queue();
            return;
        }


        // BotLogger.log(event, ""); //TEMPORARY LOG ALL BUTTONS

        String messageID = event.getMessage().getId();

        String gameName = event.getChannel().getName();
        gameName = gameName.replace(ACInfo_Legacy.CARDS_INFO, "");
        gameName = gameName.substring(0, gameName.indexOf("-"));
        Map activeMap = MapManager.getInstance().getMap(gameName);
        Player player = activeMap.getPlayer(id);
        player = Helper.getGamePlayer(activeMap, player, event.getMember(), id);
        if (player == null || player.getFaction() == null) {
            event.getChannel().sendMessage("You're not a player of the game").queue();
            return;
        }

        MessageChannel privateChannel = event.getChannel();
        if (activeMap.isFoWMode()) {
            if (player.getPrivateChannel() == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Private channels are not set up for this game. Messages will be suppressed.");
                privateChannel = null;
            } else {
                privateChannel = player.getPrivateChannel();
            }
        }

        MessageChannel mainGameChannel = event.getChannel();
        if (activeMap.getMainGameChannel() != null) {
            mainGameChannel = activeMap.getMainGameChannel();
        }

        MessageChannel actionsChannel = null;
        for (TextChannel textChannel_ : MapGenerator.jda.getTextChannels()) {
            if (textChannel_.getName().equals(gameName + Constants.ACTIONS_CHANNEL_SUFFIX)) {
                actionsChannel = textChannel_;
                break;
            }
        }

        if (buttonID.startsWith("FFCC_")) {
            buttonID = buttonID.replace("FFCC_", "");
            String factionWhoGeneratedButton = buttonID.substring(0, buttonID.indexOf("_"));
            buttonID = buttonID.replace(factionWhoGeneratedButton+"_", "");
            String factionWhoIsUp = player.getFaction();
            if (!player.getFaction().equalsIgnoreCase(factionWhoGeneratedButton) && !buttonLabel.toLowerCase().contains(factionWhoIsUp)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "To "+ Helper.getFactionIconFromDiscord(player.getFaction()) +": you are not the faction who these buttons are meant for.");
                return;
            }
        }

        String finsFactionCheckerPrefix = "FFCC_"+player.getFaction() + "_";

        if (buttonID.startsWith(Constants.AC_PLAY_FROM_HAND)) {
            String acID = buttonID.replace(Constants.AC_PLAY_FROM_HAND, "");
            MessageChannel channel = null;
            if (activeMap.getMainGameChannel() != null) {
                channel = activeMap.getMainGameChannel();
            } else {
                channel = actionsChannel;
            }

            if (channel != null) {
                try {
                    String error = PlayAC.playAC(event, activeMap, player, acID, channel, event.getGuild());
                    if (error != null) {
                        event.getChannel().sendMessage(error).queue();
                    }
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse AC ID: " + acID, e);
                    event.getChannel().asThreadChannel().sendMessage("Could not parse AC ID: " + acID + " Please play manually.").queue();
                    return;
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
        } else if (buttonID.startsWith(Constants.SO_SCORE_FROM_HAND)) {
            String soID = buttonID.replace(Constants.SO_SCORE_FROM_HAND, "");
            MessageChannel channel = null;
            if (activeMap.isFoWMode()) {
                channel = privateChannel;
            } else if (activeMap.isCommunityMode() && activeMap.getMainGameChannel() != null) {
                channel = mainGameChannel;
            } else {
                channel = actionsChannel;
            }

            if (channel != null) {
                try {
                    int soIndex = Integer.parseInt(soID);
                    ScoreSO.scoreSO(event, activeMap, player, soIndex, channel);
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse SO ID: " + soID, e);
                    event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please Score manually.").queue();
                    return;
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
            event.getMessage().delete().queue();
        }else if (buttonID.startsWith("SODISCARD_")) {
            String soID = buttonID.replace("SODISCARD_", "");
            MessageChannel channel = null;
            if (activeMap.isFoWMode()) {
                channel = privateChannel;
            } else if (activeMap.isCommunityMode() && activeMap.getMainGameChannel() != null) {
                channel = mainGameChannel;
            } else {
                channel = actionsChannel;
            }

            if (channel != null) {
                try {
                    int soIndex = Integer.parseInt(soID);
                    new DiscardSO().discardSO(event, player, soIndex, activeMap);
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse SO ID: " + soID, e);
                    event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please discard manually.").queue();
                    return;
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
            event.getMessage().delete().queue();
        }else if (buttonID.startsWith("get_so_score_buttons")) {
            String secretScoreMsg = "_ _\nClick a button below to score your Secret Objective";
            List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveButtons(activeMap, player);
            if (soButtons != null && !soButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Fin");
            }
        }else if (buttonID.startsWith("get_so_discard_buttons")) {
            String secretScoreMsg = "_ _\nClick a button below to discard your Secret Objective";
            List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveDiscardButtons(activeMap, player);
            if (soButtons != null && !soButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Fin");
            }
        } else if (buttonID.startsWith(Constants.PO_SCORING)) {
            String poID = buttonID.replace(Constants.PO_SCORING, "");
            try {
                int poIndex = Integer.parseInt(poID);
                ScorePublic.scorePO(event, privateChannel, activeMap, player, poIndex);
                addReaction(event, false, false, null, "");
            } catch (Exception e) {
                BotLogger.log(event, "Could not parse PO ID: " + poID, e);
                event.getChannel().sendMessage("Could not parse PO ID: " + poID + " Please Score manually.").queue();
                return;
            }
        } else if (buttonID.startsWith(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX)) {
            String faction = buttonID.replace(Constants.SC3_ASSIGN_SPEAKER_BUTTON_ID_PREFIX, "");
            if (!player.getSCs().contains(3)) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Only the player who played Politics can assign Speaker");
                return;
            }
            if (activeMap != null && !activeMap.isFoWMode()) {
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_.getFaction().equals(faction)) {
                        activeMap.setSpeaker(player_.getUserID());
                        String message = Emojis.SpeakerToken + " Speaker assigned to: " + Helper.getPlayerRepresentation(player_, activeMap);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    }
                }
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("reveal_stage")) {
            String lastC = StringUtils.right(buttonLabel, 1);
            if (lastC.equalsIgnoreCase("2")) {
                new RevealStage2().revealS2(event, event.getChannel());
            } else {
                new RevealStage1().revealS1(event, event.getChannel());
            }

            int playersWithSCs = 0;

            for(Player player2 : activeMap.getPlayers().values())
            {
                if(playersWithSCs > 1)
                {
                    new Cleanup().runStatusCleanup(activeMap);
                    addReaction(event, false, true, "Running Status Cleanup. ", "Status Cleanup Run!");
                    break;
                }
                if(player2.isRealPlayer())
                {
                    if(player2.getSCs()!= null && player2.getSCs().size() > 0 && !player2.getSCs().contains(Integer.valueOf(0)))
                    {
                        playersWithSCs = playersWithSCs+1;
                    }
                }
            }






            String message2 = null;
            message2 = "Resolve status homework using the buttons. Only the Ready for [X] button is essential to hit, all others are optional. ";
            Button draw1AC = Button.success("draw_1_AC", "Draw 1 AC");
            Button confirmCCs = Button.primary("confirm_cc", "Confirm Your CC Update is Complete & Final");
            Button getCCs= Button.success("redistributeCCButtons", "Redistribute & Gain CCs");
            boolean custodiansTaken = activeMap.isCustodiansScored();
            Button passOnAbilities;
            if (custodiansTaken) {
                passOnAbilities = Button.danger("pass_on_abilities", "Ready For Agenda");
                message2 = message2 +" Ready for Agenda means you are done playing/passing on playing political stability, ancient burial sites, and crown of emphidia.";
            } else {
                passOnAbilities = Button.danger("pass_on_abilities", "Ready For Strategy Phase");
                message2 = message2 +" Ready for Strategy Phase means you are done playing/passing on playing political stability, summit, and manupulate investments. ";
            }
            List<Button> buttons = null;
           

            if (activeMap.isFoWMode()) {
                buttons = List.of(draw1AC, getCCs);
                message2 = "Resolve status homework using the buttons";
                for (Player p1 : activeMap.getPlayers().values()) {
                    if (p1 == null || p1.isDummy() || p1.getFaction() == null || p1.getPrivateChannel() == null) {
                        continue;
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(p1.getPrivateChannel(), message2, buttons);

                    }
                }
                buttons = List.of(confirmCCs, passOnAbilities);
            } else {

                buttons = List.of(draw1AC, getCCs, confirmCCs, passOnAbilities);
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            event.getMessage().delete().queue();


        } else if (buttonID.startsWith("sc_follow_") && (!buttonID.contains("leadership")) && (!buttonID.contains("trade"))) {
            boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "");
            if (!used) {
                String message = deductCC(player, event);
                int scnum = 1;
                boolean setstatus = true;
                try {
                    scnum = Integer.parseInt(lastcharMod);
                } catch (NumberFormatException e) {
                    setstatus = false;
                }
                if (setstatus) {
                    player.addFollowedSC(scnum);
                }
                addReaction(event, false, false, message, "");
            }
        }
        else if (buttonID.startsWith("sc_no_follow_")) {
            int scnum2 = 1;
            boolean setstatus = true;
            try {
                scnum2 = Integer.parseInt(lastcharMod);
            } catch (NumberFormatException e) {
                setstatus = false;
            }
            if (setstatus) {
                player.addFollowedSC(scnum2);
            }
            addReaction(event, false, false, "Not Following", "");
            Set<Player> players = playerUsedSC.get(messageID);
            if (players == null) {
                players = new HashSet<>();
            }
            players.remove(player);
            playerUsedSC.put(messageID, players);
        }
        else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            addReaction(event, false, false, null, "");
        }
        else if (buttonID.startsWith("movedNExplored_")) {
            String bID = buttonID.replace("movedNExplored_", "");
            String[] info = bID.split("_");

            String message = "";


            new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeMap), info[1], info[2], player, false, activeMap, 1, false);

            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("distant_suns_")) {
            String bID = buttonID.replace("distant_suns_", "");
            String[] info = bID.split("_");
            String message = "";
            if (info[0].equalsIgnoreCase("decline")) {
                message = "Rejected Distant Suns Ability";
                new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeMap), info[1], info[2], player, true, activeMap, 1, false);
            } else {
                message = "Exploring twice";
                new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeMap), info[1], info[2], player, true, activeMap, 2, false);
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("resolve_explore_")) {
            String bID = buttonID.replace("resolve_explore_", "");
            String[] info = bID.split("_");
            String cardID = info[0];
            String planetName = info[1];
            Tile tile = Helper.getTileFromPlanet(planetName, activeMap);
            StringBuilder messageText = new StringBuilder();
            messageText.append(Helper.getPlayerRepresentation(player, activeMap)).append(" explored ");

            messageText.append("Planet "+ Helper.getPlanetRepresentationPlusEmoji(planetName) +" *(tile "+ tile.getPosition() + ")*:\n");
            messageText.append("> ").append(new ExpPlanet().displayExplore(cardID));
            new ExpPlanet().resolveExplore(event, cardID, tile, planetName, messageText.toString(), false, player, activeMap);
            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("refresh_")) {
            String planetName = buttonID.replace("refresh_", "");
            new PlanetRefresh().doAction(player, planetName, activeMap);
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex>-1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            String totalVotesSoFar = event.getMessage().getContentRaw();
            if (actionRow2.size() > 0) {
                event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
            }
            String ident = Helper.getFactionIconFromDiscord(player.getFaction());
            MessageHelper.sendMessageToChannel(event.getChannel(), ident+" Readied "+ Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap));


        }
        else if (buttonID.startsWith("refreshVotes_")) {
            String votes = buttonID.replace("refreshVotes_", "");
            List<Button> voteActionRow = Helper.getPlanetRefreshButtons(event, player, activeMap);
            Button concludeRefreshing = Button.danger( finsFactionCheckerPrefix+"delete_buttons_"+votes, "Done readying planets.");
            voteActionRow.add(concludeRefreshing);
            String voteMessage2 = "Use the buttons to ready planets. When you're done it will prompt the next person to vote.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage2,voteActionRow);
            event.getMessage().delete().queue();
        }

        else if (buttonID.startsWith("getAllTechOfType_")) {
            String techType = buttonID.replace("getAllTechOfType_", "");
            List<Button> buttons = Helper.getTechButtons(Helper.getAllTechOfAType(techType, player.getFaction(), player), techType, player);

            String message = Helper.getPlayerRepresentation(player, activeMap)+" Use The Buttons To Get The Tech You Want";

            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);

            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("getTech_")) {

            String tech = buttonID.replace("getTech_", "");
            String techFancy = buttonLabel;
            String ident = Helper.getFactionIconFromDiscord(player.getFaction());

            String message = ident+ " Acquired The Tech " +  Helper.getTechRepresentation(AliasHandler.resolveTech(tech));

            String trueIdentity = Helper.getPlayerRepresentation(player, activeMap, event.getGuild(), true);
            String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";
            player.addTech(AliasHandler.resolveTech(tech));
            List<Button> buttons = Helper.getPlanetExhaustButtons(event, player, activeMap);
            if (player.getTg() > 0) {
                Button lost1TG = Button.danger("reduceTG_1", "Spend 1 TG");
                buttons.add(lost1TG);
            }
            if (player.getTg() > 1) {
                Button lost2TG = Button.danger("reduceTG_2", "Spend 2 TGs");
                buttons.add(lost2TG);
            }
            if (player.getTg() > 2) {
                Button lost3TG = Button.danger("reduceTG_3", "Spend 3 TGs");
                buttons.add(lost3TG);
            }
            Button DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
            buttons.add(DoneExhausting);

            MessageHelper.sendMessageToChannel(event.getChannel(), message);

            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("spend_")) {
            String planetName = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());

            new PlanetExhaust().doAction(player, planetName, activeMap);
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex>-1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            String totalVotesSoFar = event.getMessage().getContentRaw();
            String ident = Helper.getFactionIconFromDiscord(player.getFaction());
            if (totalVotesSoFar.contains("exhausted")) {
                totalVotesSoFar = totalVotesSoFar + ", "+Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
            } else {
                totalVotesSoFar = ident + " exhausted " +Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
            }
            event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
          //  MessageHelper.sendMessageToChannel(event.getChannel(), ident+" Exhausted "+Helper.getPlanetRepresentation(planetName, activeMap));

        }
        else if (buttonID.startsWith("sabotage_")) {
            String type = buttonID.replace("sabotage_", "");
            String message = "Cancelling this AC using ";
            String addMessage = "An AC has been cancelled!";
            boolean sendReact = true;
            if (type.equalsIgnoreCase("empy")) {
                message = message + "a Watcher mech! The Watcher should be removed now by the owner.";
            }
            else if (type.equalsIgnoreCase("xxcha")) {
                message = message + "a use of the Instinct Training tech! The tech has been exhausted and a strategy CC removed.";
                if (player.getTechs().contains(AliasHandler.resolveTech("Instinct Training"))) {
                    player.exhaustTech(AliasHandler.resolveTech("Instinct Training"));
                    if (player.getStrategicCC() > 0) {
                        player.setStrategicCC(player.getStrategicCC()-1);
                    }
                } else {
                    sendReact = false;
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Someone clicked the Instinct Training button but did not have the tech.");
                }
            }
            else if (type.equalsIgnoreCase("ac")) {
                message = message + "A Sabotage!";
                boolean hasSabo = false;
                String saboID = "3";
                for (String AC : player.getActionCards().keySet()) {
                    if (AC.contains("sabo")) {
                        hasSabo = true;
                        saboID = ""+player.getActionCards().get(AC);
                        break;
                    }
                }
                if (hasSabo) {
                    PlayAC.playAC(event, activeMap, player, saboID, event.getChannel(), activeMap.getGuild());
                } else {
                    addMessage = "";
                    message = "Tried to play a sabo but found none in hand.";
                    sendReact = false;
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Someone clicked the AC sabo button but did not have a sabo in hand.");
                }

            }

            if (sendReact) {
                addReaction(event, true, true, message, addMessage);
            }


        }
        else if (buttonID.startsWith("reduceTG_")) {

            int tgLoss = Integer.parseInt(buttonID.replace("reduceTG_", ""));
            String ident = Helper.getFactionIconFromDiscord(player.getFaction()) + "";
            String message = ident + " reduced tgs by "+tgLoss+" ("+player.getTg()+"->"+(player.getTg()-tgLoss)+").";
            if (tgLoss > player.getTg()) {
                message = "You dont have "+tgLoss+" tgs. No change made.";
            } else {
                player.setTg(player.getTg()-tgLoss);
            }


            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
        else if (buttonID.startsWith("exhaust_")) {
            String planetName = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            String votes = buttonLabel.substring(buttonLabel.indexOf("(")+1, buttonLabel.indexOf(")"));
            if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive")&& !buttonID.contains("everything")) {
                new PlanetExhaust().doAction(player, planetName, activeMap);
            }
            if (buttonID.contains("everything")) {
                for (String planet : player.getPlanets()) {
                    player.exhaustPlanet(planet);
                }
            }

            //List<ItemComponent> actionRow = event.getMessage().getActionRows().get(0).getComponents();
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex>-1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            String totalVotesSoFar = event.getMessage().getContentRaw();
            if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive") && !buttonID.contains("everything")) {


                if (totalVotesSoFar == null || totalVotesSoFar.equalsIgnoreCase("")) {
                    totalVotesSoFar = "Total votes exhausted so far: "+ votes +"\n Planets exhausted so far are: "+Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
                } else {
                    int totalVotes = Integer.parseInt(totalVotesSoFar.substring(totalVotesSoFar.indexOf(":")+2,totalVotesSoFar.indexOf("\n"))) + Integer.parseInt(votes);
                    totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":")+2) + totalVotes + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"), totalVotesSoFar.length()) + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
                }
                if (actionRow2.size() > 0) {
                    event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
                }
            // addReaction(event, true, false,"Exhausted "+Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap) + " as "+ votes + " votes", "");
            } else {
                if (totalVotesSoFar == null || totalVotesSoFar.equalsIgnoreCase("")) {
                    totalVotesSoFar = "Total votes exhausted so far: "+ votes +"\n Planets exhausted so far are: all planets";
                } else {
                    int totalVotes = Integer.parseInt(totalVotesSoFar.substring(totalVotesSoFar.indexOf(":")+2,totalVotesSoFar.indexOf("\n"))) + Integer.parseInt(votes);
                    totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":")+2) + totalVotes + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"), totalVotesSoFar.length());
                }
                if (actionRow2.size() > 0) {
                    event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
                }
                if (buttonID.contains("everything")) {
                // addReaction(event, true, false,"Exhausted "+Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap) + " as "+ votes + " votes", "");
                addReaction(event, true, false,"Exhausted all planets for "+ votes + " votes", "");
                } else {
                    addReaction(event, true, false,"Used ability for "+ votes + " votes", "");
                }
            }


        }
        else if (buttonID.startsWith("diplo_")) {
            String planet = buttonID.replace("diplo_", "");
            String tileID = AliasHandler.resolveTile(planet.toLowerCase());
            Tile tile = activeMap.getTile(tileID);
            if (tile == null) {
                tile = activeMap.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
                return;
            }

            for (Player player_ : activeMap.getPlayers().values()) {
                if (player_ != player) {
                    String color = player_.getColor();
                    if (Mapper.isColorValid(color)) {
                        AddCC.addCC(event, color, tile);
                        Helper.isCCCountCorrect(event, activeMap, color);
                    }
                }
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), "Diplod the system containing " + Helper.getPlanetRepresentation(planet, activeMap));
            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("delete_buttons_")) {
            boolean resolveTime = false;
            String winner = "";
            String votes = buttonID.substring(buttonID.lastIndexOf("_")+1, buttonID.length());
            if (!buttonID.contains("outcomeTie_")) {
                if (votes.equalsIgnoreCase("0")) {

                    String pfaction2 = null;
                    if (player != null) {
                        pfaction2 = player.getFaction();
                    }
                    if (pfaction2 != null && buttonLabel.toLowerCase().contains(pfaction2)) {
                        addReaction(event, true, true,"Abstained.", "");
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(), "You are not the faction who is supposed to press this button.");
                        return;
                    }

                } else {
                    String identifier = "";
                    String outcome = activeMap.getLatestOutcomeVotedFor();
                    if (activeMap.isFoWMode()) {
                        identifier = player.getColor();
                    } else {
                        identifier = player.getFaction();
                    }
                    HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
                    String existingData = outcomes.getOrDefault(outcome, "empty");
                    if (existingData.equalsIgnoreCase("empty")) {

                        existingData = identifier + "_" + votes;
                    } else {
                        existingData = existingData + ";" + identifier + "_" + votes;
                    }
                    activeMap.setCurrentAgendaVote(outcome, existingData);
                    addReaction(event, true, true,"Voted "+votes + " votes for "+StringUtils.capitalize(outcome)+"!", "");
                }

                String message = " up to vote! Resolve using buttons.";

                Player nextInLine = AgendaHelper.getNextInLine(player, AgendaHelper.getVotingOrder(activeMap),activeMap);
                String realIdentity2 =Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);

                int[] voteInfo = AgendaHelper.getVoteTotal(event, nextInLine, activeMap);

                while (voteInfo[0] < 1 && !nextInLine.getColor().equalsIgnoreCase(player.getColor())) {
                    String skippedMessage = realIdentity2+"You are being skipped because you either have 0 votes or have ridered";
                    if (activeMap.isFoWMode()) {
                        MessageHelper.sendPrivateMessageToPlayer(nextInLine, activeMap, skippedMessage);
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(),skippedMessage);
                    }
                    player = nextInLine;
                    nextInLine = AgendaHelper.getNextInLine(nextInLine, AgendaHelper.getVotingOrder(activeMap), activeMap);
                    realIdentity2 =Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);
                    voteInfo = AgendaHelper.getVoteTotal(event, nextInLine, activeMap);
                }




                if (!nextInLine.getColor().equalsIgnoreCase(player.getColor())) {
                    String realIdentity = "";
                    realIdentity =Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);
                    String pFaction = StringUtils.capitalize(nextInLine.getFaction());
                    message = AgendaHelper.getSummaryOfVotes(activeMap, true) + "\n \n "+ realIdentity + message;
                    Button Vote= Button.success("vote", pFaction+" Choose To Vote");
                    Button Abstain = Button.danger("delete_buttons_0", pFaction+" Choose To Abstain");
                    Button EraseVote = Button.danger("FFCC_"+pFaction+"_eraseMyVote", pFaction+" Erase Any Of Your Previous Votes");

                    List<Button> buttons = List.of(Vote, Abstain, EraseVote);
                    if (activeMap.isFoWMode()) {
                        if (nextInLine.getPrivateChannel() != null) {
                            MessageHelper.sendMessageToChannelWithButtons(nextInLine.getPrivateChannel(), message, buttons);
                            event.getChannel().sendMessage("Notified next in line").queue();
                        }
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                    }
                } else {
                    String summary = AgendaHelper.getSummaryOfVotes(activeMap, false);
                    winner = AgendaHelper.getWinner(summary);
                    if (winner != null && !winner.contains("_")) {
                        resolveTime = true;
                    } else {
                        Player speaker = null;
                        if (activeMap.getPlayer(activeMap.getSpeaker()) != null) {
                            speaker = activeMap.getPlayers().get(activeMap.getSpeaker());
                        } else {
                            speaker = null;
                        }

                        List<Button> tiedWinners = new ArrayList<Button>();
                        if (winner != null) {
                            StringTokenizer winnerInfo = new StringTokenizer(winner, "_");
                            while (winnerInfo.hasMoreTokens()) {
                                String tiedWinner = winnerInfo.nextToken();
                                Button button = Button.primary("delete_buttons_outcomeTie_"+tiedWinner, tiedWinner);
                                tiedWinners.add(button);
                            }
                        } else {
                            event.getChannel().sendMessage("Please try the voting process again and cast at least one vote for something. Ping Fin to tell him to fix this.").queue();;
                            Button button = Button.primary("placeholder", "Unfortunate Dead End");
                            tiedWinners.add(button);
                        }
                        if (!tiedWinners.isEmpty()) {
                            MessageChannel channel = null;
                            if (activeMap.isFoWMode()) {
                                channel = speaker == null ? null : speaker.getPrivateChannel();
                                if (channel == null) {
                                    MessageHelper.sendMessageToChannel(event.getChannel(), "Speaker is not assigned for some reason. Please decide the winner.");
                                }
                            } else {
                                channel = event.getChannel();
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel, Helper.getPlayerRepresentation(speaker, activeMap, event.getGuild(), true)+ " please decide the winner.", tiedWinners);
                        }
                    }
                }
            } else {
                resolveTime = true;
                winner = buttonID.substring(buttonID.lastIndexOf("_")+1, buttonID.length());
            }
            if (resolveTime) {
                List<Player> losers = AgendaHelper.getLosers(winner, activeMap);
                String summary2 = AgendaHelper.getSummaryOfVotes(activeMap, true);
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), summary2 + "\n \n");

                String resMessage = "Please hold while people resolve shenanigans. "+losers.size() +" players have the opportunity to play deadly plot/bribery.";
                if ((!activeMap.isACInDiscard("Bribery") || !activeMap.isACInDiscard("Deadly Plot")) && losers.size() > 0) {
                    Button noDeadly = Button.primary("genericReact1", "No Deadly Plot");
                    Button noBribery = Button.primary("genericReact2", "No Bribery");
                    List<Button> deadlyActionRow = List.of(noBribery, noDeadly);


                    MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), resMessage,deadlyActionRow);
                    if (!activeMap.isFoWMode()) {
                        String loseMessage = "";
                        for (Player los : losers) {
                            if (los != null) {
                                loseMessage = loseMessage + Helper.getPlayerRepresentation(los, activeMap, event.getGuild(), true);
                            }
                        }
                        event.getChannel().sendMessage(loseMessage + " Please respond to bribery/deadly plot window").queue();
                    } else {
                        MessageHelper.privatelyPingPlayerList(losers, activeMap, "Please respond to bribery/deadly plot window");
                    }
                } else {
                    activeMap.getMainGameChannel().sendMessage("Either both bribery and deadly plot were in the discard or noone could legally play them.").queue();
                }
                if (activeMap.getCurrentAgendaInfo().contains("Elect Player") && (!activeMap.isACInDiscard("Confounding") || !activeMap.isACInDiscard("Confusing"))) {
                    String resMessage2 =Helper.getGamePing(activeMap.getGuild(), activeMap) + " please react to no confusing/confounding";
                    Button noConfounding = Button.primary("genericReact3", "Refuse Confounding Legal Text");
                    Button noConfusing = Button.primary("genericReact4", "Refuse Confusing Legal Text");
                    List<Button> buttons = List.of(noConfounding, noConfusing);
                    MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), resMessage2, buttons);

                } else {
                    if (activeMap.getCurrentAgendaInfo().contains("Elect Player")) {
                        activeMap.getMainGameChannel().sendMessage("Both confounding and confusing are in the discard pile. ").queue();

                    }
                }

                String resMessage3 = "Current winner is "+StringUtils.capitalize(winner) + ". "+Helper.getGamePing(activeMap.getGuild(), activeMap) + "When shenanigans have concluded, please confirm resolution or discard the result and manually resolve it yourselves.";
                Button autoResolve = Button.primary("agendaResolution_"+winner, "Resolve with current winner");
                Button manualResolve = Button.danger("autoresolve_manual", "Resolve it Manually");
                List<Button> deadlyActionRow3 = List.of(autoResolve, manualResolve);
                MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), resMessage3,deadlyActionRow3);


            }
            if (!votes.equalsIgnoreCase("0")) {
                event.getMessage().delete().queue();
            }
            MapSaveLoadManager.saveMap(activeMap, event);

        }
        else if (buttonID.startsWith("fixerVotes_")) {
            String voteMessage = "Thank you for specifying, please select from the available buttons to vote.";
            String vote = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            int votes =Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(votes-9,votes);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,voteActionRow);
            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("planetOutcomes_")) {
            String factionOrColor = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            Player planetOwner = Helper.getPlayerFromColorOrFaction(activeMap, factionOrColor);
            String voteMessage= "Chose to vote for one of "+factionOrColor +"'s planets. Click buttons for which outcome to vote for.";
            List<Button> outcomeActionRow = null;

            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeMap);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,outcomeActionRow);

            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("distinguished_")) {
            String voteMessage = "You added 5 votes to your total. Please select from the available buttons to vote.";
            String vote = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            int votes =Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(votes,votes+5);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,voteActionRow);

            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("outcome_")) {
            String outcome = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            String voteMessage= "Chose to vote for "+StringUtils.capitalize(outcome)+". Click buttons for amount of votes";
            activeMap.setLatestOutcomeVotedFor(outcome);

            int[] voteArray = AgendaHelper.getVoteTotal(event, player, activeMap);

            int minvote = 1;
            if (player.getFaction().equalsIgnoreCase("argent")) {
                int numPlayers = 0;
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_.isRealPlayer()) numPlayers++;
                }
                minvote = minvote+numPlayers;
            }
            if (voteArray[0]-minvote > 20) {
                voteMessage= "Chose to vote for "+StringUtils.capitalize(outcome)+". You have more votes than discord has buttons. Please further specify your desired vote count by clicking the button which contains your desired vote amount (or largest button).";
            }
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(minvote,voteArray[0]);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,voteActionRow);

            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("votes_")) {
            String votes = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            String voteMessage= "Chose to vote  "+votes+" votes for "+StringUtils.capitalize(activeMap.getLatestOutcomeVotedFor())+ ". Click buttons to choose which planets to exhaust for votes";

            List<Button> voteActionRow = AgendaHelper.getPlanetButtons(event, player, activeMap);
            int allVotes = AgendaHelper.getVoteTotal(event, player, activeMap)[0];
            Button exhausteverything = Button.danger("exhaust_everything_"+allVotes, "Exhaust everything ("+allVotes+")");
            Button concludeExhausting = Button.danger( finsFactionCheckerPrefix+"delete_buttons_"+votes, "Done exhausting planets.");
            Button OopsMistake = Button.success("refreshVotes_"+votes, "I made a mistake and want to ready some planets");
            voteActionRow.add(exhausteverything);
            voteActionRow.add(concludeExhausting);
            voteActionRow.add(OopsMistake);
            String voteMessage2 = "";
            MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);

            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage2,voteActionRow);

            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("autoresolve_")) {
            String result = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            if (result.equalsIgnoreCase("manual")) {
                String resMessage3 = "Please select the winner.";
                List<Button> deadlyActionRow3 = AgendaHelper.getAgendaResButtons(activeMap);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), resMessage3,deadlyActionRow3);
            }
            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("reverse_")) {
            String choice= buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            String voteMessage= "Chose to reverse latest rider on "+choice;

            HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
            String existingData = outcomes.getOrDefault(choice, "empty");
            if (existingData.equalsIgnoreCase("empty")) {
                voteMessage = "Something went wrong. Ping Fin and continue onwards.";
            } else {
                int lastSemicolon = existingData.lastIndexOf(";");
                if (lastSemicolon < 0) {
                    existingData = "";
                } else {
                    existingData = existingData.substring(0, lastSemicolon);
                }

            }
            activeMap.setCurrentAgendaVote(choice, existingData);
            event.getChannel().sendMessage(voteMessage).queue();
            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("rider_")) {
            String choice = buttonID.substring(buttonID.indexOf("_")+1, buttonID.lastIndexOf("_"));
            String rider = buttonID.substring(buttonID.lastIndexOf("_")+1,buttonID.length());
            String voteMessage= "Chose to put a "+rider+ " on "+StringUtils.capitalize(choice);
            String identifier = "";
            if (activeMap.isFoWMode()) {
                identifier =  player.getColor();
            } else {
                identifier =  player.getFaction();
            }
            HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
            String existingData = outcomes.getOrDefault(choice, "empty");
            if (existingData.equalsIgnoreCase("empty")) {
                existingData = identifier + "_" + rider;
            } else {
                existingData = existingData + ";" + identifier + "_" + rider;
            }
            activeMap.setCurrentAgendaVote(choice, existingData);

            if (!rider.equalsIgnoreCase("Non-AC Rider")) {
                List<Button> voteActionRow = new ArrayList<Button>();
                Button concludeExhausting = Button.danger("reverse_"+choice, "Click this if the rider is sabod");
                voteActionRow.add(concludeExhausting);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,voteActionRow);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
            }

            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("construction_")) {
            player.addFollowedSC(4);
            addReaction(event, false, false, "", "");
            String unit = buttonID.replace("construction_", "");
            String trueIdentity = Helper.getPlayerRepresentation(player, activeMap, event.getGuild(), true);
            String message = trueIdentity + " Click the name of the planet you wish to put your unit on";

            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(event, player, activeMap, unit);
            if (!activeMap.isFoWMode()) {
                List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                if (threadChannels == null) return;
                String threadName = activeMap.getName()+"-round-"+activeMap.getRound()+"-construction";
                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel)threadChannel_, message, buttons);
                    }
                }
            } else {
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
            }

        }
        else if (buttonID.startsWith("genericReact")) {
            String message = activeMap.isFoWMode() ? "Turned down window" : null;
            addReaction(event, false, false, message, "");
        }
        else if (buttonID.startsWith("place_")) {
            String unitNPlanet = buttonID.replace("place_","");
            String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
            String planetName = unitNPlanet.replace(unitLong+"_","");
            String unit = AliasHandler.resolveUnit(unitLong);

            String successMessage = "";
            String playerRep = Helper.getPlayerRepresentation(player, activeMap);
            if (unit.equalsIgnoreCase("sd")) {
                if (player.getFaction().equalsIgnoreCase("saar")) {
                    new AddUnits().unitParsing(event, player.getColor(), activeMap.getTile(AliasHandler.resolveTile(planetName)), unit, activeMap);
                    successMessage = "Placed a space dock in the space area of the "+Helper.getPlanetRepresentation(planetName, activeMap) +" system.";
                }
                else if (player.getFaction().equalsIgnoreCase("cabal")) {
                    new AddUnits().unitParsing(event, player.getColor(), activeMap.getTile(AliasHandler.resolveTile(planetName)), "csd "+planetName, activeMap);
                    successMessage = "Placed a cabal space dock on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(), activeMap.getTile(AliasHandler.resolveTile(planetName)), unit+" "+planetName, activeMap);
                    successMessage =  "Placed a "+ Helper.getEmojiFromDiscord("spacedock")+" on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                }
            }
            else if (unitLong.equalsIgnoreCase("pds")) {
                new AddUnits().unitParsing(event, player.getColor(), activeMap.getTile(AliasHandler.resolveTile(planetName)), unitLong+" "+planetName, activeMap);
                successMessage = "Placed a "+ Helper.getEmojiFromDiscord("pds")+" on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
            } else {
                if (unit.equalsIgnoreCase("gf") || unit.equalsIgnoreCase("mf") || unitLong.equalsIgnoreCase("2gf")) {
                    if (unitLong.equalsIgnoreCase("2gf")) {
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTile(AliasHandler.resolveTile(planetName)), "2 gf "+planetName, activeMap);
                        successMessage = "Produced 2 "+ Helper.getEmojiFromDiscord("infantry")+" on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                    } else {
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTile(AliasHandler.resolveTile(planetName)), unit+" "+planetName, activeMap);
                        successMessage = "Produced a "+Helper.getEmojiFromDiscord(unitLong) +" on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                    }
                } else {
                    if (unitLong.equalsIgnoreCase("2ff")) {
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName), "2 ff", activeMap);
                        successMessage = "Produced 2 "+Helper.getEmojiFromDiscord("fighter") +" in tile "+ AliasHandler.resolveTile(planetName) + ".";
                    } else {
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName), unit, activeMap);
                        successMessage = "Produced a "+Helper.getEmojiFromDiscord(unitLong) +" in tile "+ AliasHandler.resolveTile(planetName) + ".";
                    }

                }
            }



            if (unit.equalsIgnoreCase("sd") || unitLong.equalsIgnoreCase("pds")) {
                MessageHelper.sendMessageToChannel(event.getChannel(), playerRep+ " "+successMessage);
                String message = playerRep+" Would you like to put a cc from reinforcements in the same system?";
                Button placeCCInSystem= Button.success( finsFactionCheckerPrefix+"reinforcements_cc_placement_"+planetName, "Place A CC From Reinforcements In The System.");
                Button NoDontWantTo = Button.primary( finsFactionCheckerPrefix+"deleteButtons", "Don't Place A CC In The System.");
                List<Button> buttons = List.of(placeCCInSystem, NoDontWantTo);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                event.getMessage().delete().queue();

            } else {
                String editedMessage = event.getMessage().getContentRaw();
                if (editedMessage.contains("Produced")) {
                    editedMessage = editedMessage + "\n "+successMessage;
                } else {
                    editedMessage = playerRep+ " "+successMessage;
                }
                event.getMessage().editMessage(editedMessage).queue();
            }


        }
        else if(buttonID.startsWith("freelancersBuild_")){
            String planet = buttonID.replace("freelancersBuild_", "");        
            List<Button> buttons = new ArrayList<Button>();
            buttons = Helper.getPlaceUnitButtons(event, player, activeMap,  activeMap.getTile(AliasHandler.resolveTile(planet)), false);
            String message = Helper.getPlayerRepresentation(player, activeMap)+" Use the buttons to produce 1 unit.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        }
        else if(buttonID.startsWith("scPick_")){
            Stats stats = new Stats();
            String num = buttonID.replace("scPick_", "");
            int scpick = Integer.parseInt(num);
            boolean pickSuccessful = stats.secondHalfOfPickSC((GenericInteractionCreateEvent)event, activeMap, player, scpick);
            if(pickSuccessful)
            {
                new SCPick().secondHalfOfSCPick((GenericInteractionCreateEvent)event, player, activeMap, scpick);
                event.getMessage().delete().queue();
            }
            
            
        }

       
        else if (buttonID.startsWith("reinforcements_cc_placement_")) {
            String playerRep = Helper.getPlayerRepresentation(player, activeMap);
            String planet = buttonID.replace("reinforcements_cc_placement_", "");
            String tileID = AliasHandler.resolveTile(planet.toLowerCase());
            Tile tile = activeMap.getTile(tileID);
            if (tile == null) {
                tile = activeMap.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
                return;
            }
            String color = player.getColor();
            if (Mapper.isColorValid(color)) {
                AddCC.addCC(event, color, tile);
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), playerRep+" Placed A CC From Reinforcements In The "+Helper.getPlanetRepresentation(planet, activeMap) + " system");
            event.getMessage().delete().queue();
        }
        else if (buttonID.startsWith("topAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            new PutAgendaTop().putTop((GenericInteractionCreateEvent)event, Integer.parseInt(agendaNumID), activeMap);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Put "+agendaNumID + " on the top of the agenda deck.");
        }
        else if (buttonID.startsWith("bottomAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_")+1, buttonID.length());
            new PutAgendaBottom().putBottom((GenericInteractionCreateEvent) event, Integer.parseInt(agendaNumID), activeMap);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Put "+agendaNumID + " on the bottom of the agenda deck.");
        }
        else if (buttonID.startsWith("agendaResolution_")) {
            String winner = buttonID.substring(buttonID.indexOf("_")+1,buttonID.length());
            String agendaid = activeMap.getCurrentAgendaInfo().substring(activeMap.getCurrentAgendaInfo().lastIndexOf("_")+1,activeMap.getCurrentAgendaInfo().length());
            int aID = 0;
            if(agendaid.equalsIgnoreCase("CL"))
            {
                String id2 = activeMap.revealAgenda(false);
                LinkedHashMap<String, Integer> discardAgendas = activeMap.getDiscardAgendas();
                Integer uniqueID = discardAgendas.get(id2);
                aID = uniqueID;
            }
            else
            {
                aID = Integer.parseInt(agendaid);
            }
           
            if (activeMap.getCurrentAgendaInfo().startsWith("Law")) {
                if (activeMap.getCurrentAgendaInfo().contains("Player")) {
                    Player player2 = Helper.getPlayerFromColorOrFaction(activeMap, winner);
                    if (player2 != null) {
                        activeMap.addLaw(aID, winner);
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Added Law with "+winner+" as the elected!");
                } else {
                    if (winner.equalsIgnoreCase("for")) {
                        activeMap.addLaw(aID, null);
                        MessageHelper.sendMessageToChannel(event.getChannel(), "Added law to map!");
                    }
                    if (activeMap.getCurrentAgendaInfo().contains("Secret")) {
                        activeMap.addLaw(aID, winner);
                        int soID = 0;
                        String soName = winner;
                        Player playerWithSO = null;


                        for (java.util.Map.Entry<String, Player> playerEntry : activeMap.getPlayers().entrySet()) {
                            Player player_ = playerEntry.getValue();
                            LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>(player_.getSecretsScored());
                            for (java.util.Map.Entry<String, Integer> soEntry : secretsScored.entrySet()) {
                                if (soEntry.getKey().equals(soName)) {
                                    soID = soEntry.getValue();
                                    playerWithSO = player_;
                                    break;
                                }
                            }
                        }

                        if (playerWithSO == null) {
                            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
                            return;
                        }
                        if (soName.isEmpty()) {
                            MessageHelper.sendMessageToChannel(event.getChannel(), "Can make just Scored SO to Public");
                            return;
                        }
                        activeMap.addToSoToPoList(soName);
                        Integer poIndex = activeMap.addCustomPO(soName, 1);
                        activeMap.scorePublicObjective(playerWithSO.getUserID(), poIndex);

                        String sb = "**Public Objective added from Secret:**" + "\n" +
                                "(" + poIndex + ") " + "\n" +
                                Mapper.getSecretObjectivesJustNames().get(soName) + "\n";
                        MessageHelper.sendMessageToChannel(event.getChannel(), sb);

                        SOInfo.sendSecretObjectiveInfo(activeMap, playerWithSO, event);

                    }
                }
            } else {
                if (activeMap.getCurrentAgendaInfo().contains("Law")) {
                    //Figure out law
                }
            }
            String summary = AgendaHelper.getSummaryOfVotes(activeMap, false);
            List<Player> riders = AgendaHelper.getWinningRiders(summary, winner, activeMap);
            String ridSum = " has a rider to resolve.";
            for (Player rid : riders) {
                String rep = Helper.getPlayerRepresentation(rid, activeMap, event.getGuild(), true);
                if (rid != null) {
                    if (activeMap.isFoWMode()) {
                        String message = rep + "You have a rider to resolve";
                        if (rid.getFaction().equalsIgnoreCase("nomad")) {
                            message = rep + "You have a rider to resolve or you voted for the correct outcome. Either way a tg has been added to your total. ("+rid.getTg()+"-->"+(rid.getTg()+1)+")";
                            rid.setTg(rid.getTg()+1);
                        }
                        MessageHelper.sendPrivateMessageToPlayer(rid, activeMap, message);
                    } else {
                        ridSum = rep+ridSum;
                        if (rid.getFaction().equalsIgnoreCase("nomad")) {
                            String message2 = rep + "As Nomad, you have a rider to resolve or you voted for the correct outcome. Either way a tg has been added to your total due to your foresight ability. ("+rid.getTg()+"-->"+(rid.getTg()+1)+")";
                            rid.setTg(rid.getTg()+1);
                            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message2);
                        }
                    }

                }
            }
            if (activeMap.isFoWMode()) {
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), "Sent pings to all those who ridered");
            } else {
                if (riders.size() > 0) {
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), ridSum);
                }

            }
            String voteMessage = "Resolving vote for "+ StringUtils.capitalize(winner) +". Click the buttons for next steps after you're done resolving riders.";

            Button flipNextAgenda = Button.primary("flip_agenda", "Flip Agenda");
            Button proceedToStrategyPhase = Button.success("proceed_to_strategy", "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
            List<Button> resActionRow = List.of(flipNextAgenda, proceedToStrategyPhase);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,resActionRow);

            event.getMessage().delete().queue();

        } else {
            switch (buttonID) {
                //AFTER THE LAST PLAYER PASS COMMAND, FOR SCORING
                case Constants.PO_NO_SCORING -> {
                    String message = Helper.getPlayerRepresentation(player, activeMap) + " - no Public Objective scored.";
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                    }
                    String reply = activeMap.isFoWMode() ? "No public objective scored" : null;
                    addReaction(event, false, false, reply, "");
                }
                case "warfareBuild" -> {
                    List<Button> buttons = new ArrayList<Button>();
                    buttons = Helper.getPlaceUnitButtons(event, player, activeMap,  activeMap.getTile(AliasHandler.resolveTile(player.getFaction())), true);
                    String message = Helper.getPlayerRepresentation(player, activeMap)+" Use the buttons to produce. Reminder that when following warfare, you can only use 1 dock in your home system.";
                    if (!activeMap.isFoWMode()) {
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        if (threadChannels == null) return;
                        String threadName = activeMap.getName()+"-round-"+activeMap.getRound()+"-warfare";
                        // SEARCH FOR EXISTING OPEN THREAD
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)threadChannel_, message, buttons);
                            }
                        }
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "acquireATech" -> {

                    List<Button> buttons = new ArrayList<Button>();
                    Button bioticTech = Button.success( finsFactionCheckerPrefix+"getAllTechOfType_biotic", "Get A Green Tech");
                    bioticTech= bioticTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Biotictech")));
                    buttons.add(bioticTech);
                     Button warfareTech = Button.danger( finsFactionCheckerPrefix+"getAllTechOfType_warfare", "Get A Red Tech");
                    warfareTech= warfareTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Warfaretech")));
                    buttons.add(warfareTech);
                     Button propulsionTech = Button.primary( finsFactionCheckerPrefix+"getAllTechOfType_propulsion", "Get A Blue Tech");
                    propulsionTech = propulsionTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Propulsiontech")));
                    buttons.add(propulsionTech);
                     Button cyberneticTech = Button.secondary( finsFactionCheckerPrefix+"getAllTechOfType_cybernetic", "Get A Yellow Tech");
                    cyberneticTech=cyberneticTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Cybernetictech")));
                    buttons.add(cyberneticTech);
                     Button unitupgradesTech = Button.secondary( finsFactionCheckerPrefix+"getAllTechOfType_unitupgrade", "Get A Unit Upgrade Tech");
                    unitupgradesTech=unitupgradesTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("UnitUpgradeTech")));


                    buttons.add(unitupgradesTech);
                    String message = Helper.getPlayerRepresentation(player, activeMap)+" What type of tech would you want?";
                    if (!activeMap.isFoWMode()) {
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        if (threadChannels == null) return;
                        String threadName = activeMap.getName()+"-round-"+activeMap.getRound()+"-technology";
                        // SEARCH FOR EXISTING OPEN THREAD
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)threadChannel_, message, buttons);
                            }
                        }
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case Constants.SO_NO_SCORING -> {
                    String message = Helper.getPlayerRepresentation(player, activeMap) + " - no Secret Objective scored.";
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                    }
                    String reply = activeMap.isFoWMode() ? "No secret objective scored" : null;
                    addReaction(event, false, false, reply, "");
                }
                //AFTER AN ACTION CARD HAS BEEN PLAYED
                case "no_sabotage" -> {
                    String message = activeMap.isFoWMode() ? "No sabotage" : null;
                    addReaction(event, false, false, message, "");
                }
                case "proceedToVoting" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Decided to skip waiting for afters and proceed to voting.");
                    if (activeMap.getLatestOutcomeVotedFor() != null && activeMap.getLatestOutcomeVotedFor() != "") {
                        activeMap.getMainGameChannel().deleteMessageById(activeMap.getLatestOutcomeVotedFor()).queue();
                    }
                    AgendaHelper.startTheVoting(activeMap, event);
                    event.getMessage().delete().queue();
                }
                case "drawAgenda_2" -> {
                    new DrawAgenda().drawAgenda(event, 2, activeMap, player);
                    event.getMessage().delete().queue();
                }
                case "deleteButtons" -> {
                    if (buttonLabel.equalsIgnoreCase("Done Gaining CCs") || buttonLabel.equalsIgnoreCase("Done Redistributing CCs")) {

                        String playerRep = Helper.getPlayerRepresentation(player, activeMap);
                        String finalCCs = player.getTacticalCC() + "/"+ player.getFleetCC() + "/" + player.getStrategicCC();
                        if (event.getMessage().getContentRaw().contains("Net gain")) {
                            String editedMessage = event.getMessage().getContentRaw();
                            int netGain = Integer.parseInt(editedMessage.substring(editedMessage.lastIndexOf(":")+2, editedMessage.length()));
                            finalCCs = finalCCs + ". Net CC gain was "+netGain;
                        }
                        MessageHelper.sendMessageToChannel(event.getChannel(), playerRep + " Final CC Allocation Is "+ finalCCs);
                    }
                    if (buttonLabel.equalsIgnoreCase("Done Exhausting Planets") || buttonLabel.equalsIgnoreCase("Done Producing Units")) {
                        String editedMessage = event.getMessage().getContentRaw();
                        MessageHelper.sendMessageToChannel(event.getChannel(), editedMessage);
                        if (buttonLabel.equalsIgnoreCase("Done Producing Units")) {
                            String trueIdentity = Helper.getPlayerRepresentation(player, activeMap, event.getGuild(), true);
                            String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";

                            List<Button> buttons = Helper.getPlanetExhaustButtons(event, player, activeMap);
                            if (player.getTg() > 0) {
                                Button lost1TG = Button.danger("reduceTG_1", "Spend 1 TG");
                                buttons.add(lost1TG);
                            }
                            if (player.getTg() > 1) {
                                Button lost2TG = Button.danger("reduceTG_2", "Spend 2 TGs");
                                buttons.add(lost2TG);
                            }
                            if (player.getTg() > 2) {
                                Button lost3TG = Button.danger("reduceTG_3", "Spend 3 TGs");
                                buttons.add(lost3TG);
                            }
                            Button DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
                            buttons.add(DoneExhausting);
                            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);

                        }
                    }

                    event.getMessage().delete().queue();
                }
                case "diploRefresh2" -> {
                    player.addFollowedSC(2);
                    addReaction(event, false, false, "", "");
                    String trueIdentity = Helper.getPlayerRepresentation(player, activeMap, event.getGuild(), true);
                    String message = trueIdentity + " Click the names of the planets you wish to ready";

                    List<Button> buttons = Helper.getPlanetRefreshButtons(event, player, activeMap);
                    Button DoneRefreshing = Button.danger("deleteButtons", "Done Readying Planets");
                    buttons.add(DoneRefreshing);
                    if (!activeMap.isFoWMode()) {
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        if (threadChannels == null) return;
                        String threadName = activeMap.getName()+"-round-"+activeMap.getRound()+"-diplomacy";
                        // SEARCH FOR EXISTING OPEN THREAD
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)threadChannel_, message, buttons);
                            }
                        }
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "leadershipExhaust" -> {
                    addReaction(event, false, false, "", "");
                    String trueIdentity = Helper.getPlayerRepresentation(player, activeMap, event.getGuild(), true);
                    String message = trueIdentity + " Click the names of the planets you wish to exhaust.";

                    List<Button> buttons = Helper.getPlanetExhaustButtons(event, player, activeMap);

                    if (player.getTg() > 0) {
                        Button lost1TG = Button.danger("reduceTG_1", "Spend 1 TG");
                        buttons.add(lost1TG);
                    }
                    if (player.getTg() > 1) {
                        Button lost2TG = Button.danger("reduceTG_2", "Spend 2 TGs");
                        buttons.add(lost2TG);
                    }
                    if (player.getTg() > 2) {
                        Button lost3TG = Button.danger("reduceTG_3", "Spend 3 TGs");
                        buttons.add(lost3TG);
                    }
                    Button DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
                    buttons.add(DoneExhausting);
                    if (!activeMap.isFoWMode()) {
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        if (threadChannels == null) return;
                        String threadName = activeMap.getName()+"-round-"+activeMap.getRound()+"-leadership";
                        // SEARCH FOR EXISTING OPEN THREAD
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)threadChannel_, message, buttons);
                            }
                        }
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "redistributeCCButtons" -> {

                    String trueIdentity = Helper.getPlayerRepresentation(player, activeMap, event.getGuild(), true);
                    String message = trueIdentity + "! Your current CCs are "+Helper.getPlayerCCs(player)+". Use buttons to gain CCs";

                    Button getTactic= Button.success( finsFactionCheckerPrefix+"increase_tactic_cc", "Gain 1 Tactic CC");
                    Button getFleet = Button.success( finsFactionCheckerPrefix+"increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat= Button.success( finsFactionCheckerPrefix+"increase_strategy_cc", "Gain 1 Strategy CC");
                    Button loseTactic= Button.danger( finsFactionCheckerPrefix+"decrease_tactic_cc", "Lose 1 Tactic CC");
                    Button loseFleet = Button.danger( finsFactionCheckerPrefix+"decrease_fleet_cc", "Lose 1 Fleet CC");
                    Button loseStrat= Button.danger( finsFactionCheckerPrefix+"decrease_strategy_cc", "Lose 1 Strategy CC");

                    Button DoneGainingCC = Button.danger( finsFactionCheckerPrefix+"deleteButtons", "Done Redistributing CCs");
                    List<Button> buttons = List.of(getTactic, getFleet, getStrat, loseTactic, loseFleet, loseStrat, DoneGainingCC);
                    if (!activeMap.isFoWMode()) {

                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);

                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }

                case "leadershipGenerateCCButtons" -> {
                    player.addFollowedSC(1);
                    addReaction(event, false, false, "", "");
                    String trueIdentity = Helper.getPlayerRepresentation(player, activeMap, event.getGuild(), true);
                    String message = trueIdentity + "! Your current CCs are "+Helper.getPlayerCCs(player)+". Use buttons to gain CCs";

                    Button getTactic= Button.success( finsFactionCheckerPrefix+"increase_tactic_cc", "Gain 1 Tactic CC");
                    Button getFleet = Button.success( finsFactionCheckerPrefix+"increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat= Button.success( finsFactionCheckerPrefix+"increase_strategy_cc", "Gain 1 Strategy CC");
                    Button exhaust = Button.danger( finsFactionCheckerPrefix+"leadershipExhaust", "Exhaust Planets");
                    Button DoneGainingCC = Button.danger( finsFactionCheckerPrefix+"deleteButtons", "Done Gaining CCs");
                    List<Button> buttons = List.of(getTactic, getFleet, getStrat, exhaust, DoneGainingCC);
                    if (!activeMap.isFoWMode()) {
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        if (threadChannels == null) return;
                        String threadName = activeMap.getName()+"-round-"+activeMap.getRound()+"-leadership";
                        // SEARCH FOR EXISTING OPEN THREAD
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)threadChannel_, message, buttons);
                            }
                        }
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "diploSystem" -> {
                    String trueIdentity = Helper.getPlayerRepresentation(player, activeMap, event.getGuild(), true);
                    String message = trueIdentity + " Click the name of the planet who's system you wish to diplo";

                    List<Button> buttons = Helper.getPlanetSystemDiploButtons(event, player, activeMap);
                    if (!activeMap.isFoWMode()) {
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        if (threadChannels == null) return;
                        String threadName = activeMap.getName()+"-round-"+activeMap.getRound()+"-diplomacy";
                        // SEARCH FOR EXISTING OPEN THREAD
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannelWithButtons((MessageChannel)threadChannel_, message, buttons);
                            }
                        }
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }


                }
                case "sc_ac_draw" -> {
                    boolean used = addUsedSCPlayer(messageID + "ac", activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    boolean hasSchemingAbility = player.hasAbility("scheming");
                    String message = hasSchemingAbility ? "Drew 3 Actions Cards (Scheming) - please discard an Action Card from your hand" : "Drew 2 Actions cards";
                    int count = hasSchemingAbility ? 3 : 2;
                    for (int i = 0; i < count; i++) {
                        activeMap.drawActionCard(player.getUserID());
                    }
                    ACInfo.sendActionCardInfo(activeMap, player, event);
                    addReaction(event, false, false, message, "");
                }
                case "sc_draw_so" -> {
                    boolean used = addUsedSCPlayer(messageID + "so", activeMap, player, event, " Drew a " + Emojis.SecretObjective);
                    if (used) {
                        break;
                    }
                    String message = "Drew Secret Objective";
                    activeMap.drawSecretObjective(player.getUserID());
                    player.addFollowedSC(8);
                    SOInfo.sendSecretObjectiveInfo(activeMap, player, event);
                    addReaction(event, false, false, message, "");
                }
                case "sc_trade_follow" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    String message = deductCC(player, event);
                    player.addFollowedSC(5);
                    player.setCommodities(player.getCommoditiesTotal());
                    addReaction(event, false, false, message, "");
                    addReaction(event, false, false, "Replenishing Commodities", "");
                }
                case "flip_agenda" -> {
                    new RevealAgenda().revealAgenda(event, false, activeMap, event.getChannel());
                    event.getMessage().delete().queue();

                }
                case "hack_election" -> {
                    activeMap.setHackElectionStatus(true);
                    MessageHelper.sendMessageToChannel(event.getChannel(),"Reversed voting order.");
                    event.getMessage().delete().queue();
                }
                case "proceed_to_strategy" -> {
                    LinkedHashMap<String, Player> players = activeMap.getPlayers();
                    for (Player player_ : players.values()) {
                        player_.cleanExhaustedPlanets(false);
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda cleanup run!");
                    Player speaker = null;
                    if (activeMap.getPlayer(activeMap.getSpeaker()) != null) {
                        speaker = activeMap.getPlayers().get(activeMap.getSpeaker());
                    } else {
                        speaker = null;
                    }
                    String message =  Helper.getPlayerRepresentation(speaker, activeMap, event.getGuild(), true) + " UP TO PICK SC";
                    if (activeMap.isFoWMode()) {
                        MessageHelper.sendPrivateMessageToPlayer(speaker, activeMap, message);
                        if(!activeMap.isHomeBrewSCMode())
                        {
                            MessageHelper.sendMessageToChannelWithButtons(speaker.getPrivateChannel(), "Use Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeMap));
                        }
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                        if(!activeMap.isHomeBrewSCMode())
                        {
                            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Use Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeMap));
                        }

                    }
                    event.getMessage().delete().queue();

                }
                case "sc_follow_trade" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    String message = deductCC(player, event);
                    player.addFollowedSC(5);
                    player.setCommodities(player.getCommoditiesTotal());
                    addReaction(event, false, false, message, "");
                    addReaction(event, false, false, "Replenishing Commodities", "");
                }
                case "sc_follow_leadership" -> {
                    String message = Helper.getPlayerPing(player) + " following.";
                    player.addFollowedSC(1);
                    addReaction(event, false, false, message, "");
                }
                case "sc_leadership_follow" -> {
                    String message = Helper.getPlayerPing(player) + " following.";
                    player.addFollowedSC(1);
                    addReaction(event, false, false, message, "");
                }
                case "sc_refresh" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "Replenish");
                    if (used) {
                        break;
                    }
                    player.setCommodities(player.getCommoditiesTotal());
                    player.addFollowedSC(5);
                    addReaction(event, false, false, "Replenishing Commodities", "");
                }
                case "sc_refresh_and_wash" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "Replenish and Wash");
                    if (used) {
                        break;
                    }
                    int commoditiesTotal = player.getCommoditiesTotal();
                    int tg = player.getTg();
                    player.setTg(tg + commoditiesTotal);
                    player.setCommodities(0);
                    player.addFollowedSC(5);
                    addReaction(event, false, false, "Replenishing and washing", "");
                }
                case "sc_follow" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    String message = deductCC(player, event);
                    int scnum = 1;
                    boolean setstatus = true;
                    try {
                        scnum = Integer.parseInt(lastchar);
                    } catch (NumberFormatException e) {
                        setstatus = false;
                    }
                    if (setstatus) {
                        player.addFollowedSC(scnum);
                    }
                    addReaction(event, false, false, message, "");

                }
                case "trade_primary" -> {
                    if (!player.getSCs().contains(5)) {
                        break;
                    }
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "Trade Primary");
                    if (used) {
                        break;
                    }
                    int tg = player.getTg();
                    player.setTg(tg + 3);
                    player.setCommodities(player.getCommoditiesTotal());
                    addReaction(event, false, false, " gained 3" + Emojis.tg + " and replenished commodities (" + String.valueOf(player.getCommodities()) + Emojis.comm + ")", "");
                }
                case "score_imperial" -> {
                    if (player == null || activeMap == null) {
                        break;
                    }
                    if (!player.getSCs().contains(8)) {
                        MessageHelper.sendMessageToChannel(privateChannel, "Only the player who has " + Helper.getSCBackRepresentation(event, 8) + " can score the Imperial point");
                        break;
                    }
                    boolean used = addUsedSCPlayer(messageID + "score_imperial", activeMap, player, event, " scored Imperial");
                    if (used) {
                        break;
                    }
                    ScorePublic.scorePO(event, privateChannel, activeMap, player, 0);
                }
                //AFTER AN AGENDA HAS BEEN REVEALED
                case "play_when" -> {
                    clearAllReactions(event);
                    addReaction(event, true, true, "Playing When", "When Played");
                    Button playWhen = Button.danger("play_when", "Play When");
                    Button noWhen = Button.primary("no_when", "No Whens").withEmoji(Emoji.fromFormatted(Emojis.noafters));
                    Button noWhenPersistent = Button.primary("no_when_persistent", "No Whens No Matter What (for this agenda)").withEmoji(Emoji.fromFormatted(Emojis.noafters));
                    List<Button> whenButtons = new ArrayList<>(List.of(playWhen, noWhen, noWhenPersistent));

                    MessageHelper.sendMessageToChannelWithPersistentReacts(actionsChannel, "Please indicate no whens again.", activeMap, whenButtons, "when");
                    //addPersistentReactions(event, activeMap, "when");
                    event.getMessage().delete().queue();
                }
                case "no_when" -> {
                    String message = activeMap.isFoWMode() ? "No whens" : null;
                    addReaction(event, false, false, message, "");
                }
                case "play_after" -> {
                    clearAllReactions(event);

                    addReaction(event, true, true, "Playing A Non-AC Rider", "Non-AC Rider Played");

                    List<Button> riderButtons = AgendaHelper.getRiderButtons("Non-AC Rider", activeMap);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Please select your rider target", activeMap, player, riderButtons);

                    Button playAfter = Button.danger("play_after", "Play A Non-AC Rider");
                    Button noAfter = Button.primary("no_after", "No Afters").withEmoji(Emoji.fromFormatted(Emojis.noafters));
                    Button noAfterPersistent = Button.primary("no_after_persistent", "No Afters No Matter What (for this agenda)").withEmoji(Emoji.fromFormatted(Emojis.noafters));
                    List<Button> afterButtons = new ArrayList<>(List.of(playAfter, noAfter, noAfterPersistent));

                    MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no afters again.", activeMap, afterButtons, "after");
                    //addPersistentReactions(event, activeMap, "after");
                    event.getMessage().delete().queue();
                }
                case "no_after" -> {
                    String message = activeMap.isFoWMode() ? "No afters" : null;
                    addReaction(event, false, false, message, "");
                }
                case "no_after_persistent" -> {
                    String message = activeMap.isFoWMode() ? "No afters (locked in)" : null;
                    activeMap.addPlayersWhoHitPersistentNoAfter(player.getFaction());
                    addReaction(event, false, false, message, "");
                }
                case "no_when_persistent" -> {
                    String message = activeMap.isFoWMode() ? "No whens (locked in)" : null;
                    activeMap.addPlayersWhoHitPersistentNoWhen(player.getFaction());
                    addReaction(event, false, false, message, "");
                }
                case "gain_2_comms" -> {
                    if (player.getCommodities()+2 > player.getCommoditiesTotal()) {
                        player.setCommodities(player.getCommoditiesTotal());
                        addReaction(event, false, false, "Gained Commodities to Max", "");
                    } else {
                        player.setCommodities(player.getCommodities()+2);
                        addReaction(event, false, false, "Gained 2 Commodities", "");
                    }
                }
                case "covert_2_comms" -> {
                    if (player.getCommodities() > 1) {
                        player.setCommodities(player.getCommodities()-2);
                        player.setTg(player.getTg()+2);
                        addReaction(event, false, false, "Coverted 2 Commodities to 2 tg", "");
                    } else {
                        player.setTg(player.getTg()+player.getCommodities());
                        player.setCommodities(0);
                        addReaction(event, false, false, "Converted all remaining commodies (less than 2) into tg", "");
                    }
                }
                case "gain_1_comms" -> {
                    if (player.getCommodities()+1 > player.getCommoditiesTotal()) {
                        player.setCommodities(player.getCommoditiesTotal());
                        addReaction(event, false, false,"Gained No Commodities (at max already)", "");

                    } else {
                        player.setCommodities(player.getCommodities()+1);
                        addReaction(event, false, false,"Gained 1 Commodity", "");
                    }
                }
                case "comm_for_AC" -> {
                    boolean hasSchemingAbility = player.hasAbility("scheming");
                    int count2 = hasSchemingAbility ? 2 : 1;
                    if (player.getCommodities() > 0) {
                        player.setCommodities(player.getCommodities()-1);
                        for (int i = 0; i < count2; i++) {
                            activeMap.drawActionCard(player.getUserID());
                        }
                        ACInfo.sendActionCardInfo(activeMap, player, event);
                        String message = hasSchemingAbility ? "Spent 1 commodity to draw " + count2 + " Action Card (Scheming) - please discard an Action Card from your hand" : "Spent 1 commodity to draw " + count2 + " AC";
                        addReaction(event, false, false, message, "");
                        event.getMessage().delete().queue();
                    }
                    else if (player.getTg() > 0) {
                        player.setTg(player.getTg()-1);
                        for (int i = 0; i < count2; i++) {
                            activeMap.drawActionCard(player.getUserID());
                        }
                        ACInfo.sendActionCardInfo(activeMap, player, event);
                        addReaction(event, false, false,"Spent 1 tg for an AC", "");
                        event.getMessage().delete().queue();
                    } else {
                        addReaction(event, false, false,"Didn't have any comms/tg to spend, no AC drawn", "");
                    }

                }
                case "comm_for_mech" -> {
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ")+1, labelP.length());
                    if (player.getCommodities() > 0) {
                        player.setCommodities(player.getCommodities()-1);
                         new AddUnits().unitParsing(event, player.getColor(), activeMap.getTile(AliasHandler.resolveTile(planetName)), "mech "+planetName, activeMap);
                        addReaction(event, false, false, "Spent 1 commodity for a mech on "+planetName, "");
                        event.getMessage().delete().queue();
                    }
                    else if (player.getTg() > 0) {
                        player.setTg(player.getTg()-1);
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTile(AliasHandler.resolveTile(planetName)), "mech "+planetName, activeMap);
                        addReaction(event, false, false, "Spent 1 tg for a mech on "+ planetName, "");
                        event.getMessage().delete().queue();
                    } else {
                        addReaction(event, false, false, "Didn't have any comms/tg to spend, no mech placed", "");
                    }
                }
                case "increase_strategy_cc" -> {
                   // addReaction(event, false, false, "Increased Strategy Pool CCs By 1 ("+player.getStrategicCC()+"->"+(player.getStrategicCC()+1)+").", "");
                   String originalCCs = Helper.getPlayerCCs(player);
                   player.setStrategicCC(player.getStrategicCC()+1);
                   String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace("Your current CCs are "+originalCCs+". Use buttons to gain CCs", "CCs have gone from " +originalCCs+" -> " +Helper.getPlayerCCs(player)+". Net gain of: 1");
                    } else {
                        int netGain = Integer.parseInt(editedMessage.substring(editedMessage.lastIndexOf(":")+2, editedMessage.length())) +1;
                        editedMessage = editedMessage.substring(0,editedMessage.indexOf("->")+3) + Helper.getPlayerCCs(player)+". Net gain of: " +netGain;
                    }
                     event.getMessage().editMessage(editedMessage).queue();
                }
                case "increase_tactic_cc" -> {
                    String originalCCs = Helper.getPlayerCCs(player);
                    player.setTacticalCC(player.getTacticalCC()+1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace("Your current CCs are "+originalCCs+". Use buttons to gain CCs", "CCs have gone from " +originalCCs+" -> " +Helper.getPlayerCCs(player)+". Net gain of: 1");
                    } else {
                        int netGain = Integer.parseInt(editedMessage.substring(editedMessage.lastIndexOf(":")+2, editedMessage.length())) +1;
                        editedMessage = editedMessage.substring(0,editedMessage.indexOf("->")+3) + Helper.getPlayerCCs(player)+". Net gain of: " +netGain;
                    }
                     event.getMessage().editMessage(editedMessage).queue();
                }
                case "increase_fleet_cc" -> {
                   // addReaction(event, false, false, "Increased Fleet Pool CCs By 1 ("+player.getFleetCC()+"->"+(player.getFleetCC()+1)+").", "");
                   String originalCCs = Helper.getPlayerCCs(player);
                    player.setFleetCC(player.getFleetCC()+1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace("Your current CCs are "+originalCCs+". Use buttons to gain CCs", "CCs have gone from " +originalCCs+" -> " +Helper.getPlayerCCs(player)+". Net gain of: 1");
                    } else {
                        int netGain = Integer.parseInt(editedMessage.substring(editedMessage.lastIndexOf(":")+2, editedMessage.length())) +1;
                        editedMessage = editedMessage.substring(0,editedMessage.indexOf("->")+3) + Helper.getPlayerCCs(player)+". Net gain of: " +netGain;
                    }
                     event.getMessage().editMessage(editedMessage).queue();
                }

                case "decrease_strategy_cc" -> {
                  //  addReaction(event, false, false, "Decreased Strategy Pool CCs By 1 ("+player.getStrategicCC()+"->"+(player.getStrategicCC()-1)+").", "");
                  String originalCCs = Helper.getPlayerCCs(player);
                  player.setStrategicCC(player.getStrategicCC()-1);
                  String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace("Your current CCs are "+originalCCs+". Use buttons to gain CCs", "CCs have gone from " +originalCCs+" -> " +Helper.getPlayerCCs(player)+". Net gain of: -1");
                    } else {
                        int netGain = Integer.parseInt(editedMessage.substring(editedMessage.lastIndexOf(":")+2, editedMessage.length())) -1;
                        editedMessage = editedMessage.substring(0,editedMessage.indexOf("->")+3) + Helper.getPlayerCCs(player)+". Net gain of: " +netGain;
                    }
                     event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_tactic_cc" -> {
                   // addReaction(event, false, false, "Decreased Tactic Pool CCs By 1 ("+player.getTacticalCC()+ "->" +(player.getTacticalCC()-1)+").", "");
                   String originalCCs = Helper.getPlayerCCs(player);
                   player.setTacticalCC(player.getTacticalCC()-1);
                   String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace("Your current CCs are "+originalCCs+". Use buttons to gain CCs", "CCs have gone from " +originalCCs+" -> " +Helper.getPlayerCCs(player)+". Net gain of: -1");
                    } else {
                        int netGain = Integer.parseInt(editedMessage.substring(editedMessage.lastIndexOf(":")+2, editedMessage.length())) -1;
                        editedMessage = editedMessage.substring(0,editedMessage.indexOf("->")+3) + Helper.getPlayerCCs(player)+". Net gain of: " +netGain;
                    }
                     event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_fleet_cc" -> {
                   // addReaction(event, false, false, "Decreased Fleet Pool CCs By 1 ("+player.getFleetCC()+"->"+(player.getFleetCC()-1)+").", "");
                   String originalCCs = Helper.getPlayerCCs(player);
                    player.setFleetCC(player.getFleetCC()-1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace("Your current CCs are "+originalCCs+". Use buttons to gain CCs", "CCs have gone from " +originalCCs+" -> " +Helper.getPlayerCCs(player)+". Net gain of: -1");
                    } else {
                        int netGain = Integer.parseInt(editedMessage.substring(editedMessage.lastIndexOf(":")+2, editedMessage.length())) -1;
                        editedMessage = editedMessage.substring(0,editedMessage.indexOf("->")+3) + Helper.getPlayerCCs(player)+". Net gain of: " +netGain;
                    }
                     event.getMessage().editMessage(editedMessage).queue();
                }
                case "gain_1_tg" -> {

                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ")+1, labelP.length());
                    boolean failed = false;
                    if (labelP.contains("Inf") && labelP.contains("Mech")) {
                        message = message + mechOrInfCheck(planetName, activeMap,player);
                        failed = message.contains("Please try again.");
                    }
                    if (!failed) {
                        message = message + "Gained 1 tg ("+player.getTg()+"->"+(player.getTg()+1)+").";
                        player.setTg(player.getTg()+1);
                    }
                    addReaction(event, false, false, message, "");
                }
                case "mallice_2_tg" -> {
                    
                    String playerRep = Helper.getFactionIconFromDiscord(player.getFaction());
                    
                    String message = playerRep + " exhausted Mallice ability and gained 2 tg ("+player.getTg()+"->"+(player.getTg()+2)+").";
                    player.setTg(player.getTg()+2);
                    if(activeMap.isFoWMode() && event.getMessageChannel() != activeMap.getMainGameChannel())
                    {

                        MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message);
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    event.getMessage().delete().queue();
                    //player.exhaustPlanetAbility("mallice");
                }
                case "mallice_convert_comm" -> {
                    
                    String playerRep = Helper.getFactionIconFromDiscord(player.getFaction());
                    
                    String message = playerRep + " exhausted Mallice ability and converted comms to tg (TGs: "+player.getTg()+"->"+(player.getTg()+player.getCommodities())+").";
                    player.setTg(player.getTg()+player.getCommodities());
                    player.setCommodities(0);
                    if(activeMap.isFoWMode() && event.getMessageChannel() != activeMap.getMainGameChannel())
                    {
                        MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message);
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    event.getMessage().delete().queue();
                    //player.exhaustPlanetAbility("mallice");
                }
                case "mallice_use_ability" -> {
                    
                    String playerRep = Helper.getEmojiFromDiscord(StringUtils.capitalize(player.getFaction()));
                    
                    String message = playerRep + " Use buttons to decide how to resolve Mallice ability.";
                    List<Button> buttons = new ArrayList<Button>();
                    Button twoTG = Button.success(finsFactionCheckerPrefix+"mallice_2_tg", "Use Ability to get 2tg");
                    buttons.add(twoTG);
                    Button convertC = Button.success(finsFactionCheckerPrefix+"mallice_convert_comm", "Use Ability to Convert Your Commodities to TG");
                    buttons.add(convertC);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons); 
                    event.getMessage().delete().queue();
                    player.exhaustPlanetAbility("mallice");

                }
                case "decline_explore" -> {
                    addReaction(event, false, false, "Declined Explore", "");
                    event.getMessage().delete().queue();
                }
                case "confirm_cc" -> {
                    if (player.getMahactCC().size() > 0) {
                        addReaction(event, true, false, "Confirmed CCs: "+player.getTacticalCC()+"/"+player.getFleetCC()+"(+"+player.getMahactCC().size()+")/"+player.getStrategicCC(), "");
                    } else {
                        addReaction(event, true, false, "Confirmed CCs: "+player.getTacticalCC()+"/"+player.getFleetCC()+"/"+player.getStrategicCC(), "");

                    }
                }
                case "draw_1_AC" -> {
                    activeMap.drawActionCard(player.getUserID());
                    ACInfo.sendActionCardInfo(activeMap, player, event);
                    addReaction(event, true, false, "Drew 1 AC", "");
                }
                case "draw_2_AC" -> {
                    activeMap.drawActionCard(player.getUserID());
                    activeMap.drawActionCard(player.getUserID());
                    ACInfo.sendActionCardInfo(activeMap, player, event);
                    addReaction(event, true, false, "Drew 2 AC", "");
                }
                case "pass_on_abilities" -> {
                    addReaction(event, false, false," Is "+event.getButton().getLabel(), "");
                }
                case "vote" -> {
                    String pfaction2 = null;
                    if (player != null) {
                        pfaction2 = player.getFaction();
                    }
                    if ( pfaction2 != null && buttonLabel.toLowerCase().contains(pfaction2)) {
                        String voteMessage= "Chose to Vote. Click buttons for which outcome to vote for.";
                        String agendaDetails = activeMap.getCurrentAgendaInfo();
                        agendaDetails = agendaDetails.substring(agendaDetails.indexOf("_")+1, agendaDetails.lastIndexOf("_"));
                        List<Button> outcomeActionRow = null;
                        if (agendaDetails.contains("For") || agendaDetails.contains("for")) {
                            outcomeActionRow = AgendaHelper.getForAgainstOutcomeButtons(null, "outcome");
                        }
                        else if (agendaDetails.contains("Player") || agendaDetails.contains("player")) {
                            outcomeActionRow = AgendaHelper.getPlayerOutcomeButtons(activeMap, null, "outcome");
                        }
                        else if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
                            voteMessage= "Chose to Vote. Too many planets in the game to represent all as buttons. Click buttons for which player owns the planet you wish to elect.";
                            outcomeActionRow = AgendaHelper.getPlayerOutcomeButtons(activeMap, null, "planetOutcomes");
                        }
                        else if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
                            outcomeActionRow = AgendaHelper.getSecretOutcomeButtons(activeMap, null, "outcome");
                        }
                        else if (agendaDetails.contains("Strategy") || agendaDetails.contains("strategy")) {
                            outcomeActionRow = AgendaHelper.getStrategyOutcomeButtons(null, "outcome");
                        } else {
                            outcomeActionRow = AgendaHelper.getLawOutcomeButtons(activeMap, null, "outcome");
                        }
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,outcomeActionRow);
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(), "You are not the faction who is supposed to press this button.");
                    }


                }
                case "planet_ready" -> {
                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ")+1, labelP.length());
                    boolean failed = false;
                    if (labelP.contains("Inf") && labelP.contains("Mech")) {
                        message = message + mechOrInfCheck(planetName, activeMap, player);
                        failed = message.contains("Please try again.");
                    }

                    if (!failed) {
                        new PlanetRefresh().doAction(player, planetName, activeMap);
                        message = message + "Readied "+ planetName;
                        addReaction(event, false, false, message, "");
                        event.getMessage().delete().queue();
                    } else {
                        addReaction(event, false, false, message, "");
                    }

                }
                case "sc_no_follow" -> {
                    int scnum2 = 1;
                    boolean setstatus = true;
                    try {
                        scnum2 = Integer.parseInt(lastchar);
                    } catch (NumberFormatException e) {
                        setstatus = false;
                    }
                    if (setstatus) {
                        player.addFollowedSC(scnum2);
                    }
                    addReaction(event, false, false, "Not Following", "");
                    Set<Player> players = playerUsedSC.get(messageID);
                    if (players == null) {
                        players = new HashSet<>();
                    }
                    players.remove(player);
                    playerUsedSC.put(messageID, players);
                }
                case "turnEnd" -> {
                    new Turn().execute(event, player, activeMap);
                    event.getMessage().delete().queue();
                }
                case "eraseMyVote" -> {
                    String pfaction = player.getFaction();
                    AgendaHelper.eraseVotesOfFaction(activeMap, pfaction);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Erased previous votes made by "+Helper.getFactionIconFromDiscord(player.getFaction()) + "\n \n"+ AgendaHelper.getSummaryOfVotes(activeMap, true));

                }
                case "gain_CC"-> {
                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ")+1, labelP.length());
                    boolean failed = false;
                    if (labelP.contains("Inf") && labelP.contains("Mech")) {
                        message = message + mechOrInfCheck(planetName, activeMap, player);
                        failed = message.contains("Please try again.");
                    }

                    if (!failed) {
                        String trueIdentity = Helper.getPlayerRepresentation(player, activeMap, event.getGuild(), true);
                        String message2 = trueIdentity + "! Your current CCs are "+Helper.getPlayerCCs(player)+". Use buttons to gain CCs";
                        Button getTactic= Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
                        Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
                        Button getStrat= Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
                        Button DoneGainingCC = Button.danger("deleteButtons", "Done Gaining CCs");
                        List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);

                    }
                    addReaction(event, false, false, message, "");
                }
                case "run_status_cleanup" -> {
                    new Cleanup().runStatusCleanup(activeMap);
                    List<ActionRow> actionRow2 = new ArrayList<>();
                    for (ActionRow row : event.getMessage().getActionRows()) {
                        List<ItemComponent> buttonRow = row.getComponents();
                        int buttonIndex = buttonRow.indexOf(event.getButton());
                        if (buttonIndex>-1) {
                            buttonRow.remove(buttonIndex);
                        }
                        if (buttonRow.size() > 0) {
                            actionRow2.add(ActionRow.of(buttonRow));
                        }
                    }
                    String message3 = event.getMessage().getContentRaw();
                    event.getMessage().editMessage(message3).setComponents(actionRow2).queue();
                    
                    addReaction(event, false, true, "Running Status Cleanup. ", "Status Cleanup Run!");

                }
                default -> event.getHook().sendMessage("Button " + buttonID + " pressed.").queue();
            }
        }
        MapSaveLoadManager.saveMap(activeMap, event);
    }

    private String mechOrInfCheck(String planetName, Map activeMap, Player player) {
        String message = "";
        Tile tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;
        int numInf = 0;
        String colorID = Mapper.getColorID(player.getColor());
        String mechKey = colorID + "_mf.png";
        String infKey = colorID + "_gf.png";
        if (unitHolder.getUnits() != null) {

            if (unitHolder.getUnits().get(mechKey) != null) {
                numMechs = unitHolder.getUnits().get(mechKey);
            }
            if (unitHolder.getUnits().get(infKey)!=null) {
                numInf = unitHolder.getUnits().get(infKey);
            }
        }
        if (numMechs > 0 || numInf > 0) {
            if (numMechs > 0) {
                message = "Planet had a mech. ";
            } else {
                message = "Planet did not have a mech. Removed 1 infantry ("+numInf+"->"+(numInf-1)+"). ";
                tile.removeUnit(planetName, infKey, 1);
            }
        } else {
            message = "Planet did not have a mech or infantry. Please try again.";
        }
        return message;
    }


    private boolean addUsedSCPlayer(String messageID, Map activeMap, Player player, @NotNull ButtonInteractionEvent event, String defaultText) {
        Set<Player> players = playerUsedSC.get(messageID);
        if (players == null) {
            players = new HashSet<>();
        }
        boolean contains = players.contains(player);
        players.add(player);
        playerUsedSC.put(messageID, players);
        if (contains) {
            String alreadyUsedMessage = defaultText.isEmpty() ? "used Secondary of Strategy Card" : defaultText;
            String message = "Player: " + Helper.getPlayerPing(player) + " already " + alreadyUsedMessage;
            if (activeMap.isFoWMode()) {
                MessageHelper.sendPrivateMessageToPlayer(player, activeMap, message);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), message);
            }
        }
        return contains;
    }

    @NotNull
    private String deductCC(Player player, @NotNull ButtonInteractionEvent event) {
        int strategicCC = player.getStrategicCC();
        String message;
        if (strategicCC == 0) {
            message = "Have 0 CC in Strategy, can't follow";
        } else {
            strategicCC--;
            player.setStrategicCC(strategicCC);
            message = " following SC, deducted 1 CC from Strategy Tokens";
        }
        return message;
    }


    private void clearAllReactions(@NotNull ButtonInteractionEvent event) {
        Message mainMessage = event.getInteraction().getMessage();
        mainMessage.clearReactions().queue();
        String messageId = mainMessage.getId();
        RestAction<Message> messageRestAction = event.getChannel().retrieveMessageById(messageId);
        //messageRestAction.queue(m -> {
         //   RestAction<Void> voidRestAction = m.clearReactions();
         //   voidRestAction.queue();
        //});
    }


    private void addPersistentReactions(ButtonInteractionEvent event, Map activeMap, String afterorWhen) {
        Guild guild = activeMap.getGuild();
        HashMap<String, Emoji> emojiMap = emoteMap.get(guild);
        List<RichCustomEmoji> emojis = guild.getEmojis();
        if (emojiMap != null && emojiMap.size() != emojis.size()) {
            emojiMap.clear();
        }
        if (emojiMap == null || emojiMap.isEmpty()) {
            emojiMap = new HashMap<>();
            for (Emoji emoji : emojis) {
                emojiMap.put(emoji.getName().toLowerCase(), emoji);
            }
        }
        Message mainMessage = event.getInteraction().getMessage();
        String messageId = mainMessage.getId();

        StringTokenizer players = null;
        if (afterorWhen != null && afterorWhen.equalsIgnoreCase("when")) {
             players = new StringTokenizer(activeMap.getPlayersWhoHitPersistentNoAfter(), "_");
        } else {
           players = new StringTokenizer(activeMap.getPlayersWhoHitPersistentNoWhen(), "_");
        }

        while (players.hasMoreTokens()) {
            String player = players.nextToken();
            Player player_ = Helper.getPlayerFromColorOrFaction(activeMap, player);
            if (player_ != null) {
                Emoji emojiToUse = Helper.getPlayerEmoji(activeMap, player_, mainMessage);
                event.getChannel().addReactionById(messageId, emojiToUse).queue();
            }
        }

    }

    private void addReaction(@NotNull ButtonInteractionEvent event, boolean skipReaction, boolean sendPublic, String message, String additionalMessage) {
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        Player player = Helper.getGamePlayer(activeMap, null, event.getMember(), userID);
        if (player == null || !player.isRealPlayer()) {
            event.getChannel().sendMessage("You're not an active player of the game").queue();
            return;
        }
        String playerFaction = player.getFaction();
        Guild guild = event.getGuild();
        if (guild == null) {
            event.getChannel().sendMessage("Could not find server Emojis").queue();
            return;
        }
        HashMap<String, Emoji> emojiMap = emoteMap.get(guild);
        List<RichCustomEmoji> emojis = guild.getEmojis();
        if (emojiMap != null && emojiMap.size() != emojis.size()) {
            emojiMap.clear();
        }
        if (emojiMap == null || emojiMap.isEmpty()) {
            emojiMap = new HashMap<>();
            for (Emoji emoji : emojis) {
                emojiMap.put(emoji.getName().toLowerCase(), emoji);
            }
        }

        Message mainMessage = event.getInteraction().getMessage();
        Emoji emojiToUse = Helper.getPlayerEmoji(activeMap, player, mainMessage);
        String messageId = mainMessage.getId();

        if (!skipReaction) {
            event.getChannel().addReactionById(messageId, emojiToUse).queue();
            checkForAllReactions(event, activeMap);
            if (message == null || message.isEmpty()) return;
        }


        String text = Helper.getPlayerRepresentation(player, activeMap) + " " + message;
        if (activeMap.isFoWMode() && sendPublic) {
            text = message;
        } else if (activeMap.isFoWMode() && !sendPublic) {
            text = "(You) " + emojiToUse.getFormatted() + " " + message;
        }

        if (!additionalMessage.isEmpty()) {
            text += Helper.getGamePing(event.getGuild(), activeMap) + " " + additionalMessage;
        }

        if (activeMap.isFoWMode() && !sendPublic) {
            MessageHelper.sendPrivateMessageToPlayer(player, activeMap, text);
            return;
        }

        MessageHelper.sendMessageToChannel(Helper.getThreadChannelIfExists(event), text);
    }

    private void checkForAllReactions(@NotNull ButtonInteractionEvent event, Map activeMap) {
        String messageId = event.getInteraction().getMessage().getId();

        Message mainMessage = event.getMessageChannel().retrieveMessageById(messageId).completeAfter(500, TimeUnit.MILLISECONDS);

        int matchingFactionReactions = 0;
        for (Player player : activeMap.getPlayers().values()) {
            if (!player.isRealPlayer()) {
                matchingFactionReactions++;
                continue;
            }

            String faction = player.getFaction();
            if (faction == null || faction.isEmpty() || faction.equals("null")) continue;

            Emoji reactionEmoji = Emoji.fromFormatted(Helper.getFactionIconFromDiscord(faction));
            if (activeMap.isFoWMode()) {
                int index = 0;
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_ == player) break;
                    index++;
                }
                reactionEmoji = Emoji.fromFormatted(Helper.getRandomizedEmoji(index, event.getMessageId()));
            }
            MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
            if (reaction != null) matchingFactionReactions++;
        }
        int numberOfPlayers = activeMap.getPlayers().size();
        if (matchingFactionReactions >= numberOfPlayers) { //TODO: @Jazzxhands to verify this will work for FoW
            respondAllPlayersReacted(event, activeMap);
        }
    }

    private static void respondAllPlayersReacted(ButtonInteractionEvent event, Map activeMap) {
        String buttonID = event.getButton().getId();
        if (event == null || activeMap == null || buttonID == null) {
            return;
        }
        if (buttonID.startsWith(Constants.PO_SCORING)) {
            buttonID = Constants.PO_SCORING;
        } else if ((buttonID.startsWith(Constants.SC_FOLLOW) || buttonID.startsWith("sc_no_follow"))) {
            buttonID = Constants.SC_FOLLOW;
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            String buttonText = event.getButton().getLabel();
            event.getInteraction().getMessage().reply("All players have reacted to '" + buttonText + "'").queue();
        }
        switch (buttonID) {
            case Constants.SC_FOLLOW, "sc_no_follow", "sc_refresh", "sc_refresh_and_wash", "trade_primary", "sc_ac_draw", "sc_draw_so","sc_trade_follow", "sc_leadership_follow" -> {
                if (activeMap.isFoWMode()) {
                    event.getInteraction().getMessage().reply("All players have reacted to this Strategy Card").queueAfter(1, TimeUnit.SECONDS);
                } else {
                    GuildMessageChannel guildMessageChannel = Helper.getThreadChannelIfExists(event);
                    guildMessageChannel.sendMessage("All players have reacted to this Strategy Card").queueAfter(10, TimeUnit.SECONDS);
                    if (guildMessageChannel instanceof ThreadChannel) ((ThreadChannel) guildMessageChannel).getManager().setArchived(true).queueAfter(5, TimeUnit.MINUTES);
                }
            }
            case "no_when","no_when_persistent" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Whens'").queueAfter(1, TimeUnit.SECONDS);
            }
            case "no_after","no_after_persistent" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Afters'").queue();
                AgendaHelper.startTheVoting(activeMap, event);
                event.getMessage().delete().queue();

            }
            case "no_sabotage" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Sabotage'").queueAfter(1, TimeUnit.SECONDS);
            }
            case Constants.PO_SCORING, Constants.PO_NO_SCORING -> {
                String message2 = "All players have indicated scoring. Flip the relevant PO using the buttons. This will automatically run status clean-up if it has not been run already.";
                Button drawStage2= Button.success("reveal_stage_2", "Reveal Stage 2");
                Button drawStage1 = Button.success("reveal_stage_1", "Reveal Stage 1");
               // Button runStatusCleanup = Button.primary("run_status_cleanup", "Run Status Cleanup");
                List<Button> buttons = List.of(drawStage1, drawStage2);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            }
            case "pass_on_abilities"-> {
                if (activeMap.isCustodiansScored()) {
                    //new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    Button flipAgenda = Button.primary("flip_agenda", "Press this to flip agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please flip agenda now", buttons);
                } else {
                    event.getInteraction().getMessage().reply(Helper.getGamePing(event.getGuild(), activeMap) + " All players have indicated completion of status phase. Proceed to Strategy Phase.").queueAfter(1, TimeUnit.SECONDS);
                    Player speaker = null;
                    if (activeMap.getPlayer(activeMap.getSpeaker()) != null) {
                        speaker = activeMap.getPlayers().get(activeMap.getSpeaker());
                    } else {
                        speaker = null;
                    }
                    String message =  Helper.getPlayerRepresentation(speaker, activeMap, event.getGuild(), true) + " UP TO PICK SC";
                    if (activeMap.isFoWMode()) {
                        MessageHelper.sendPrivateMessageToPlayer(speaker, activeMap, message);
                        if(!activeMap.isHomeBrewSCMode())
                        {
                            MessageHelper.sendMessageToChannelWithButtons(speaker.getPrivateChannel(), "Use Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeMap));
                        }
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                        if(!activeMap.isHomeBrewSCMode())
                        {
                            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Use Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeMap));
                        }

                    }
                }

            }
        }
    }
}