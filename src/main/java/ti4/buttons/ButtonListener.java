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
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
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
import ti4.commands.cardsac.ShowAllAC;
import ti4.commands.cardspn.ShowAllPN;
import ti4.commands.cardsso.DealSOToAll;
import ti4.commands.cardsso.DiscardSO;
import ti4.commands.cardsso.SOInfo;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.cardsso.ShowAllSO;
import ti4.commands.explore.DrawRelic;
import ti4.commands.explore.ExpPlanet;
import ti4.commands.fow.PingSystem;
import ti4.commands.planet.PlanetExhaust;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.commands.planet.PlanetRefresh;
import ti4.commands.status.Cleanup;
import ti4.commands.status.RevealStage1;
import ti4.commands.status.RevealStage2;
import ti4.commands.status.ScorePublic;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.RemoveUnits;
import ti4.commands.player.Stats;
import ti4.commands.player.SCPick;
import ti4.commands.player.SCPlay;
import ti4.commands.player.Turn;
import ti4.commands.special.SleeperToken;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.TechnologyModel;
import ti4.map.Tile;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

import ti4.generator.GenerateMap;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;

public class ButtonListener extends ListenerAdapter {
    public static HashMap<Guild, HashMap<String, Emoji>> emoteMap = new HashMap<>();
    private static HashMap<String, Set<Player>> playerUsedSC = new HashMap<>();

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        event.deferEdit().queue();
        String id = event.getUser().getId();
        MessageListener.setActiveGame(event.getMessageChannel(), id, "button", "no sub command");
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
        if (player == null) {
            event.getChannel().sendMessage("You're not a player of the game").queue();
            return;
        }

        MessageChannel privateChannel = event.getChannel();
        if (activeMap.isFoWMode()) {
            if (player.getPrivateChannel() == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Private channels are not set up for this game. Messages will be suppressed.");
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
            buttonID = buttonID.replaceFirst(factionWhoGeneratedButton + "_", "");
            String factionWhoIsUp = player.getFaction();
            if (!player.getFaction().equalsIgnoreCase(factionWhoGeneratedButton)
                    && !buttonLabel.toLowerCase().contains(factionWhoIsUp)) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "To " + Helper.getFactionIconFromDiscord(player.getFaction())
                                + ": you are not the faction who these buttons are meant for.");
                return;
            }
        }

        String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";
        String trueIdentity = Helper.getPlayerRepresentation(player, activeMap, event.getGuild(), true);
        String ident = Helper.getFactionIconFromDiscord(player.getFaction());
  try {
                    
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
                    event.getChannel().asThreadChannel()
                            .sendMessage("Could not parse AC ID: " + acID + " Please play manually.").queue();
                    return;
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
        } else if (buttonID.startsWith("ac_discard_from_hand_")) {
            String acIndex = buttonID.replace("ac_discard_from_hand_", "");
            MessageChannel channel = null;
            if (activeMap.getMainGameChannel() != null) {
                channel = activeMap.getMainGameChannel();
            } else {
                channel = actionsChannel;
            }

            if (channel != null) {
                try {
                    String acID = null;
                    for (java.util.Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
                        if (so.getValue().equals(Integer.parseInt(acIndex))) {
                            acID = so.getKey();
                        }
                    }

                    boolean removed = activeMap.discardActionCard(player.getUserID(), Integer.parseInt(acIndex));
                    if (!removed) {
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                                "No such Action Card ID found, please retry");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("Player: ").append(player.getUserName()).append(" - ");
                    sb.append("Discarded Action Card:").append("\n");
                    sb.append(Mapper.getActionCard(acID).getRepresentation()).append("\n");
                    MessageChannel channel2 = activeMap.getMainGameChannel();
                    if (activeMap.isFoWMode()) {
                        channel2 = player.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannel(channel2, sb.toString());
                    ACInfo.sendActionCardInfo(activeMap, player);
                    String message = "Use buttons to end turn or do another action.";
                    if(player.hasAbility("stall_tactics")){
                        List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeMap, true, event);
                        MessageHelper.sendMessageToChannelWithButtons(channel2, message, systemButtons);
                       
                    }

                    ButtonHelper.checkACLimit(activeMap, event, player);
                    event.getMessage().delete().queue();
                    
                    
                   
                } catch (Exception e) {
                    BotLogger.log(event, "Something went wrong discarding", e);
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
                    event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please Score manually.")
                            .queue();
                    return;
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("SODISCARD_")) {
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
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), ident + " discarded an SO");
                    }
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse SO ID: " + soID, e);
                    event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please discard manually.")
                            .queue();
                    return;
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("get_so_score_buttons")) {
            String secretScoreMsg = "_ _\nClick a button below to score your Secret Objective";
            List<Button> soButtons = SOInfo.getUnscoredSecretObjectiveButtons(activeMap, player);
            if (soButtons != null && !soButtons.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), secretScoreMsg, soButtons);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong. Please report to Fin");
            }
        } else if (buttonID.startsWith("get_so_discard_buttons")) {
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
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Only the player who played Politics can assign Speaker");
                return;
            }
            if (activeMap != null && !activeMap.isFoWMode()) {
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_.getFaction().equals(faction)) {
                        activeMap.setSpeaker(player_.getUserID());
                        String message = Emojis.SpeakerToken + " Speaker assigned to: "
                                + Helper.getPlayerRepresentation(player_, activeMap);
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

            for (Player player2 : activeMap.getPlayers().values()) {

                if (playersWithSCs > 1) {
                    new Cleanup().runStatusCleanup(activeMap);
                    addReaction(event, false, true, "Running Status Cleanup. ", "Status Cleanup Run!");
                    playersWithSCs = -30;
                }
                if (player2.isRealPlayer()) {
                    if (player2.getSCs() != null && player2.getSCs().size() > 0
                            && !player2.getSCs().contains(Integer.valueOf(0))) {
                        playersWithSCs = playersWithSCs + 1;
                    }
                } else {
                    continue;
                }
                if (player2.hasLeader("naaluhero") && player2.getLeaderByID("naaluhero") != null
                        && !player2.getLeaderByID("naaluhero").isLocked()) {
                    MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                            Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                    + "Reminder this is the window to do Naalu Hero");
                }
                if (player2.getRelics() != null && player2.hasRelic("mawofworlds") && activeMap.isCustodiansScored()) {
                    MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                            Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                    + "Reminder this is the window to do Maw of Worlds");
                }
                if (player2.getRelics() != null && player2.hasRelic("emphidia")) {
                    for (String pl : player2.getPlanets()) {
                        Tile tile = activeMap.getTile(AliasHandler.resolveTile(pl));
                        if(tile == null){
                            continue;
                        }
                        UnitHolder unitHolder = tile.getUnitHolders().get(pl);
                        if (unitHolder.getTokenList() != null
                                && unitHolder.getTokenList().contains("attachment_tombofemphidia.png")) {
                            MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                                    Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                            + "Reminder this is the window to purge Crown of Emphidia if you want to. Command to make a new custom public for crown is /status po_add_custom public_name:");
                        }
                    }
                }
                if (player2.getActionCards() != null && player2.getActionCards().keySet().contains("summit")
                        && !activeMap.isCustodiansScored()) {
                    MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                            Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                    + "Reminder this is the window to do summit");
                }
                if (player2.getActionCards() != null && (player2.getActionCards().keySet().contains("investments")
                        && !activeMap.isCustodiansScored())) {
                    MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                            Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                    + "Reminder this is the window to do manipulate investments.");
                }
                if (player2.getActionCards() != null && player2.getActionCards().keySet().contains("stability")) {
                    MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                            Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true)
                                    + "Reminder this is the window to play political stability.");
                }
                for (String pn : player2.getPromissoryNotes().keySet()) {

                    if (!player2.ownsPromissoryNote("ce") && pn.equalsIgnoreCase("ce")) {
                        String cyberMessage = Helper.getPlayerRepresentation(player2, activeMap, event.getGuild(), true)
                                + " reminder to use cybernetic enhancements!";
                        MessageHelper.sendMessageToChannel((MessageChannel) player2.getCardsInfoThread(activeMap),
                                cyberMessage);
                    }
                }
            }

            String message2 = null;
            message2 = "Resolve status homework using the buttons. Only the Ready for [X] button is essential to hit, all others are optional. ";
            Button draw1AC = Button.success("draw_1_AC", "Draw 1 AC").withEmoji(Emoji.fromFormatted(Emojis.ActionCard));
            Button getCCs = Button.success("redistributeCCButtons", "Redistribute, Gain, & Confirm CCs").withEmoji(Emoji.fromFormatted("🔺"));
            boolean custodiansTaken = activeMap.isCustodiansScored();
            Button passOnAbilities;

            if (custodiansTaken) {
                passOnAbilities = Button.danger("pass_on_abilities", "Ready For Agenda");
                message2 = message2
                        + " Ready for Agenda means you are done playing/passing on playing political stability, ancient burial sites, maw of worlds, Naalu hero, and crown of emphidia.";
            } else {
                passOnAbilities = Button.danger("pass_on_abilities", "Ready For Strategy Phase");
                message2 = message2
                        + " Ready for Strategy Phase means you are done playing/passing on playing political stability, summit, and manipulate investments. ";
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
                buttons = List.of(passOnAbilities);
            } else {

                buttons = List.of(draw1AC, getCCs, passOnAbilities);
            }
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("sc_follow_") && (!buttonID.contains("leadership"))
                && (!buttonID.contains("trade"))) {
            boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "");

            if (!used) {
                ButtonHelper.resolveMuaatCommanderCheck(player, activeMap, event);
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
        } else if (buttonID.startsWith("sc_no_follow_")) {
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
        } else if (buttonID.startsWith(Constants.GENERIC_BUTTON_ID_PREFIX)) {
            addReaction(event, false, false, null, "");
        } else if (buttonID.startsWith("movedNExplored_")) {
            String bID = buttonID.replace("movedNExplored_", "");
            String[] info = bID.split("_");

            String message = "";

            new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeMap), info[1], info[2], player,
                    false, activeMap, 1, false);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("distant_suns_")) {
            String bID = buttonID.replace("distant_suns_", "");
            String[] info = bID.split("_");
            String message = "";
            if (info[0].equalsIgnoreCase("decline")) {
                message = "Rejected Distant Suns Ability";
                new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeMap), info[1], info[2],
                        player, true, activeMap, 1, false);
            } else {
                message = "Exploring twice";
                new ExpPlanet().explorePlanet(event, Helper.getTileFromPlanet(info[1], activeMap), info[1], info[2],
                        player, true, activeMap, 2, false);
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("strategicAction_")) {
            int scNum = Integer.parseInt(buttonID.replace("strategicAction_", ""));
            new SCPlay().playSC(event, scNum, activeMap, mainGameChannel, player);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("resolve_explore_")) {
            String bID = buttonID.replace("resolve_explore_", "");
            String[] info = bID.split("_");
            String cardID = info[0];
            String planetName = info[1];
            Tile tile = Helper.getTileFromPlanet(planetName, activeMap);
            StringBuilder messageText = new StringBuilder();
            messageText.append(Helper.getPlayerRepresentation(player, activeMap)).append(" explored ");

            messageText.append("Planet " + Helper.getPlanetRepresentationPlusEmoji(planetName) + " *(tile "
                    + tile.getPosition() + ")*:\n");
            messageText.append("> ").append(new ExpPlanet().displayExplore(cardID));
            new ExpPlanet().resolveExplore(event, cardID, tile, planetName, messageText.toString(), false, player,
                    activeMap);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("refresh_")) {
            String planetName = buttonID.replace("refresh_", "");
            new PlanetRefresh().doAction(player, planetName, activeMap);
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            String totalVotesSoFar = event.getMessage().getContentRaw();
            if (totalVotesSoFar.contains("Readied")) {
                totalVotesSoFar += ", "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
            } else {
                totalVotesSoFar = ident + " Readied "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
            }

            if (actionRow2.size() > 0) {
                event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
            }
        } else if (buttonID.startsWith("biostimsReady_")) {
            ButtonHelper.bioStimsReady(activeMap, event, player, buttonID);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("refreshVotes_")) {
            String votes = buttonID.replace("refreshVotes_", "");
            List<Button> voteActionRow = Helper.getPlanetRefreshButtons(event, player, activeMap);
            Button concludeRefreshing = Button.danger(finsFactionCheckerPrefix + "votes_" + votes, "Done readying planets.");
            voteActionRow.add(concludeRefreshing);
            String voteMessage2 = "Use the buttons to ready planets. When you're done it will prompt the next person to vote.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage2, voteActionRow);
            event.getMessage().delete().queue();
        }

        else if (buttonID.startsWith("getAllTechOfType_")) {
            String techType = buttonID.replace("getAllTechOfType_", "");
            List<TechnologyModel> techs = Helper.getAllTechOfAType(techType, player.getFaction(), player);
            List<Button> buttons = Helper.getTechButtons(techs, techType, player);

            String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to get the tech you want";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
            
        } else if (buttonID.startsWith("getTech_")) {

            String tech = buttonID.replace("getTech_", "");
            String techFancy = buttonLabel;

            String message = ident + " Acquired The Tech " + Helper.getTechRepresentation(AliasHandler.resolveTech(tech));

            String message2 = trueIdentity + " Click the names of the planets you wish to exhaust. ";
            player.addTech(AliasHandler.resolveTech(tech));
            if(AliasHandler.resolveTech(tech).equalsIgnoreCase("iihq")){
                message = message + "\n Automatically added the Custodia Vigilia planet";
            }
            if(player.getLeaderIDs().contains("jolnarcommander") && !player.hasLeaderUnlocked("jolnarcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeMap, "jolnar", event);
            }
            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeMap, player, event);
            if (player.hasTechReady("aida")) {
                        Button aiDEVButton = Button.danger("exhaustTech_aida", "Exhaust AIDEV");
                        buttons.add(aiDEVButton);
                    }
            Button DoneExhausting = Button.danger("deleteButtons_technology", "Done Exhausting Planets");
            buttons.add(DoneExhausting);
            


            if (activeMap.isFoWMode()) {
                MessageHelper.sendMessageToChannel(event.getChannel(), message);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            } else {

                String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-technology";
                
                List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                if (!activeMap.getComponentAction()) {
                    for (ThreadChannel threadChannel_ : threadChannels) {
                        if (threadChannel_.getName().equals(threadName)) {
                            MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, message);
                        }
                    }
                } else {
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message);
                }
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) player.getCardsInfoThread(activeMap),
                        message2, buttons);
            }

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("takeAC_")) {
            buttonID = buttonID.replace("takeAC_", "");

            int acNum = Integer.parseInt(buttonID.split("_")[0]);
            String faction2 = buttonID.split("_")[1];
            Player player2 = Helper.getPlayerFromColorOrFaction(activeMap, faction2);
            String ident2 = Helper.getFactionIconFromDiscord(player2.getFaction());
            String message2 = ident + " took AC #" + acNum + " from " + ident2;
            String acID = null;
            for (java.util.Map.Entry<String, Integer> so : player2.getActionCards().entrySet()) {
                if (so.getValue().equals(acNum)) {
                    acID = so.getKey();
                }
            }
            if (activeMap.isFoWMode()) {
                message2 = "Someone took AC #" + acNum + " from " + player2.getColor();
                MessageHelper.sendMessageToChannel(player.getPrivateChannel(), message2);
                MessageHelper.sendMessageToChannel(player2.getPrivateChannel(), message2);
            } else {
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message2);
            }
            player2.removeActionCard(acNum);
            player.setActionCard(acID);
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(activeMap), "Acquired " + acID);
            MessageHelper.sendMessageToChannel(player2.getCardsInfoThread(activeMap), "Lost " + acID);
            ACInfo.sendActionCardInfo(activeMap, player2);
            ACInfo.sendActionCardInfo(activeMap, player);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("spend_")) {
            String planetName = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());

            new PlanetExhaust().doAction(player, planetName, activeMap);
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            String exhaustedMessage = event.getMessage().getContentRaw();
            if (!exhaustedMessage.contains("Click the names")) {
                exhaustedMessage = exhaustedMessage + ", exhausted "
                        + Helper.getPlanetRepresentation(planetName, activeMap);
            } else {
                exhaustedMessage = ident + " exhausted "
                        + Helper.getPlanetRepresentation(planetName, activeMap);
            }
            event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
           

        } else if (buttonID.startsWith("finishTransaction_")) {
            String player2Color = buttonID.split("_")[1];
            Player player2 = Helper.getPlayerFromColorOrFaction(activeMap, player2Color);
            ButtonHelper.pillageCheck(player, activeMap);
            ButtonHelper.pillageCheck(player2, activeMap);
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("sabotage_")) {
            String typeNName = buttonID.replace("sabotage_", "");
            String type = typeNName.substring(0, typeNName.indexOf("_"));
            String acName = typeNName.replace(type + "_", "");
            String message = "Cancelling the AC \"" + acName + "\" using ";
            String addMessage = "An AC has been cancelled!";
            boolean sendReact = true;
            if (type.equalsIgnoreCase("empy")) {
                message = message + "a Watcher mech! The Watcher should be removed now by the owner.";
                event.getMessage().delete().queue();
            } else if (type.equalsIgnoreCase("xxcha")) {
                message = message
                        + "the \"Instinct Training\" tech! The tech has been exhausted and a strategy CC removed.";
                if (player.hasTech(AliasHandler.resolveTech("Instinct Training"))) {
                    player.exhaustTech(AliasHandler.resolveTech("Instinct Training"));
                    if (player.getStrategicCC() > 0) {
                        player.setStrategicCC(player.getStrategicCC() - 1);
                    }
                    event.getMessage().delete().queue();
                } else {
                    sendReact = false;
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Someone clicked the Instinct Training button but did not have the tech.");
                }
            } else if (type.equalsIgnoreCase("ac")) {
                message = message + "A Sabotage!";
                boolean hasSabo = false;
                String saboID = "3";
                for (String AC : player.getActionCards().keySet()) {
                    if (AC.contains("sabo")) {
                        hasSabo = true;
                        saboID = "" + player.getActionCards().get(AC);
                        break;
                    }
                }
                if (hasSabo) {
                    PlayAC.playAC(event, activeMap, player, saboID, activeMap.getActionsChannel(),
                            activeMap.getGuild());
                } else {
                    addMessage = "";
                    message = "Tried to play a sabo but found none in hand.";
                    sendReact = false;
                    MessageHelper.sendMessageToChannel(activeMap.getActionsChannel(),
                            "Someone clicked the AC sabo button but did not have a sabo in hand.");
                }

            }

            if (sendReact) {
                MessageHelper.sendMessageToChannel(activeMap.getActionsChannel(),
                        message + "\n" + Helper.getGamePing(activeMap.getGuild(), activeMap) + addMessage);

            }

        } else if (buttonID.startsWith("reduceTG_")) {

            int tgLoss = Integer.parseInt(buttonID.replace("reduceTG_", ""));
            String message = ident + " reduced tgs by " + tgLoss + " (" + player.getTg() + "->"
                    + (player.getTg() - tgLoss) + ")";

            if (tgLoss > player.getTg()) {
                message = "You dont have " + tgLoss + " tgs. No change made.";
            } else {
                player.setTg(player.getTg() - tgLoss);
            }
            String editedMessage = event.getMessage().getContentRaw() + " " + message;

            if (editedMessage.contains("Click the names")) {
                editedMessage = message;
            }
            event.getMessage().editMessage(editedMessage).queue();

            // MessageHelper.sendMessageToChannel(event.getChannel(), message);
        } else if (buttonID.startsWith("reduceComm_")) {

            int tgLoss = Integer.parseInt(buttonID.replace("reduceComm_", ""));
            String message = ident + " reduced comms by " + tgLoss + " (" + player.getCommodities() + "->"
                    + (player.getCommodities() - tgLoss) + ")";

            if (tgLoss > player.getCommodities()) {
                message = "You dont have " + tgLoss + " comms. No change made.";
            } else {
                player.setCommodities(player.getCommodities() - tgLoss);
            }
            String editedMessage = event.getMessage().getContentRaw() + " " + message;

            if (editedMessage.contains("Click the names")) {
                editedMessage = message;
            }
            Leader playerLeader = player.getLeader("keleresagent");
		
		
                
            if(!playerLeader.isExhausted() ){
                playerLeader.setExhausted(true);
                StringBuilder messageText = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap))
                        .append(" exhausted ").append(Helper.getLeaderFullRepresentation(playerLeader));
                if(activeMap.isFoWMode()){
                    MessageHelper.sendMessageToChannel(player.getPrivateChannel(), messageText.toString());
                }else{
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), messageText.toString());
                }
            }
                    event.getMessage().editMessage(editedMessage).queue();

            // MessageHelper.sendMessageToChannel(event.getChannel(), message);
         } else if (buttonID.startsWith("pillage_")) {
            buttonID = buttonID.replace("pillage_", "");
            String colorPlayer = buttonID.split("_")[0];
            String checkedStatus = buttonID.split("_")[1];
            Player pillaged = Helper.getPlayerFromColorOrFaction(activeMap, colorPlayer);
            if(checkedStatus.contains("unchecked")){
                 List<Button> buttons = new ArrayList<Button>();
                String message2 =  "Please confirm this is a valid pillage opportunity and that you wish to pillage.";
                buttons.add(Button.danger(finsFactionCheckerPrefix+"pillage_"+pillaged.getColor()+"_checked","Pillage"));
                buttons.add(Button.success(finsFactionCheckerPrefix+"deleteButtons","Delete these buttons"));
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            }else{
                MessageChannel channel1 = activeMap.getMainGameChannel();
                MessageChannel channel2 = activeMap.getMainGameChannel();
                String pillagerMessage = Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true) + " you pillaged, your tgs have gone from "+player.getTg() +" to "+(player.getTg()+1) +".";
                String pillagedMessage = Helper.getPlayerRepresentation(pillaged, activeMap, activeMap.getGuild(), true) + " you have been pillaged";
                if(activeMap.isFoWMode()){
                    channel1 = pillaged.getPrivateChannel();
                    channel2 = player.getPrivateChannel();
                }
                if(pillaged.getCommodities()>0){
                    pillagedMessage = pillagedMessage+ ", your comms have gone from "+pillaged.getCommodities() +" to "+(pillaged.getCommodities()-1) +".";
                    pillaged.setCommodities(pillaged.getCommodities()-1);

                } else {
                    pillagedMessage = pillagedMessage+ ", your tgs have gone from "+pillaged.getTg() +" to "+(pillaged.getTg()-1) +".";
                    pillaged.setTg(pillaged.getTg()-1);
                }
                player.setTg(player.getTg()+1);
                MessageHelper.sendMessageToChannel(channel2, pillagerMessage);
                MessageHelper.sendMessageToChannel(channel1, pillagedMessage);
            }
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("exhaust_")) {
            String planetName = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            String votes = buttonLabel.substring(buttonLabel.indexOf("(") + 1, buttonLabel.indexOf(")"));
            if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive")
                    && !buttonID.contains("everything")) {
                new PlanetExhaust().doAction(player, planetName, activeMap);
            }
            if (buttonID.contains("everything")) {
                for (String planet : player.getPlanets()) {
                    player.exhaustPlanet(planet);
                }
            }

            // List<ItemComponent> actionRow =
            // event.getMessage().getActionRows().get(0).getComponents();
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (buttonRow.size() > 0) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            String totalVotesSoFar = event.getMessage().getContentRaw();
            if (!buttonID.contains("argent") && !buttonID.contains("blood") && !buttonID.contains("predictive")
                    && !buttonID.contains("everything")) {

                if (totalVotesSoFar == null || totalVotesSoFar.equalsIgnoreCase("")) {
                    totalVotesSoFar = "Total votes exhausted so far: " + votes + "\n Planets exhausted so far are: "
                            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
                } else {
                    int totalVotes = Integer.parseInt(
                            totalVotesSoFar.substring(totalVotesSoFar.indexOf(":") + 2, totalVotesSoFar.indexOf("\n")))
                            + Integer.parseInt(votes);
                    totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":") + 2) + totalVotes
                            + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"), totalVotesSoFar.length())
                            + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, activeMap);
                }
                if (actionRow2.size() > 0) {
                    event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
                }
                // addReaction(event, true, false,"Exhausted
                // "+Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName,
                // activeMap) + " as "+ votes + " votes", "");
            } else {
                if (totalVotesSoFar == null || totalVotesSoFar.equalsIgnoreCase("")) {
                    totalVotesSoFar = "Total votes exhausted so far: " + votes
                            + "\n Planets exhausted so far are: all planets";
                } else {
                    int totalVotes = Integer.parseInt(
                            totalVotesSoFar.substring(totalVotesSoFar.indexOf(":") + 2, totalVotesSoFar.indexOf("\n")))
                            + Integer.parseInt(votes);
                    totalVotesSoFar = totalVotesSoFar.substring(0, totalVotesSoFar.indexOf(":") + 2) + totalVotes
                            + totalVotesSoFar.substring(totalVotesSoFar.indexOf("\n"), totalVotesSoFar.length());
                }
                if (actionRow2.size() > 0) {
                    event.getMessage().editMessage(totalVotesSoFar).setComponents(actionRow2).queue();
                }
                if (buttonID.contains("everything")) {
                    // addReaction(event, true, false,"Exhausted
                    // "+Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName,
                    // activeMap) + " as "+ votes + " votes", "");
                    addReaction(event, true, false, "Exhausted all planets for " + votes + " votes", "");
                } else {
                    addReaction(event, true, false, "Used ability for " + votes + " votes", "");
                }
            }

        } else if (buttonID.startsWith("diplo_")) {
            String planet = buttonID.replace("diplo_", "");
            String tileID = AliasHandler.resolveTile(planet.toLowerCase());
            Tile tile = activeMap.getTile(tileID);
            if (tile == null) {
                tile = activeMap.getTileByPosition(tileID);
            }
            if (tile == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Could not resolve tileID:  `" + tileID + "`. Tile not found");
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
            MessageHelper.sendMessageToChannel(event.getChannel(), ident + " chose to diplo the system containing "
                    + Helper.getPlanetRepresentation(planet, activeMap));
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("doneWithOneSystem_")) {
            String pos = buttonID.replace("doneWithOneSystem_", "");
            Tile tile = activeMap.getTileByPosition(pos);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "From system "
                    + tile.getRepresentationForButtons(activeMap, player) + "\n" + event.getMessage().getContentRaw());
            String message = "Choose a different system to move from, or finalize movement.";
            activeMap.resetCurrentMovedUnitsFrom1System();
            List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, activeMap, event);
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("delete_buttons_")) {
            boolean resolveTime = false;
            String winner = "";
            String votes = buttonID.substring(buttonID.lastIndexOf("_") + 1, buttonID.length());
            if (!buttonID.contains("outcomeTie*")) {
                if (votes.equalsIgnoreCase("0")) {

                    String pfaction2 = null;
                    if (player != null) {
                        pfaction2 = player.getFaction();
                    }
                    if (pfaction2 != null) {
                        addReaction(event, true, true, "Abstained.", "");
                        event.getMessage().delete().queue();
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
                    addReaction(event, true, true,
                            "Voted " + votes + " votes for " + StringUtils.capitalize(outcome) + "!", "");
                }

                String pFaction1 = StringUtils.capitalize(player.getFaction());
                Button EraseVote = Button.danger("FFCC_" + pFaction1 + "_eraseMyVote",
                        pFaction1 + " Erase Any Of Your Previous Votes");
                List<Button> EraseButton = List.of(EraseVote);
                String mErase = "Use this button if you want to erase your previous votes and vote again";
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), mErase, EraseButton);
                String message = " up to vote! Resolve using buttons.";

                Player nextInLine = AgendaHelper.getNextInLine(player, AgendaHelper.getVotingOrder(activeMap),
                        activeMap);
                String realIdentity2 = Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);

                int[] voteInfo = AgendaHelper.getVoteTotal(event, nextInLine, activeMap);

                while (voteInfo[0] < 1 && !nextInLine.getColor().equalsIgnoreCase(player.getColor())) {
                    String skippedMessage = realIdentity2
                            + "You are being skipped because you either have 0 votes or have ridered";
                    if (activeMap.isFoWMode()) {
                        MessageHelper.sendPrivateMessageToPlayer(nextInLine, activeMap, skippedMessage);
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(), skippedMessage);
                    }
                    player = nextInLine;
                    nextInLine = AgendaHelper.getNextInLine(nextInLine, AgendaHelper.getVotingOrder(activeMap),
                            activeMap);
                    realIdentity2 = Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);
                    voteInfo = AgendaHelper.getVoteTotal(event, nextInLine, activeMap);
                }

                if (!nextInLine.getColor().equalsIgnoreCase(player.getColor())) {
                    String realIdentity = "";
                    realIdentity = Helper.getPlayerRepresentation(nextInLine, activeMap, event.getGuild(), true);
                    String pFaction = StringUtils.capitalize(nextInLine.getFaction());
                    
                    Button Vote = Button.success("vote", pFaction + " Choose To Vote");
                    Button Abstain = Button.danger("delete_buttons_0", pFaction + " Choose To Abstain");
                    activeMap.updateActivePlayer(nextInLine);
                    List<Button> buttons = List.of(Vote, Abstain);
                    if (activeMap.isFoWMode()) {
                        if (nextInLine.getPrivateChannel() != null) {
                            MessageHelper.sendMessageToChannel(nextInLine.getPrivateChannel(),AgendaHelper.getSummaryOfVotes(activeMap, true) + "\n ");
                            MessageHelper.sendMessageToChannelWithButtons(nextInLine.getPrivateChannel(), "\n " + realIdentity+message,
                                    buttons);
                            event.getChannel().sendMessage("Notified next in line").queue();
                        }
                    } else {
                        message = AgendaHelper.getSummaryOfVotes(activeMap, true) + "\n \n " + realIdentity + message;
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                    }
                } else {
                    String summary = AgendaHelper.getSummaryOfVotes(activeMap, false);
                    winner = AgendaHelper.getWinner(summary);
                    if (winner != null && !winner.contains("*")) {
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
                            StringTokenizer winnerInfo = new StringTokenizer(winner, "*");
                            while (winnerInfo.hasMoreTokens()) {
                                String tiedWinner = winnerInfo.nextToken();
                                Button button = Button.primary("delete_buttons_outcomeTie* " + tiedWinner, tiedWinner);
                                tiedWinners.add(button);
                            }
                        } else {

                            tiedWinners = AgendaHelper.getAgendaButtons(null, activeMap, "delete_buttons_outcomeTie*");
                        }
                        if (!tiedWinners.isEmpty()) {
                            MessageChannel channel = null;
                            if (activeMap.isFoWMode()) {
                                channel = speaker == null ? null : speaker.getPrivateChannel();
                                if (channel == null) {
                                    MessageHelper.sendMessageToChannel(event.getChannel(),
                                            "Speaker is not assigned for some reason. Please decide the winner.");
                                }
                            } else {
                                channel = event.getChannel();
                            }
                            MessageHelper.sendMessageToChannelWithButtons(channel,
                                    Helper.getPlayerRepresentation(speaker, activeMap, event.getGuild(), true)
                                            + " please decide the winner.",
                                    tiedWinners);
                        }
                    }
                }
            } else {
                resolveTime = true;
                winner = buttonID.substring(buttonID.lastIndexOf("*") + 2, buttonID.length());
            }
            if (resolveTime) {
                List<Player> losers = AgendaHelper.getLosers(winner, activeMap);
                String summary2 = AgendaHelper.getSummaryOfVotes(activeMap, true);
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), summary2 + "\n \n");
                activeMap.setCurrentPhase("agendaEnd");
                activeMap.setActivePlayer(null);
                String resMessage = "Please hold while people resolve shenanigans. " + losers.size()
                        + " players have the opportunity to play deadly plot.";
                if ((!activeMap.isACInDiscard("Bribery") || !activeMap.isACInDiscard("Deadly Plot"))
                        && (losers.size() > 0 || activeMap.isAbsolMode())) {
                    Button noDeadly = Button.primary("genericReact1", "No Deadly Plot");
                    Button noBribery = Button.primary("genericReact2", "No Bribery");
                    List<Button> deadlyActionRow = List.of(noBribery, noDeadly);

                    MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), resMessage,
                            deadlyActionRow);
                    if (!activeMap.isFoWMode()) {
                        String loseMessage = "";
                        for (Player los : losers) {
                            if (los != null) {
                                loseMessage = loseMessage
                                        + Helper.getPlayerRepresentation(los, activeMap, event.getGuild(), true);
                            }
                        }
                        event.getChannel().sendMessage(loseMessage + " Please respond to bribery/deadly plot window")
                                .queue();
                    } else {
                        MessageHelper.privatelyPingPlayerList(losers, activeMap,
                                "Please respond to bribery/deadly plot window");
                    }
                } else {
                    String messageShen = "Either both bribery and deadly plot were in the discard or noone could legally play them.";
                    
                    if (activeMap.getCurrentAgendaInfo().contains("Elect Player")&& (!activeMap.isACInDiscard("Confounding") || !activeMap.isACInDiscard("Confusing"))){

                    } else{
                        messageShen = messageShen + " There are no shenanigans possible. Please resolve the agenda. ";
                    }
                    activeMap.getMainGameChannel().sendMessage(messageShen).queue();
                }
                if (activeMap.getCurrentAgendaInfo().contains("Elect Player")
                        && (!activeMap.isACInDiscard("Confounding") || !activeMap.isACInDiscard("Confusing"))) {
                    String resMessage2 = Helper.getGamePing(activeMap.getGuild(), activeMap)
                            + " please react to no confusing/confounding";
                    Button noConfounding = Button.primary("genericReact3", "Refuse Confounding Legal Text");
                    Button noConfusing = Button.primary("genericReact4", "Refuse Confusing Legal Text");
                    List<Button> buttons = List.of(noConfounding, noConfusing);
                    MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), resMessage2, buttons);

                } else {
                    if (activeMap.getCurrentAgendaInfo().contains("Elect Player")) {
                        activeMap.getMainGameChannel()
                                .sendMessage("Both confounding and confusing are in the discard pile. ").queue();

                    }
                }

                String resMessage3 = "Current winner is " + StringUtils.capitalize(winner) + ". "
                        + Helper.getGamePing(activeMap.getGuild(), activeMap)
                        + "When shenanigans have concluded, please confirm resolution or discard the result and manually resolve it yourselves.";
                Button autoResolve = Button.primary("agendaResolution_" + winner, "Resolve with current winner");
                Button manualResolve = Button.danger("autoresolve_manual", "Resolve it Manually");
                List<Button> deadlyActionRow3 = List.of(autoResolve, manualResolve);
                MessageHelper.sendMessageToChannelWithButtons(activeMap.getMainGameChannel(), resMessage3,
                        deadlyActionRow3);

            }
            if (!votes.equalsIgnoreCase("0")) {
                event.getMessage().delete().queue();
            }
            MapSaveLoadManager.saveMap(activeMap, event);

        } else if (buttonID.startsWith("fixerVotes_")) {
            String voteMessage = "Thank you for specifying, please select from the available buttons to vote.";
            String vote = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            int votes = Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(votes - 9, votes);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("exhaustTech_")) {
            String tech = buttonID.replace("exhaustTech_", "");
            if (!tech.equals("st")) {
                if(tech.equals("bs")){
                    ButtonHelper.sendAllTechsNTechSkipPlanetsToReady(activeMap, event, player);
                }
                if(tech.equals("aida") || tech.equals("sar")){
                    if(!activeMap.isFoWMode() && event.getMessageChannel() != activeMap.getActionsChannel()){
                       String msg = (Helper.getPlayerRepresentation(player, activeMap) + " exhausted tech: " +Helper.getTechRepresentation(tech)); 
                        String exhaustedMessage = event.getMessage().getContentRaw();
                        List<ActionRow> actionRow2 = new ArrayList<>();
                        for (ActionRow row : event.getMessage().getActionRows()) {
                            List<ItemComponent> buttonRow = row.getComponents();
                            int buttonIndex = buttonRow.indexOf(event.getButton());
                            if (buttonIndex > -1) {
                                buttonRow.remove(buttonIndex);
                            }
                            if (buttonRow.size() > 0) {
                                actionRow2.add(ActionRow.of(buttonRow));
                            }
                        }
                        if (!exhaustedMessage.contains("Click the names")) {
                            exhaustedMessage = exhaustedMessage + ", "+msg;
                        } else {
                            exhaustedMessage = ident + msg;
                        }
                        event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
                    }
                }
                player.exhaustTech(tech);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        (Helper.getPlayerRepresentation(player, activeMap) + " exhausted tech: "
                                + Helper.getTechRepresentation(tech)));
                if(tech.equals("pi")){
                    List<Button> redistributeButton = new ArrayList<Button>();
                    Button redistribute = Button.success("redistributeCCButtons", "Redistribute CCs");
                    Button deleButton= Button.danger("FFCC_"+player.getFaction()+"_"+"deleteButtons", "Delete These Buttons");
                    redistributeButton.add(redistribute);
                    redistributeButton.add(deleButton);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), false) +" use buttons to redistribute", redistributeButton);
                }
            } else {
                String msg = (Helper.getPlayerRepresentation(player, activeMap) + " used tech: "
                                + Helper.getTechRepresentation(tech));
                    String exhaustedMessage = event.getMessage().getContentRaw();
                     List<ActionRow> actionRow2 = new ArrayList<>();
                    for (ActionRow row : event.getMessage().getActionRows()) {
                        List<ItemComponent> buttonRow = row.getComponents();
                        int buttonIndex = buttonRow.indexOf(event.getButton());
                        if (buttonIndex > -1) {
                            buttonRow.remove(buttonIndex);
                        }
                        if (buttonRow.size() > 0) {
                            actionRow2.add(ActionRow.of(buttonRow));
                        }
                    }
                    if (!exhaustedMessage.contains("Click the names")) {
                        exhaustedMessage = exhaustedMessage + ", "+msg;
                    } else {
                        exhaustedMessage = ident + msg;
                    }
                    event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
            }
        } else if (buttonID.startsWith("planetOutcomes_")) {
            String factionOrColor = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            Player planetOwner = Helper.getPlayerFromColorOrFaction(activeMap, factionOrColor);
            String voteMessage = "Chose to vote for one of " + factionOrColor
                    + "'s planets. Click buttons for which outcome to vote for.";
            List<Button> outcomeActionRow = null;
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeMap, "outcome", null);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("tiedPlanets_")) {
            buttonID = buttonID.replace("tiedPlanets_", "");
            buttonID = buttonID.replace("delete_buttons_outcomeTie*_", "");
            String factionOrColor = buttonID.substring(0, buttonID.length());
            Player planetOwner = Helper.getPlayerFromColorOrFaction(activeMap, factionOrColor);
            String voteMessage = "Chose to break tie for one of " + factionOrColor
                    + "'s planets. Use buttons to select which one.";
            List<Button> outcomeActionRow = null;
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeMap,
                    "delete_buttons_outcomeTie*", null);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            event.getMessage().delete().queue();
        }

        else if (buttonID.startsWith("planetRider_")) {
            buttonID = buttonID.replace("planetRider_", "");
            String factionOrColor = buttonID.substring(0, buttonID.indexOf("_"));
            Player planetOwner = Helper.getPlayerFromColorOrFaction(activeMap, factionOrColor);
            String voteMessage = "Chose to rider for one of " + factionOrColor
                    + "'s planets. Use buttons to select which one.";
            List<Button> outcomeActionRow = null;
            buttonID = buttonID.replace(factionOrColor + "_", "");
            outcomeActionRow = AgendaHelper.getPlanetOutcomeButtons(event, planetOwner, activeMap,
                    finsFactionCheckerPrefix, buttonID);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
            event.getMessage().delete().queue();
        }

        else if (buttonID.startsWith("distinguished_")) {
            String voteMessage = "You added 5 votes to your total. Please select from the available buttons to vote.";
            String vote = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            int votes = Integer.parseInt(vote);
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(votes, votes + 5);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("outcome_")) {
            String outcome = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            String voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
                    + ". Click buttons for amount of votes";
            activeMap.setLatestOutcomeVotedFor(outcome);

            int[] voteArray = AgendaHelper.getVoteTotal(event, player, activeMap);

            int minvote = 1;
            if (player.hasAbility("zeal")) {
                int numPlayers = 0;
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_.isRealPlayer())
                        numPlayers++;
                }
                minvote = minvote + numPlayers;
                
            }
            if (activeMap.getLaws() != null && (activeMap.getLaws().keySet().contains("rep_govt") || activeMap.getLaws().keySet().contains("absol_government"))) {
                    minvote = 1;
                    voteArray[0] = 1;
            }
            if (voteArray[0] - minvote > 20) {
                voteMessage = "Chose to vote for " + StringUtils.capitalize(outcome)
                        + ". You have more votes than discord has buttons. Please further specify your desired vote count by clicking the button which contains your desired vote amount (or largest button).";
            }
            List<Button> voteActionRow = AgendaHelper.getVoteButtons(minvote, voteArray[0]);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("votes_")) {
            String votes = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            String voteMessage = "Chose to vote  " + votes + " votes for "
                    + StringUtils.capitalize(activeMap.getLatestOutcomeVotedFor())
                    + ". Click buttons to choose which planets to exhaust for votes";

            List<Button> voteActionRow = AgendaHelper.getPlanetButtons(event, player, activeMap);
            int allVotes = AgendaHelper.getVoteTotal(event, player, activeMap)[0];
            Button exhausteverything = Button.danger("exhaust_everything_" + allVotes,
                    "Exhaust everything (" + allVotes + ")");
            Button concludeExhausting = Button.danger(finsFactionCheckerPrefix + "delete_buttons_" + votes,
                    "Done exhausting planets.");
            Button OopsMistake = Button.success("refreshVotes_" + votes,
                    "I made a mistake and want to ready some planets");
            voteActionRow.add(exhausteverything);
            voteActionRow.add(concludeExhausting);
            voteActionRow.add(OopsMistake);
            String voteMessage2 = "";
            MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);

            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage2, voteActionRow);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("autoresolve_")) {
            String result = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            if (result.equalsIgnoreCase("manual")) {
                String resMessage3 = "Please select the winner.";
                List<Button> deadlyActionRow3 = AgendaHelper.getAgendaButtons(null, activeMap, "agendaResolution");
                deadlyActionRow3.add(Button.danger("deleteButtons", "Resolve with no result"));
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), resMessage3, deadlyActionRow3);
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("deleteButtons")) {
            buttonID = buttonID.replace("deleteButtons_", "");
            String editedMessage = event.getMessage().getContentRaw();

            if ((buttonLabel.equalsIgnoreCase("Done Gaining CCs")
                    || buttonLabel.equalsIgnoreCase("Done Redistributing CCs")) && editedMessage.contains("CCs have gone from") ) {

                String playerRep = Helper.getPlayerRepresentation(player, activeMap);
                String finalCCs = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
                String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "), editedMessage.length());
                shortCCs = shortCCs.replace("CCs have gone from ", "");
                shortCCs = shortCCs.substring(0,shortCCs.indexOf(" "));
                if (event.getMessage().getContentRaw().contains("Net gain")) {
                    
                    int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                    finalCCs = finalCCs + ". Net CC gain was " + netGain;
                }
                if (!activeMap.isFoWMode()) {
                    if (buttonLabel.equalsIgnoreCase("Done Redistributing CCs")) {
                        MessageHelper.sendMessageToChannel(actionsChannel,
                                playerRep + " Initial CCs were "+shortCCs+". Final CC Allocation Is " + finalCCs);
                    } else {
                        if (buttonID.equalsIgnoreCase("leadership")) {
                            String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-leadership";
                            List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                            for (ThreadChannel threadChannel_ : threadChannels) {
                                if (threadChannel_.getName().equals(threadName)) {
                                    MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_,
                                            playerRep + " Initial CCs were "+shortCCs+". Final CC Allocation Is " + finalCCs);
                                }
                            }

                        } else {
                            MessageHelper.sendMessageToChannel(actionsChannel,
                                    playerRep + " Final CC Allocation Is " + finalCCs);
                        }

                    }

                } else {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            playerRep + " Final CC Allocation Is " + finalCCs);
                }

            }
            if ((buttonLabel.equalsIgnoreCase("Done Exhausting Planets")
                    || buttonLabel.equalsIgnoreCase("Done Producing Units"))
                    && !event.getMessage().getContentRaw().contains("Click the names of the planets you wish")) {
                
                if (activeMap.isFoWMode()) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), editedMessage);
                } else {
                    if (buttonID.equalsIgnoreCase("warfare")) {
                        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-warfare";
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, editedMessage);
                            }
                        }
                    } else if (buttonID.equalsIgnoreCase("leadership")) {
                        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-leadership";
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, editedMessage);
                            }
                        }

                    } else if (buttonID.equalsIgnoreCase("construction")) {
                        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-construction";
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, editedMessage);
                            }
                        }
                    } else if (buttonID.equalsIgnoreCase("technology")) {
                        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-technology";
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();

                        if (!activeMap.getComponentAction()) {
                            for (ThreadChannel threadChannel_ : threadChannels) {
                                if (threadChannel_.getName().equals(threadName)) {
                                    MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, editedMessage);
                                }
                            }
                        } else {
                            MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), editedMessage);

                        }
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(), editedMessage);
                    }
                }

                if (buttonLabel.equalsIgnoreCase("Done Producing Units")) {
                    String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";

                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeMap, player, event);
                    if (player.hasTechReady("sar")) {
                        Button sar = Button.danger("exhaustTech_sar", "Exhaust Self Assembly Routines");
                        buttons.add(sar);
                    }
                    if (player.hasTechReady("aida")) {
                        Button aiDEVButton = Button.danger("exhaustTech_aida", "Exhaust AIDEV");
                        buttons.add(aiDEVButton);
                    }
                    if (player.hasTechReady("st")) {
                        Button sarweenButton = Button.danger("exhaustTech_st", "Use Sarween");
                        buttons.add(sarweenButton);
                    }
                    Button DoneExhausting = null;
                    if (!buttonID.contains("deleteButtons")) {
                        DoneExhausting = Button.danger("deleteButtons_" + buttonID, "Done Exhausting Planets");
                    } else {
                        DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
                    }
                    ButtonHelper.updateMap(activeMap, event);
                    buttons.add(DoneExhausting);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);

                }
            }
            if (buttonLabel.equalsIgnoreCase("Done Exhausting Planets")) {
                if (buttonID.contains("tacticalAction")) {
                    ButtonHelper.exploreDET(player, activeMap, event);
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeMap, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                }
            }

            if (buttonID.equalsIgnoreCase("diplomacy")) {
                

                if (!activeMap.isFoWMode()) {
                    String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-diplomacy";
                    List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                    for (ThreadChannel threadChannel_ : threadChannels) {
                        if (threadChannel_.getName().equals(threadName)) {
                            MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, editedMessage);
                        }
                    }
                } else {
                    MessageHelper.sendMessageToChannel(event.getChannel(), editedMessage);
                }
            }

            event.getMessage().delete().queue();
        }

        else if (buttonID.startsWith("reverse_")) {
            String choice = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            String voteMessage = "Chose to reverse latest rider on " + choice;

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
        } else if (buttonID.startsWith("rider_")) {
            String[] choiceParams = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.lastIndexOf("_")).split(";");
            String choiceType = choiceParams[0];
            String choice = choiceParams[1];

            String rider = buttonID.substring(buttonID.lastIndexOf("_") + 1, buttonID.length());
            String agendaDetails = activeMap.getCurrentAgendaInfo();
            agendaDetails = agendaDetails.substring(agendaDetails.indexOf("_")+1, agendaDetails.length());
           // if(activeMap)
           String cleanedChoice = choice;
           if(agendaDetails.contains("Planet") || agendaDetails.contains("planet")){
                cleanedChoice = Helper.getPlanetRepresentation(choice, activeMap);
           }
            String voteMessage = "Chose to put a " + rider + " on " + StringUtils.capitalize(cleanedChoice);
            String identifier = "";
            if (activeMap.isFoWMode()) {
                identifier = player.getColor();
            } else {
                identifier = player.getFaction();
            }
            HashMap<String, String> outcomes = activeMap.getCurrentAgendaVotes();
            String existingData = outcomes.getOrDefault(choice, "empty");
            if (existingData.equalsIgnoreCase("empty")) {
                existingData = identifier + "_" + rider;
            } else {
                existingData = existingData + ";" + identifier + "_" + rider;
            }
            activeMap.setCurrentAgendaVote(choice, existingData);

            if (!rider.equalsIgnoreCase("Non-AC Rider") && !rider.equalsIgnoreCase("Keleres Rider")
                    && !rider.equalsIgnoreCase("Keleres Xxcha Hero")
                    && !rider.equalsIgnoreCase("Galactic Threat Rider")) {
                List<Button> voteActionRow = new ArrayList<Button>();
                Button concludeExhausting = Button.danger("reverse_" + choice, "Click this if the rider is sabod");
                voteActionRow.add(concludeExhausting);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, voteActionRow);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), voteMessage);
            }

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("construction_")) {
            player.addFollowedSC(4);
            addReaction(event, false, false, "", "");
            String unit = buttonID.replace("construction_", "");
            String message = trueIdentity + " Click the name of the planet you wish to put your unit on";

            List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeMap, unit, "place");
            if (!activeMap.isFoWMode()) {
                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) player.getCardsInfoThread(activeMap),
                        message, buttons);
            } else {
                MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
            }

        } else if (buttonID.startsWith("produceOneUnitInTile_")) {
            buttonID = buttonID.replace("produceOneUnitInTile_", "");
            String type = buttonID.split("_")[1];
            String pos = buttonID.split("_")[0];
            List<Button> buttons = new ArrayList<Button>();
            buttons = Helper.getPlaceUnitButtons(event, player, activeMap,
                    activeMap.getTileByPosition(pos), type, "placeOneNDone_dontskip");
            String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to produce 1 unit. "
                    + ButtonHelper.getListOfStuffAvailableToSpend(player, activeMap);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);

            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("genericReact")) {
            String message = activeMap.isFoWMode() ? "Turned down window" : null;
            addReaction(event, false, false, message, "");
        } else if (buttonID.startsWith("placeOneNDone_")) {
            String unitNPlanet = buttonID.replace("placeOneNDone_", "");
            String skipbuild= unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
            unitNPlanet = unitNPlanet.replace(skipbuild+"_","");
            String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
            String planetName = unitNPlanet.replace(unitLong + "_", "");
            String unit = AliasHandler.resolveUnit(unitLong);
            String producedOrPlaced = "Produced";
             if(skipbuild.equalsIgnoreCase("skipbuild")){
                producedOrPlaced = "Placed";
            }
            String successMessage = "";
            String playerRep = Helper.getPlayerRepresentation(player, activeMap);
            if (unit.equalsIgnoreCase("sd")) {
                if (player.ownsUnit("saar_spacedock") || player.ownsUnit("saar_spacedock2")) {
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), unit, activeMap);
                    successMessage = "Placed a space dock in the space area of the "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + " system.";
                } else if (player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2")) {
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), "csd " + planetName, activeMap);
                    successMessage = "Placed a cabal space dock on "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                            activeMap);
                    successMessage = "Placed a " + Helper.getEmojiFromDiscord("spacedock") + " on "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                }
            } else if (unitLong.equalsIgnoreCase("pds")) {
                new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                        activeMap);
                successMessage = "Placed a " + Helper.getEmojiFromDiscord("pds") + " on "
                        + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
            } else {
                Tile tile = null;
                if (unit.equalsIgnoreCase("gf") || unit.equalsIgnoreCase("mf") || unitLong.equalsIgnoreCase("2gf")) {
                    if (unitLong.equalsIgnoreCase("2gf")) {
                        if(!planetName.contains("space")){
                            new AddUnits().unitParsing(event, player.getColor(),
                                activeMap.getTile(AliasHandler.resolveTile(planetName)), "2 gf " + planetName,
                                activeMap);
                            successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                        }else {
                            tile = activeMap.getTileByPosition(planetName.replace("space",""));
                            new AddUnits().unitParsing(event, player.getColor(),
                                tile, "2 gf ",
                                activeMap);
                            successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " in space.";
                        }
                    } else {
                       
                        if(!planetName.contains("space")){
                            tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
                            new AddUnits().unitParsing(event, player.getColor(),
                                tile, unit + " " + planetName,
                                activeMap);
                            successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                        } else {
                            tile = activeMap.getTileByPosition(planetName.replace("space",""));
                            new AddUnits().unitParsing(event, player.getColor(),
                                tile, unit,
                                activeMap);
                            successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " in space.";
                        }
                        
                    }
                } else {
                    if (unitLong.equalsIgnoreCase("2ff")) {
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName),
                                "2 ff", activeMap);
                        successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("fighter") + " in tile "
                                + AliasHandler.resolveTile(planetName) + ".";
                    } else {
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName),
                                unit, activeMap);
                        successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " in tile "
                                + AliasHandler.resolveTile(planetName) + ".";
                    }

                }
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), playerRep + " " + successMessage);

            String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";

            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeMap, player, event);


            Button DoneExhausting = null;
            if (!buttonID.contains("deleteButtons")) {
                DoneExhausting = Button.danger("deleteButtons_" + buttonID, "Done Exhausting Planets");
            } else {
                DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
            }
            buttons.add(DoneExhausting);
            if(!skipbuild.equalsIgnoreCase("skipbuild")){
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
            }
            

            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("place_")) {
            String unitNPlanet = buttonID.replace("place_", "");
            String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
            String planetName = unitNPlanet.replace(unitLong + "_", "");
            String unit = AliasHandler.resolveUnit(unitLong);

            String successMessage = "";
            String playerRep = Helper.getPlayerRepresentation(player, activeMap);
            if (unit.equalsIgnoreCase("sd")) {
                if (player.ownsUnit("saar_spacedock") || player.ownsUnit("saar_spacedock2")) {
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), unit, activeMap);
                    successMessage = "Placed a space dock in the space area of the "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + " system.";
                } else if (player.ownsUnit("cabal_spacedock") || player.ownsUnit("cabal_spacedock2")) {
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), "csd " + planetName, activeMap);
                    successMessage = "Placed a cabal space dock on "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                            activeMap);
                    successMessage = "Placed a " + Helper.getEmojiFromDiscord("spacedock") + " on "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                }
            } else if (unitLong.equalsIgnoreCase("pds")) {
                new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                        activeMap);
                successMessage = "Placed a " + Helper.getEmojiFromDiscord("pds") + " on "
                        + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
            } else {
                 Tile tile = null;
                 String producedOrPlaced = "Produced";
                if (unit.equalsIgnoreCase("gf") || unit.equalsIgnoreCase("mf") || unitLong.equalsIgnoreCase("2gf")) {
                    if (unitLong.equalsIgnoreCase("2gf")) {
                        if(!planetName.contains("space")){
                            new AddUnits().unitParsing(event, player.getColor(),
                                activeMap.getTile(AliasHandler.resolveTile(planetName)), "2 gf " + planetName,
                                activeMap);
                            successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                        }else {
                            tile = activeMap.getTileByPosition(planetName.replace("space",""));
                            new AddUnits().unitParsing(event, player.getColor(),
                                tile, "2 gf",
                                activeMap);
                            successMessage = producedOrPlaced+" 2 " + Helper.getEmojiFromDiscord("infantry") + " in space.";
                        }
                    } else {
                       
                        if(!planetName.contains("space")){
                            tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
                            new AddUnits().unitParsing(event, player.getColor(),
                                tile, unit + " " + planetName,
                                activeMap);
                            successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " on "+ Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                        } else {
                            tile = activeMap.getTileByPosition(planetName.replace("space",""));
                            new AddUnits().unitParsing(event, player.getColor(),
                                tile, unit,
                                activeMap);
                            successMessage = producedOrPlaced+" a " + Helper.getEmojiFromDiscord(unitLong) + " in space.";
                        }
                        
                    }
                } else {
                    if (unitLong.equalsIgnoreCase("2ff")) {
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName),
                                "2 ff", activeMap);
                        successMessage = "Produced 2 " + Helper.getEmojiFromDiscord("fighter") + " in tile "
                                + AliasHandler.resolveTile(planetName) + ".";
                    } else {
                        new AddUnits().unitParsing(event, player.getColor(), activeMap.getTileByPosition(planetName),
                                unit, activeMap);
                        successMessage = "Produced a " + Helper.getEmojiFromDiscord(unitLong) + " in tile "
                                + AliasHandler.resolveTile(planetName) + ".";
                    }

                }
            }
            if (unit.equalsIgnoreCase("sd") || unitLong.equalsIgnoreCase("pds")) {
                if (activeMap.isFoWMode()) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), playerRep + " " + successMessage);
                } else {
                    List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                    if (threadChannels == null)
                        return;
                    String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-construction";
                    // SEARCH FOR EXISTING OPEN THREAD
                    for (ThreadChannel threadChannel_ : threadChannels) {
                        if (threadChannel_.getName().equals(threadName)) {
                            MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_,
                                    playerRep + " " + successMessage);
                        }
                    }
                }
                if(player.hasLeader("mahactagent")){
                    String message = playerRep + " Would you like to put a cc from reinforcements in the same system?";
                    Button placeCCInSystem = Button.success(
                            finsFactionCheckerPrefix + "reinforcements_cc_placement_" + planetName,
                            "Place A CC From Reinforcements In The System.");
                    Button NoDontWantTo = Button.primary(finsFactionCheckerPrefix + "deleteButtons",
                            "Don't Place A CC In The System.");
                    List<Button> buttons = List.of(placeCCInSystem, NoDontWantTo);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
                }else{
                    if(!player.getSCs().contains(Integer.parseInt("4"))){
                        String color = player.getColor();
                        String tileID = AliasHandler.resolveTile(planetName.toLowerCase());
                        Tile tile = activeMap.getTile(tileID);
                        if (tile == null) {
                            tile = activeMap.getTileByPosition(tileID);
                        }
                        if (Mapper.isColorValid(color)) {
                            AddCC.addCC(event, color, tile);
                        }

                        if (activeMap.isFoWMode()) {
                            MessageHelper.sendMessageToChannel(event.getChannel(),
                                    playerRep + " Placed A CC From Reinforcements In The "
                                            + Helper.getPlanetRepresentation(planetName, activeMap) + " system");
                        } else {
                            List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                            if (threadChannels == null)
                                return;
                            String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-construction";
                            // SEARCH FOR EXISTING OPEN THREAD
                            for (ThreadChannel threadChannel_ : threadChannels) {
                                if (threadChannel_.getName().equals(threadName)) {
                                    MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_,
                                            playerRep + " Placed A CC From Reinforcements In The "
                                                    + Helper.getPlanetRepresentation(planetName, activeMap) + " system");
                                }
                            }
                        }
                    }
                }
                
                event.getMessage().delete().queue();

            } else {
                String editedMessage = event.getMessage().getContentRaw();
                if (editedMessage.contains("Produced")) {
                    editedMessage = editedMessage + "\n " + successMessage;
                } else {
                    editedMessage = playerRep + " " + successMessage;
                }

                if (editedMessage.contains("place 2 infantry")) {
                    successMessage = "Placed 2 " + Helper.getEmojiFromDiscord("infantry") + " on "
                            + Helper.getPlanetRepresentation(planetName, activeMap) + ".";
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                    event.getMessage().delete().queue();
                } else {
                    event.getMessage().editMessage(editedMessage).queue();
                }

            }

            if(player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "titans", event);
            }
            if(player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "saar", event);
            }
            if(player.getLeaderIDs().contains("mentakcommander") && !player.hasLeaderUnlocked("mentakcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "mentak", event);
            }
            if(player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "l1z1x", event);
            }
            if(player.getLeaderIDs().contains("muaatcommander") && !player.hasLeaderUnlocked("muaatcommander") && unitLong.equalsIgnoreCase("warsun")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "muaat", event);
            }
            if(player.getLeaderIDs().contains("argentcommander") && !player.hasLeaderUnlocked("argentcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "argent", event);
            }
            if(player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "naaz", event);
            }
            if(player.getLeaderIDs().contains("arboreccommander") && !player.hasLeaderUnlocked("arboreccommander")){
                ButtonHelper.commanderUnlockCheck(player, activeMap, "arborec", event);
            }

        } else if (buttonID.startsWith("yssarilcommander_")) {

            buttonID = buttonID.replace("yssarilcommander_", "");
            String enemyFaction = buttonID.split("_")[1];
            Player enemy = Helper.getPlayerFromColorOrFaction(activeMap, enemyFaction);
            String message = "";
            String type = buttonID.split("_")[0];
            if(type.equalsIgnoreCase("ac")){
                ShowAllAC.showAll(enemy, player, activeMap);
                message = "Yssaril commander used to look at ACs";
            }
            if(type.equalsIgnoreCase("so")){
                new ShowAllSO().showAll(enemy, player, activeMap);
                message = "Yssaril commander used to look at SOs";
            }
            if(type.equalsIgnoreCase("pn")){
                new ShowAllPN().showAll(enemy, player, activeMap, false);
                message = "Yssaril commander used to look at PNs";
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
            if(activeMap.isFoWMode()){
                MessageHelper.sendMessageToChannel(enemy.getPrivateChannel(), message);
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("freelancersBuild_")) {
            String planet = buttonID.replace("freelancersBuild_", "");
            List<Button> buttons = new ArrayList<Button>();
            Tile tile = activeMap.getTile(AliasHandler.resolveTile(planet));
            if(tile == null){
                tile = activeMap.getTileByPosition(planet);
            }
            buttons = Helper.getPlaceUnitButtons(event, player, activeMap,
                    tile, "freelancers", "placeOneNDone_dontskip");
            String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to produce 1 unit. "
                    + ButtonHelper.getListOfStuffAvailableToSpend(player, activeMap);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("tacticalActionBuild_")) {
            String pos = buttonID.replace("tacticalActionBuild_", "");
            List<Button> buttons = new ArrayList<Button>();
            buttons = Helper.getPlaceUnitButtons(event, player, activeMap,
                    activeMap.getTileByPosition(pos), "tacticalAction", "place");
            String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to produce units. "
                    + ButtonHelper.getListOfStuffAvailableToSpend(player, activeMap);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("getModifyTiles")) {
            List<Button> buttons = new ArrayList<Button>();
            buttons = ButtonHelper.getTilesToModify(player, activeMap, event);
            String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to select the tile in which you wish to modify units. ";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        } else if (buttonID.startsWith("genericModify_")) {
            String pos = buttonID.replace("genericModify_", "");
            Tile tile = activeMap.getTileByPosition(pos);
            ButtonHelper.offerBuildOrRemove(player, activeMap, event, tile);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("genericBuild_")) {
            String pos = buttonID.replace("genericBuild_", "");
            activeMap.resetCurrentMovedUnitsFrom1TacticalAction();
            List<Button> buttons = new ArrayList<Button>();
            buttons = Helper.getPlaceUnitButtons(event, player, activeMap,
                    activeMap.getTileByPosition(pos), "genericBuild", "place");
            String message = Helper.getPlayerRepresentation(player, activeMap) + " Use the buttons to produce units. ";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("starforgeTile_")) {
            String pos = buttonID.replace("starforgeTile_", "");

            List<Button> buttons = new ArrayList<Button>();
            Button starforgerStroter = Button.danger("starforge_destroyer_" + pos, "Starforge Destroyer")
                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("destroyer")));
            buttons.add(starforgerStroter);
            Button starforgerFighters = Button.danger("starforge_fighters_" + pos, "Starforge 2 Fighters")
                    .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("fighter")));
            buttons.add(starforgerFighters);
            String message = "Use the buttons to select what you would like to starforge.";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("starforge_")) {
            String unitNPlace = buttonID.replace("starforge_", "");
            String unit = unitNPlace.split("_")[0];
            String pos = unitNPlace.split("_")[1];
            Tile tile = activeMap.getTileByPosition(pos);
            String successMessage = "Reduced strategy pool CCs by 1 (" + (player.getStrategicCC()) + "->"
                    + (player.getStrategicCC() - 1) + ")";
            player.setStrategicCC(player.getStrategicCC() - 1);
            ButtonHelper.resolveMuaatCommanderCheck(player, activeMap, event);
            MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
            List<Button> buttons = ButtonHelper.getStartOfTurnButtons(player, activeMap, true, event);
            if (unit.equals("destroyer")) {
                new AddUnits().unitParsing(event, player.getColor(), tile, "1 destroyer", activeMap);
                successMessage = "Produced 1 " + Helper.getEmojiFromDiscord("destroyer") + " in tile "
                        + tile.getRepresentationForButtons(activeMap, player) + ".";

            } else {
                new AddUnits().unitParsing(event, player.getColor(), tile, "2 ff", activeMap);
                successMessage = "Produced 2 " + Helper.getEmojiFromDiscord("fighter") + " in tile "
                        + tile.getRepresentationForButtons(activeMap, player) + ".";
            }
            successMessage = ButtonHelper.putInfWithMechsForStarforge(pos, successMessage, activeMap, player, event);

            MessageHelper.sendMessageToChannel(event.getChannel(), successMessage);
            String message = "Use buttons to end turn or do another action";
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("planetAbilityExhaust_")) {
            String planet = buttonID.replace("planetAbilityExhaust_", "");
            new PlanetExhaustAbility().doAction(player, planet, activeMap);
        } else if (buttonID.startsWith("scPick_")) {
            Stats stats = new Stats();
            String num = buttonID.replace("scPick_", "");
            int scpick = Integer.parseInt(num);
            boolean pickSuccessful = stats.secondHalfOfPickSC((GenericInteractionCreateEvent) event, activeMap, player,
                    scpick);
            if (pickSuccessful) {
                new SCPick().secondHalfOfSCPick((GenericInteractionCreateEvent) event, player, activeMap, scpick);
                event.getMessage().delete().queue();
            }

        } else if (buttonID.startsWith("milty_")) {

//            System.out.println("MILTY");
        } else if (buttonID.startsWith("ring_")) {
            List<Button> ringButtons = ButtonHelper.getTileInARing(player, activeMap, buttonID, event);
            String num = buttonID.replace("ring_", "");
            String message = "";
            if (!num.equalsIgnoreCase("corners")) {
                int ring = Integer.parseInt(num.charAt(0) + "");
                if (ring > 4 && !num.contains("left") && !num.contains("right")) {
                    message = "That ring is very large. Specify if your tile is on the left or right side of the map (center will be counted in both).";
                } else {
                    message = "Click the tile that you want to activate.";
                }
            } else {
                message = "Click the tile that you want to activate.";
            }

            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("getACFrom_")) {
            String faction = buttonID.replace("getACFrom_", "");
            Player victim = Helper.getPlayerFromColorOrFaction(activeMap, faction);
            List<Button> buttons = ButtonHelper.getButtonsToTakeSomeonesAC(activeMap, player, victim);
            ShowAllAC.showAll(victim, player, activeMap);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeMap),
                    Helper.getPlayerRepresentation(player, activeMap, activeMap.getGuild(), true)
                            + " Select which AC you would like to steal",
                    buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("doActivation_")) {
            String pos = buttonID.replace("doActivation_", "");
            ButtonHelper.resolveOnActivationEnemyAbilities(activeMap, activeMap.getTileByPosition(pos), player, false);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("ringTile_")) {
            String pos = buttonID.replace("ringTile_", "");
            List<Button> systemButtons = ButtonHelper.getTilesToMoveFrom(player, activeMap, event);
            activeMap.setActiveSystem(pos);
            MessageHelper.sendMessageToChannel(event.getChannel(), trueIdentity + " activated "
                    + activeMap.getTileByPosition(pos).getRepresentationForButtons(activeMap, player));
            
            List<Player> playersWithPds2 = new ArrayList<Player>();
            if(!activeMap.isFoWMode()){
                playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player, activeMap, pos);
                int abilities = ButtonHelper.resolveOnActivationEnemyAbilities(activeMap, activeMap.getTileByPosition(pos), player, true);
                if(abilities > 0){
                    List<Button> buttons = new ArrayList<Button>();
                    buttons.add(Button.success(finsFactionCheckerPrefix+"doActivation_"+pos, "Confirm"));
                    buttons.add(Button.danger(finsFactionCheckerPrefix+"deleteButtons", "This activation was a mistake"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), ident+" You are about to automatically trigger some abilities by activating this system, are you sure you want to proceed?", buttons);
                }
            }else{
                 List<Player> playersAdj = FoWHelper.getAdjacentPlayers(activeMap, pos, true);
                for (Player player_ : playersAdj) {
                    String playerMessage = Helper.getPlayerRepresentation(player_, activeMap, event.getGuild(), true) + " - System " + pos + " has been activated ";
                    boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, playerMessage);
                }
                ButtonHelper.resolveOnActivationEnemyAbilities(activeMap, activeMap.getTileByPosition(pos), player, false);
            }
            if (!activeMap.isFoWMode() && playersWithPds2.size() > 0) {
                String pdsMessage = trueIdentity + " this is a courtesy notice that the selected system is in range of deep space cannon units owned by";

                for(Player playerWithPds : playersWithPds2){
                    pdsMessage = pdsMessage + " "+Helper.getPlayerRepresentation(playerWithPds, activeMap, activeMap.getGuild(), false);
                }
                MessageHelper.sendMessageToChannel(event.getChannel(), pdsMessage);
            }
            List<Button> button2 = ButtonHelper.scanlinkResolution(player, activeMap, event);
            if (player.getTechs().contains("sdn") && !button2.isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Please resolve scanlink",
                        button2);
                if(player.hasAbility("awaken")){
                    ButtonHelper.resolveTitanShenanigansOnActivation(player, activeMap, activeMap.getTileByPosition(pos), event);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                        "\n\nUse buttons to select the first system you want to move from", systemButtons);
            } else {
                if(player.hasAbility("awaken")){
                    ButtonHelper.resolveTitanShenanigansOnActivation(player, activeMap, activeMap.getTileByPosition(pos), event);
                }
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                        "Use buttons to select the first system you want to move from", systemButtons);
            }
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("genericRemove_")) {
            String pos = buttonID.replace("genericRemove_", "");
            activeMap.resetCurrentMovedUnitsFrom1System();
            activeMap.resetCurrentMovedUnitsFrom1TacticalAction();
            List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeMap,
                    activeMap.getTileByPosition(pos), "Remove");
            activeMap.resetCurrentMovedUnitsFrom1System();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Chose to remove units from "
                            + activeMap.getTileByPosition(pos).getRepresentationForButtons(activeMap, player));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),"Use buttons to select the units you want to remove.",systemButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("tacticalMoveFrom_")) {
            String pos = buttonID.replace("tacticalMoveFrom_", "");
            List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeMap,
                    activeMap.getTileByPosition(pos), "Move");
            activeMap.resetCurrentMovedUnitsFrom1System();
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                    "Chose to move from "
                            + activeMap.getTileByPosition(pos).getRepresentationForButtons(activeMap, player)
                            + ". Use buttons to select the units you want to move.",
                    systemButtons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("purge_Frags_")) {
            String typeNAmount = buttonID.replace("purge_Frags_", "");
            String type = typeNAmount.split("_")[0];
            int count = Integer.parseInt(typeNAmount.split("_")[1]);
            List<String> fragmentsToPurge = new ArrayList<String>();
            ArrayList<String> playerFragments = player.getFragments();
            for (String fragid : playerFragments) {
                if (fragid.contains(type.toLowerCase())) {
                    fragmentsToPurge.add(fragid);
                }
            }
            while (fragmentsToPurge.size() > count) {
                fragmentsToPurge.remove(0);
            }

            for (String fragid : fragmentsToPurge) {
                player.removeFragment(fragid);
            }
            String message = Helper.getPlayerRepresentation(player, activeMap) + " purged fragments: "
                    + fragmentsToPurge.toString();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        }

        else if (buttonID.startsWith("unitTactical")) {
            String remove = "Move";
             HashMap<String, Integer> currentSystem = activeMap.getCurrentMovedUnitsFrom1System();
            HashMap<String, Integer> currentActivation = activeMap.getMovedUnitsFromCurrentActivation();
            String rest = "";
            if(buttonID.contains("Remove")){
                remove = "Remove";
                rest = buttonID.replace("unitTacticalRemove_", "");
            }else{
                rest = buttonID.replace("unitTacticalMove_", "");
            }
            String pos = rest.substring(0, rest.indexOf("_"));
            Tile tile = activeMap.getTileByPosition(pos);
            rest = rest.replace(pos + "_", "");

            if(rest.contains("All")){
               
                if(rest.contains("reverse"))
                {
                    for(String unit : currentSystem.keySet()){
                        
                        String unitkey = "";
                        String planet = "";
                        String origUnit = unit;
                        String damagedMsg = "";
                        int amount = currentSystem.get(unit);
                        if(unit.contains("_"))
                        {
                            unitkey =unit.split("_")[0];
                            planet =unit.split("_")[1];
                        }else{
                            unitkey = unit;
                        }
                        if (currentActivation.containsKey(unitkey)) {
                            activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey,
                                    currentActivation.get(unitkey) - amount);
                        }
                        if(unitkey.contains("damaged")){
                            unitkey = unitkey.replace("damaged", "");
                            damagedMsg = " damaged ";
                        }
                        new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTileByPosition(pos), (amount) + " " + unitkey + " " + planet, activeMap);
                        if(damagedMsg.contains("damaged")){
                            if (planet.equalsIgnoreCase("")) {
                                planet = "space";
                            }
                            String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
                            activeMap.getTileByPosition(pos).addUnitDamage(planet, unitID, (amount));
                        }
                    }
                   
                    activeMap.resetCurrentMovedUnitsFrom1System();
                }else{
                    java.util.Map<String, String> unitRepresentation = Mapper.getUnitImageSuffixes();
                    java.util.Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
                    String cID = Mapper.getColorID(player.getColor());
                    for (java.util.Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                        String name = entry.getKey();
                        String representation = planetRepresentations.get(name);
                        if (representation == null){
                            representation = name;
                        }
                        UnitHolder unitHolder = entry.getValue();
                        HashMap<String, Integer> units1 = unitHolder.getUnits();
                        HashMap<String, Integer> units = new HashMap<String, Integer>();
                        units.putAll(units1);
                    
                        if (unitHolder instanceof Planet planet) {
                            for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                                String key = unitEntry.getKey();
                                if ((key.endsWith("gf.png") || key.endsWith("mf.png")) &&key.contains(cID)) {
                                    String unitKey = key.replace(cID+"_", "");
                                    unitKey = unitKey.replace(".png", "");
                                    unitKey = ButtonHelper.getUnitName(unitKey);
                                    int amount = unitEntry.getValue();
                                    rest = unitKey+"_"+unitHolder.getName();
                                    if (currentSystem.containsKey(rest)) {
                                        activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
                                    } else {
                                        activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                    }
                                    if (currentActivation.containsKey(unitKey)) {
                                        activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey,
                                                currentActivation.get(unitKey) + amount);
                                    } else {
                                        activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey, amount);
                                    }
                                    String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());

                                    new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), unitEntry.getValue(), unitHolder.getName(), unitID, player.getColor(), false);

                                  //  validTile2 = Button.danger(finChecker+"unitTactical"+moveOrRemove+"_"+tile.getPosition()+"_"+x+unitKey+"_"+representation, moveOrRemove+" "+x+" Infantry from "+Helper.getPlanetRepresentation(representation.toLowerCase(), activeMap)).withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("infantry")));
                                }
                            }
                        }
                        else{
                            for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {

                                String key = unitEntry.getKey();
                                for (String unitRepresentationKey : unitRepresentation.keySet()) {
                                    if (key.endsWith(unitRepresentationKey) && key.contains(cID)) {
                                        
                                        String unitKey = key.replace(cID+"_", "");
                                        
                                        int totalUnits = unitEntry.getValue();
                                        int amount = unitEntry.getValue();
                                        unitKey  = unitKey.replace(".png", "");
                                        unitKey = ButtonHelper.getUnitName(unitKey);
                                        int damagedUnits = 0;
                                        if(unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null){
                                            damagedUnits = unitHolder.getUnitDamage().get(key);
                                        }
                                        String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitKey), player.getColor());

                                        new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), totalUnits, "space", unitID, player.getColor(), false);
                                        if(damagedUnits > 0){
                                            rest = unitKey+"damaged";
                                            amount = damagedUnits;
                                            if (currentSystem.containsKey(rest)) {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
                                            } else {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                            }
                                            if (currentActivation.containsKey(unitKey)) {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey,
                                                        currentActivation.get(unitKey) + amount);
                                            } else {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey, amount);
                                            }
                                        }
                                        rest = unitKey;
                                        amount = totalUnits - damagedUnits;
                                        if(amount > 0){
                                            if (currentSystem.containsKey(rest)) {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
                                            } else {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
                                            }
                                            if (currentActivation.containsKey(unitKey)) {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey,
                                                        currentActivation.get(unitKey) + amount);
                                            } else {
                                                activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitKey, amount);
                                            }
                                        }
                                        
                                        
                                    }
                                }
                            }
                        }             
                    }
                }
                String message = ButtonHelper.buildMessageFromDisplacedUnits(activeMap, false, player, remove);
                List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeMap,
                        activeMap.getTileByPosition(pos), remove);
                event.getMessage().editMessage(message)
                        .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
                return;
            }
            int amount = Integer.parseInt(rest.charAt(0) + "");
            if (rest.contains("_reverse")) {
                amount = amount * -1;
                rest = rest.replace("_reverse", "");
            }
            rest = rest.substring(1, rest.length());
            String unitkey = "";
            String planet = "";

            if (rest.contains("_")) {
                unitkey = rest.split("_")[0];
                planet = rest.split("_")[1].toLowerCase().replace(" ", "");
            } else {
                unitkey = rest;
            }
            unitkey = unitkey.replace("damaged", "");
            planet = planet.replace("damaged", "");
            String unitID = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
            rest = rest.replace("damaged", "");
            if (amount < 0) {
                
                new AddUnits().unitParsing(event, player.getColor(),
                        activeMap.getTileByPosition(pos), (amount * -1) + " " + unitkey + " " + planet, activeMap);
                if(buttonLabel.toLowerCase().contains("damaged")){
                    if (planet.equalsIgnoreCase("")) {
                        planet = "space";
                    }
                    activeMap.getTileByPosition(pos).addUnitDamage(planet, unitID, (amount * -1));
                }
            } else {
                String planetName = "";
                if (planet.equalsIgnoreCase("")) {
                    planetName = "space";
                } else {

                    planetName = planet.toLowerCase().replace(" ", "");
                    planetName = planet.replace("'", "");
                    planetName = AliasHandler.resolvePlanet(planetName);

                }
                
                if(buttonLabel.toLowerCase().contains("damaged")){
                    new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), true);
                }else{
                    new RemoveUnits().removeStuff(event, activeMap.getTileByPosition(pos), amount, planetName, unitID, player.getColor(), false);
                }
               // String key = Mapper.getUnitID(AliasHandler.resolveUnit(unitkey), player.getColor());
                //activeMap.getTileByPosition(pos).removeUnit(planetName, key, amount);
            }
            if(buttonLabel.toLowerCase().contains("damaged")){ 
                unitkey = unitkey + "damaged";
                rest = rest+"damaged";
            }
            if (currentSystem.containsKey(rest)) {
                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
            } else {
                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
            }
            if(currentSystem.get(rest) == 0){
                currentSystem.remove(rest);
            }
            if (currentActivation.containsKey(unitkey)) {
                activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey,
                        currentActivation.get(unitkey) + amount);
            } else {
                activeMap.setSpecificCurrentMovedUnitsFrom1TacticalAction(unitkey, amount);
            }
            String message = ButtonHelper.buildMessageFromDisplacedUnits(activeMap, false, player, remove);
            List<Button> systemButtons = ButtonHelper.getButtonsForAllUnitsInSystem(player, activeMap,
                    activeMap.getTileByPosition(pos), remove);
            event.getMessage().editMessage(message)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();

        } else if (buttonID.startsWith("landUnits_")) {
            String rest = buttonID.replace("landUnits_", "");
            String pos = rest.substring(0, rest.indexOf("_"));
            rest = rest.replace(pos + "_", "");
            int amount = Integer.parseInt(rest.charAt(0) + "");
            rest = rest.substring(1, rest.length());
            String unitkey = "";
            String planet = "";
            if (rest.contains("_")) {
                unitkey = rest.split("_")[0];
                planet = rest.split("_")[1].replace(" ", "").toLowerCase();
            } else {
                unitkey = rest;
            }
            if(buttonLabel.toLowerCase().contains("damaged")){
                rest = rest+"damaged";
            }
            HashMap<String, Integer> currentSystem = activeMap.getCurrentMovedUnitsFrom1System();
            if (currentSystem.containsKey(rest)) {
                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, currentSystem.get(rest) + amount);
            } else {
                activeMap.setSpecificCurrentMovedUnitsFrom1System(rest, amount);
            }
            String message = ButtonHelper.buildMessageFromDisplacedUnits(activeMap, true, player, "Move");
            List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeMap, event);
            event.getMessage().editMessage(message)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
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
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        "Could not resolve tileID:  `" + tileID + "`. Tile not found");
                return;
            }
            String color = player.getColor();
            if (Mapper.isColorValid(color)) {
                AddCC.addCC(event, color, tile);
            }

            if (activeMap.isFoWMode()) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                        playerRep + " Placed A CC From Reinforcements In The "
                                + Helper.getPlanetRepresentation(planet, activeMap) + " system");
            } else {
                List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                if (threadChannels == null)
                    return;
                String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-construction";
                // SEARCH FOR EXISTING OPEN THREAD
                for (ThreadChannel threadChannel_ : threadChannels) {
                    if (threadChannel_.getName().equals(threadName)) {
                        MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_,
                                playerRep + " Placed A CC From Reinforcements In The "
                                        + Helper.getPlanetRepresentation(planet, activeMap) + " system");
                    }
                }
            }
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("transactWith_")) {
            String faction = buttonID.replace("transactWith_", "");
            Player p2 = Helper.getPlayerFromColorOrFaction(activeMap, faction);
            List<Button> buttons = new ArrayList<Button>();
            buttons = ButtonHelper.getStuffToTransButtons(activeMap, player, p2);
            String message = Helper.getPlayerRepresentation(player, activeMap)
                    + " Use the buttons to select what you want to transact";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("transact_")) {
            ButtonHelper.resolveSpecificTransButtons(activeMap, player, buttonID, event);
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("play_after_")) {
            String riderName = buttonID.replace("play_after_", "");
            addReaction(event, true, true, "Playing " + riderName, riderName + " Played");
            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, activeMap,
                    finsFactionCheckerPrefix);
            MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel,
                    "Please select your rider target", activeMap, player, riderButtons);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(activeMap);
            if (riderName.equalsIgnoreCase("Keleres Rider")) {
                ButtonHelper.resolvePNPlay(AliasHandler.resolvePromissory(riderName), player, activeMap, event);
            }
            if (riderName.equalsIgnoreCase("Keleres Xxcha Hero")) {
                Leader playerLeader = player.getLeader("keleresheroodlynn");
                StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap))
                        .append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
                boolean purged = player.removeLeader(playerLeader);
                if (purged) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            message.toString() + " - Leader Oodlynn has been purged");
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Leader was not purged - something went wrong");
                }
            }
            MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel,
                    "Please indicate no afters again.", activeMap, afterButtons, "after");
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("componentActionRes_")) {
            ButtonHelper.resolvePressedCompButton(activeMap, player, event, buttonID);
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("addIonStorm_")) {
            String pos = buttonID.substring(buttonID.lastIndexOf("_")+1, buttonID.length());
            Tile tile = activeMap.getTileByPosition(pos);
            if(buttonID.contains("alpha")){
                String tokenFilename = Mapper.getTokenID("ionalpha");
                tile.addToken(tokenFilename, Constants.SPACE);
                MessageHelper.sendMessageToChannel(event.getChannel(), "Added ionstorm alpha to "+tile.getRepresentation());

            }else{
                String tokenFilename = Mapper.getTokenID("ionbeta");
                tile.addToken(tokenFilename, Constants.SPACE);
                MessageHelper.sendMessageToChannel(event.getChannel(), "Added ionstorm beta to "+tile.getRepresentation());
            }

            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("terraformPlanet_")) {
            String planet = buttonID.replace("terraformPlanet_","");
            UnitHolder unitHolder = activeMap.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            planetReal.addToken(Constants.ATTACHMENT_TITANSPN_PNG);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Attached terraform to "+Helper.getPlanetRepresentation(planet, activeMap));
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("nanoforgePlanet_")) {
            String planet = buttonID.replace("nanoforgePlanet_","");
            UnitHolder unitHolder = activeMap.getPlanetsInfo().get(planet);
            Planet planetReal = (Planet) unitHolder;
            planetReal.addToken("attachment_nanoforge.png");
            MessageHelper.sendMessageToChannel(event.getChannel(), "Attached nanoforge to "+Helper.getPlanetRepresentation(planet, activeMap));
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("resolvePNPlay_")) {
            String pnID = buttonID.replace("resolvePNPlay_", "");
            ButtonHelper.resolvePNPlay(pnID, player, activeMap, event);
            if(!pnID.equalsIgnoreCase("bmf")){
                event.getMessage().delete().queue();
            }
            
        } else if (buttonID.startsWith("send_")) {
            ButtonHelper.resolveSpecificTransButtonPress(activeMap, player, buttonID, event);
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("replacePDSWithFS_")) {
            buttonID = buttonID.replace("replacePDSWithFS_", "");
            String planet = buttonID;
            String message = ident + " replaced "+ Helper.getEmojiFromDiscord("pds") +" on " + Helper.getPlanetRepresentation(planet,activeMap)+ " with a "+ Helper.getEmojiFromDiscord("flagship");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            new AddUnits().unitParsing(event, player.getColor(),activeMap.getTile(AliasHandler.resolveTile(planet)), "flagship",activeMap);
            String key = Mapper.getUnitID(AliasHandler.resolveUnit("pds"), player.getColor());
            activeMap.getTile(AliasHandler.resolveTile(planet)).removeUnit(planet,key, 1);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("putSleeperOnPlanet_")) {
            buttonID = buttonID.replace("putSleeperOnPlanet_", "");
            String planet = buttonID;
            String message = ident+" put a sleeper on " + Helper.getPlanetRepresentation(planet,activeMap);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            new SleeperToken().addOrRemoveSleeper(event, activeMap, planet, player);
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("removeSleeperFromPlanet_")) {
            buttonID = buttonID.replace("removeSleeperFromPlanet_", "");
            String planet = buttonID;
            String message = ident + " removed a sleeper from " + planet;
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            new SleeperToken().addOrRemoveSleeper(event, activeMap, planet, player);
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("replaceSleeperWith_")) {
            buttonID = buttonID.replace("replaceSleeperWith_", "");
            String planetName = buttonID.split("_")[1];
            String unit = buttonID.split("_")[0];
            String message = "";
            new SleeperToken().addOrRemoveSleeper(event, activeMap, planetName, player);
            if(unit.equalsIgnoreCase("mech")){
                new AddUnits().unitParsing(event, player.getColor(),activeMap.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName + ", inf "+planetName,activeMap);
                message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName,activeMap) + " with a "+ Helper.getEmojiFromDiscord("mech") +" and "+ Helper.getEmojiFromDiscord("infantry");
            }else{
                new AddUnits().unitParsing(event, player.getColor(),activeMap.getTile(AliasHandler.resolveTile(planetName)), "pds " + planetName,activeMap);
                message = ident + " replaced a sleeper on " + Helper.getPlanetRepresentation(planetName,activeMap) + " with a "+ Helper.getEmojiFromDiscord("pds");
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("topAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            new PutAgendaTop().putTop((GenericInteractionCreateEvent) event, Integer.parseInt(agendaNumID), activeMap);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Put " + agendaNumID + " on the top of the agenda deck.");
        } else if (buttonID.startsWith("primaryOfWarfare")) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeMap, event, "warfare");
            MessageChannel channel = event.getMessageChannel();
            if(activeMap.isFoWMode()){
                channel = player.getPrivateChannel();
            }
            MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
        } else if (buttonID.startsWith("mahactCommander")) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, activeMap, event, "mahactCommander");
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to remove token.", buttons);
            event.getMessage().delete().queue();
         } else if (buttonID.startsWith("useTA_")) {
            String ta = buttonID.replace("useTA_", "") + "_ta";
            ButtonHelper.resolvePNPlay(ta,  player,  activeMap,  event);
            event.getMessage().delete().queue();
        } else if (buttonID.startsWith("removeCCFromBoard_")) {
            ButtonHelper.resolveRemovingYourCC(player, activeMap, event, buttonID);
            event.getMessage().delete().queue();

        } else if (buttonID.startsWith("bottomAgenda_")) {
            String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            new PutAgendaBottom().putBottom((GenericInteractionCreateEvent) event, Integer.parseInt(agendaNumID),
                    activeMap);
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Put " + agendaNumID + " on the bottom of the agenda deck.");
        } else if (buttonID.startsWith("agendaResolution_")) {
            String winner = buttonID.substring(buttonID.indexOf("_") + 1, buttonID.length());
            String agendaid = activeMap.getCurrentAgendaInfo().substring(
                    activeMap.getCurrentAgendaInfo().lastIndexOf("_") + 1, activeMap.getCurrentAgendaInfo().length());
            int aID = 0;
            if (agendaid.equalsIgnoreCase("CL")) {
                String id2 = activeMap.revealAgenda(false);
                LinkedHashMap<String, Integer> discardAgendas = activeMap.getDiscardAgendas();
                AgendaModel agendaDetails = Mapper.getAgenda(id2);
                String agendaName = agendaDetails.getName();
                MessageHelper.sendMessageToChannel(actionsChannel, "The hidden agenda was " + agendaName
                        + "! You can find it added as a law or in the discard.");
                Integer uniqueID = discardAgendas.get(id2);
                aID = uniqueID;
            } else {
                aID = Integer.parseInt(agendaid);
            }

            if (activeMap.getCurrentAgendaInfo().startsWith("Law")) {
                if (activeMap.getCurrentAgendaInfo().contains("Player")) {
                    Player player2 = Helper.getPlayerFromColorOrFaction(activeMap, winner);
                    if (player2 != null) {
                        activeMap.addLaw(aID, winner);
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Added Law with " + winner + " as the elected!");
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
                            LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>(
                                    player_.getSecretsScored());
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
                 LinkedHashMap<String, Integer> discardAgendas = activeMap.getDiscardAgendas();
                String agID ="";
                for (java.util.Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
                    if (agendas.getValue().equals(aID)) {
                        agID = agendas.getKey();
                        break;
                    }
                }
                
                if(agID.equalsIgnoreCase("mutiny")){
                    List<Player> winOrLose = null;
                    StringBuilder message = new StringBuilder("");
                    Integer poIndex = 5;
                    if (winner.equalsIgnoreCase("for")) {
                        winOrLose = AgendaHelper.getWinningVoters(winner, activeMap);
                        poIndex = activeMap.addCustomPO("Mutiny", 1);

                    }else{
                        winOrLose = AgendaHelper.getLosingVoters(winner, activeMap);
                        poIndex = activeMap.addCustomPO("Mutiny", -1);
                    }
                    message.append("Custom PO 'Mutiny' has been added.\n");
                    for(Player playerWL : winOrLose){
                        activeMap.scorePublicObjective(playerWL.getUserID(), poIndex);
                        message.append(Helper.getPlayerRepresentation(playerWL, activeMap)).append(" scored 'Mutiny'\n");
                    }
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message.toString());
                        
                        
                       
                }
                if (activeMap.getCurrentAgendaInfo().contains("Law")) {
                    // Figure out law
                }
            }
            String summary = AgendaHelper.getSummaryOfVotes(activeMap, false);
            List<Player> riders = AgendaHelper.getWinningRiders(summary, winner, activeMap);
            String ridSum = " has a rider to resolve.";
            for (Player rid : riders) {
                String rep = Helper.getPlayerRepresentation(rid, activeMap, event.getGuild(), true);
                if (rid != null) {
                    String message = "";
                    if (rid.hasAbility("future_sight")) {
                        message = rep
                                + "You have a rider to resolve or you voted for the correct outcome. Either way a tg has been added to your total due to your future sight ability. ("
                                + rid.getTg() + "-->" + (rid.getTg() + 1) + ")";
                        rid.setTg(rid.getTg() + 1);
                        ButtonHelper.pillageCheck(rid, activeMap);
                    } else {
                        message = rep + "You have a rider to resolve";
                    }
                    if (activeMap.isFoWMode()) {
                        MessageHelper.sendPrivateMessageToPlayer(rid, activeMap, message);
                    } else {
                        MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message);
                    }
                }
            }
            if (activeMap.isFoWMode()) {
                MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(),
                        "Sent pings to all those who ridered");
            } else {
                if (riders.size() > 0) {
                    MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), ridSum);
                }

            }
            String voteMessage = "Resolving vote for " + StringUtils.capitalize(winner)
                    + ". Click the buttons for next steps after you're done resolving riders.";

            Button flipNextAgenda = Button.primary("flip_agenda", "Flip Agenda");
            Button proceedToStrategyPhase = Button.success("proceed_to_strategy",
                    "Proceed to Strategy Phase (will run agenda cleanup and ping speaker)");
            List<Button> resActionRow = List.of(flipNextAgenda, proceedToStrategyPhase);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, resActionRow);

            event.getMessage().delete().queue();

        } else {
            switch (buttonID) {
                // AFTER THE LAST PLAYER PASS COMMAND, FOR SCORING
                case Constants.PO_NO_SCORING -> {
                    String message = Helper.getPlayerRepresentation(player, activeMap)
                            + " - no Public Objective scored.";
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                    }
                    String reply = activeMap.isFoWMode() ? "No public objective scored" : null;
                    addReaction(event, false, false, reply, "");
                }
                case "warfareBuild" -> {
                    List<Button> buttons = new ArrayList<Button>();
                    buttons = Helper.getPlaceUnitButtons(event, player, activeMap,
                            activeMap.getTile(AliasHandler.resolveTile(player.getFaction())), "warfare", "place");
                    String message = Helper.getPlayerRepresentation(player, activeMap)
                            + " Use the buttons to produce. Reminder that when following warfare, you can only use 1 dock in your home system. "
                            + ButtonHelper.getListOfStuffAvailableToSpend(player, activeMap);
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeMap), message,
                                buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "transaction" -> {
                    List<Button> buttons = new ArrayList<Button>();
                    buttons = ButtonHelper.getPlayersToTransact(activeMap, player);
                    String message = Helper.getPlayerRepresentation(player, activeMap)
                            + " Use the buttons to select which player you wish to transact with";
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);

                }
                case "acquireATech" -> {

                    List<Button> buttons = new ArrayList<Button>();

                    Button propulsionTech = Button.primary(finsFactionCheckerPrefix + "getAllTechOfType_propulsion", "Get a Blue Tech");
                    propulsionTech = propulsionTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Propulsiontech")));
                    buttons.add(propulsionTech);

                    Button bioticTech = Button.success(finsFactionCheckerPrefix + "getAllTechOfType_biotic", "Get a Green Tech");
                    bioticTech = bioticTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Biotictech")));
                    buttons.add(bioticTech);

                    Button cyberneticTech = Button.secondary(finsFactionCheckerPrefix + "getAllTechOfType_cybernetic", "Get a Yellow Tech");
                    cyberneticTech = cyberneticTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Cybernetictech")));
                    buttons.add(cyberneticTech);

                    Button warfareTech = Button.danger(finsFactionCheckerPrefix + "getAllTechOfType_warfare", "Get a Red Tech");
                    warfareTech = warfareTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("Warfaretech")));
                    buttons.add(warfareTech);

                    Button unitupgradesTech = Button.secondary(finsFactionCheckerPrefix + "getAllTechOfType_unitupgrade", "Get A Unit Upgrade Tech");
                    unitupgradesTech = unitupgradesTech.withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("UnitUpgradeTech")));
                    buttons.add(unitupgradesTech);

                    String message = Helper.getPlayerRepresentation(player, activeMap) + " What type of tech would you want?";
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons((MessageChannel) player.getCardsInfoThread(activeMap), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case Constants.SO_NO_SCORING -> {
                    String message = Helper.getPlayerRepresentation(player, activeMap)
                            + " - no Secret Objective scored.";
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                    }
                    String reply = activeMap.isFoWMode() ? "No secret objective scored" : null;
                    addReaction(event, false, false, reply, "");
                }
                // AFTER AN ACTION CARD HAS BEEN PLAYED
                case "no_sabotage" -> {
                    String message = activeMap.isFoWMode() ? "No sabotage" : null;
                    addReaction(event, false, false, message, "");
                }
                case "titansCommander" -> {
                    int cTG = player.getTg();
                    int fTG = cTG+1;
                    player.setTg(fTG);
                    String msg = " used Titans commander to gain a tg ("+cTG+"->"+fTG+"). ";
                    String exhaustedMessage = event.getMessage().getContentRaw();
                     List<ActionRow> actionRow2 = new ArrayList<>();
                    for (ActionRow row : event.getMessage().getActionRows()) {
                        List<ItemComponent> buttonRow = row.getComponents();
                        int buttonIndex = buttonRow.indexOf(event.getButton());
                        if (buttonIndex > -1) {
                            buttonRow.remove(buttonIndex);
                        }
                        if (buttonRow.size() > 0) {
                            actionRow2.add(ActionRow.of(buttonRow));
                        }
                    }
                    if (!exhaustedMessage.contains("Click the names")) {
                        exhaustedMessage = exhaustedMessage + ", "+msg;
                    } else {
                        exhaustedMessage = ident + msg;
                    }
                    event.getMessage().editMessage(exhaustedMessage).setComponents(actionRow2).queue();
                }
                case "passForRound" -> {
                    player.setPassed(true);
                    String text = Helper.getPlayerRepresentation(player, activeMap) + " PASSED";
                    MessageHelper.sendMessageToChannel(event.getChannel(), text);
                    Turn turn = new Turn();
                    turn.sendMessage(turn.pingNextPlayer(event, activeMap, player), event);
                }
                case "proceedToVoting" -> {
                    MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Decided to skip waiting for afters and proceed to voting.");
                     try {
                         AgendaHelper.startTheVoting(activeMap, event);
                        } catch (Exception e) {
                            BotLogger.log(event, "Could not start the voting", e);
                        }
                    

                    //event.getMessage().delete().queue();
                }
                case "drawAgenda_2" -> {
                    new DrawAgenda().drawAgenda(event, 2, activeMap, player);
                    event.getMessage().delete().queue();
                }
                case "diploRefresh2" -> {
                    player.addFollowedSC(2);
                    addReaction(event, false, false, "", "");
                    String message = trueIdentity + " Click the names of the planets you wish to ready";

                    List<Button> buttons = Helper.getPlanetRefreshButtons(event, player, activeMap);
                    Button DoneRefreshing = Button.danger("deleteButtons_diplomacy", "Done Readying Planets");
                    buttons.add(DoneRefreshing);
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                (MessageChannel) player.getCardsInfoThread(activeMap), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "leadershipExhaust" -> {
                    addReaction(event, false, false, "", "");
                    String message = trueIdentity + " Click the names of the planets you wish to exhaust.";

                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeMap, player, event);
                    Button DoneExhausting = Button.danger("deleteButtons_leadership", "Done Exhausting Planets");
                    buttons.add(DoneExhausting);
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(activeMap), message,
                                buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "endOfTurnAbilities" -> {
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to do an end of turn ability", ButtonHelper.getEndOfTurnAbilities(player, activeMap));

                }
                case "redistributeCCButtons" -> {

                    String message = trueIdentity + "! Your current CCs are " + Helper.getPlayerCCs(player)
                            + ". Use buttons to gain CCs";

                    Button getTactic = Button.success(finsFactionCheckerPrefix + "increase_tactic_cc",
                            "Gain 1 Tactic CC");
                    Button getFleet = Button.success(finsFactionCheckerPrefix + "increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat = Button.success(finsFactionCheckerPrefix + "increase_strategy_cc",
                            "Gain 1 Strategy CC");
                    Button loseTactic = Button.danger(finsFactionCheckerPrefix + "decrease_tactic_cc",
                            "Lose 1 Tactic CC");
                    Button loseFleet = Button.danger(finsFactionCheckerPrefix + "decrease_fleet_cc", "Lose 1 Fleet CC");
                    Button loseStrat = Button.danger(finsFactionCheckerPrefix + "decrease_strategy_cc",
                            "Lose 1 Strategy CC");

                    Button DoneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons",
                            "Done Redistributing CCs");
                    List<Button> buttons = List.of(getTactic, getFleet, getStrat, loseTactic, loseFleet, loseStrat,
                            DoneGainingCC);
                    if (!activeMap.isFoWMode()) {

                        MessageHelper.sendMessageToChannelWithButtons(
                                (MessageChannel) player.getCardsInfoThread(activeMap), message, buttons);

                        // MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message,
                        // buttons);

                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }

                case "leadershipGenerateCCButtons" -> {
                    player.addFollowedSC(1);
                    addReaction(event, false, false, "", "");
                    String message = trueIdentity + "! Your current CCs are " + Helper.getPlayerCCs(player)
                            + ". Use buttons to gain CCs";

                    Button getTactic = Button.success(finsFactionCheckerPrefix + "increase_tactic_cc",
                            "Gain 1 Tactic CC");
                    Button getFleet = Button.success(finsFactionCheckerPrefix + "increase_fleet_cc", "Gain 1 Fleet CC");
                    Button getStrat = Button.success(finsFactionCheckerPrefix + "increase_strategy_cc",
                            "Gain 1 Strategy CC");
                    // Button exhaust = Button.danger(finsFactionCheckerPrefix +
                    // "leadershipExhaust", "Exhaust Planets");
                    Button DoneGainingCC = Button.danger(finsFactionCheckerPrefix + "deleteButtons_leadership",
                            "Done Gaining CCs");
                    List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                (MessageChannel) player.getCardsInfoThread(activeMap), message, buttons);
                    } else {
                        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
                    }
                }
                case "diploSystem" -> {
                    String message = trueIdentity + " Click the name of the planet who's system you wish to diplo";

                    List<Button> buttons = Helper.getPlanetSystemDiploButtons(event, player, activeMap);
                    if (!activeMap.isFoWMode()) {
                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                        if (threadChannels == null)
                            return;
                        String threadName = activeMap.getName() + "-round-" + activeMap.getRound() + "-diplomacy";
                        // SEARCH FOR EXISTING OPEN THREAD
                        for (ThreadChannel threadChannel_ : threadChannels) {
                            if (threadChannel_.getName().equals(threadName)) {
                                MessageHelper.sendMessageToChannelWithButtons((MessageChannel) threadChannel_, message,
                                        buttons);
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
                    String message = hasSchemingAbility
                            ? "Drew 3 Actions Cards (Scheming) - please discard an Action Card from your hand"
                            : "Drew 2 Actions cards";
                    int count = hasSchemingAbility ? 3 : 2;
                    for (int i = 0; i < count; i++) {
                        activeMap.drawActionCard(player.getUserID());
                    }
                    ACInfo.sendActionCardInfo(activeMap, player, event);
                     ButtonHelper.checkACLimit(activeMap, event, player);
                    addReaction(event, false, false, message, "");
                }
                case "sc_draw_so" -> {
                    boolean used = addUsedSCPlayer(messageID + "so", activeMap, player, event,
                            " Drew a " + Emojis.SecretObjective);
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
                    activeMap.setHackElectionStatus(false);
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Set Order Back To Normal.");
                    event.getMessage().delete().queue();
                }
                case "proceed_to_strategy" -> {
                    LinkedHashMap<String, Player> players = activeMap.getPlayers();
                    for (Player player_ : players.values()) {
                        player_.cleanExhaustedPlanets(false);
                    }
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda cleanup run!");
                    ButtonHelper.startStrategyPhase(event, activeMap);
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
                    ButtonHelper.resolveMinisterOfCommerceCheck(activeMap, player, event);
                }
                case "sc_refresh_and_wash" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "Replenish and Wash");
                    if (used) {
                        break;
                    }
                    int commoditiesTotal = player.getCommoditiesTotal();
                    int tg = player.getTg();
                    player.setTg(tg + commoditiesTotal);
                    ButtonHelper.pillageCheck(player, activeMap);
                    player.setCommodities(0);
                    player.addFollowedSC(5);
                    addReaction(event, false, false, "Replenishing and washing", "");
                    ButtonHelper.resolveMinisterOfCommerceCheck(activeMap, player, event);
                }
                case "sc_follow" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    String message = deductCC(player, event);

                    ButtonHelper.resolveMuaatCommanderCheck(player, activeMap, event);
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
                    if(player.getLeaderIDs().contains("hacancommander") && !player.hasLeaderUnlocked("hacancommander")){
                        ButtonHelper.commanderUnlockCheck(player, activeMap, "hacan", event);
                    }
                    ButtonHelper.pillageCheck(player, activeMap);
                    player.setCommodities(player.getCommoditiesTotal());
                    addReaction(event, false, false, " gained 3" + Emojis.tg + " and replenished commodities ("
                            + String.valueOf(player.getCommodities()) + Emojis.comm + ")", "");
                    ButtonHelper.resolveMinisterOfCommerceCheck(activeMap, player, event);
                }
                case "score_imperial" -> {
                    if (player == null || activeMap == null) {
                        break;
                    }
                    if (!player.getSCs().contains(8)) {
                        MessageHelper.sendMessageToChannel(privateChannel, "Only the player who has "
                                + Helper.getSCBackRepresentation(event, 8) + " can score the Imperial point");
                        break;
                    }
                    boolean used = addUsedSCPlayer(messageID + "score_imperial", activeMap, player, event,
                            " scored Imperial");
                    if (used) {
                        break;
                    }
                    ScorePublic.scorePO(event, privateChannel, activeMap, player, 0);
                }
                // AFTER AN AGENDA HAS BEEN REVEALED
                case "play_when" -> {
                    clearAllReactions(event);
                    addReaction(event, true, true, "Playing When", "When Played");
                    List<Button> whenButtons = AgendaHelper.getWhenButtons(activeMap);
                    Date newTime = new Date();
                    activeMap.setLastActivePlayerPing(newTime);
                    MessageHelper.sendMessageToChannelWithPersistentReacts(actionsChannel,
                            "Please indicate no whens again.", activeMap, whenButtons, "when");
                    // addPersistentReactions(event, activeMap, "when");
                    event.getMessage().delete().queue();
                }
                case "no_when" -> {
                    String message = activeMap.isFoWMode() ? "No whens" : null;
                    addReaction(event, false, false, message, "");
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
                case "deal2SOToAll" -> {
                    new DealSOToAll().dealSOToAll(event, 2, activeMap);
                    event.getMessage().delete().queue();

                }
                case "startOfGameObjReveal" -> {
                     Player speaker = null;
                    if (activeMap.getPlayer(activeMap.getSpeaker()) != null) {
                        speaker = activeMap.getPlayers().get(activeMap.getSpeaker());
                    } 
                    for(Player p :activeMap.getRealPlayers()){
                        if(p.getSecrets().size() > 1){
                             MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please ensure everyone has discarded secrets before hitting this button. ");
                            return;
                        }
                    }
                    if(speaker == null){
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please assign speaker before hitting this button (command is /player stats speaker:y)");
                        return;
                    }
                    new RevealStage1().revealS1(event, activeMap.getMainGameChannel());
                    new RevealStage1().revealS1(event, activeMap.getMainGameChannel());
                    ButtonHelper.startStrategyPhase(event, activeMap);
                    event.getMessage().delete().queue();
                }
                case "gain_2_comms" -> {
                    String message = "";
                    if (player.getCommodities() + 2 > player.getCommoditiesTotal()) {
                        player.setCommodities(player.getCommoditiesTotal());
                        message = "Gained Commodities to Max";
                    } else {
                        player.setCommodities(player.getCommodities() + 2);
                        message = "Gained 2 Commodities";

                    }
                    addReaction(event, false, false, message, "");
                    event.getMessage().delete().queue();
                    if (!activeMap.isFoWMode() && (event.getChannel() != activeMap.getActionsChannel())) {
                        String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                        MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                    }

                }
                case "convert_2_comms" -> {
                    String message = "";
                    if (player.getCommodities() > 1) {
                        player.setCommodities(player.getCommodities() - 2);
                        player.setTg(player.getTg() + 2);
                        message = "Converted 2 Commodities to 2 tg";

                    } else {
                        player.setTg(player.getTg() + player.getCommodities());
                        player.setCommodities(0);
                        message = "Converted all remaining commodies (less than 2) into tg";
                    }
                    addReaction(event, false, false, message, "");

                    event.getMessage().delete().queue();
                    if (!activeMap.isFoWMode() && (event.getChannel() != activeMap.getActionsChannel())) {
                        String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                        MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                    }
                }
                case "gain_1_comms" -> {
                    String message = "";
                    if (player.getCommodities() + 1 > player.getCommoditiesTotal()) {
                        player.setCommodities(player.getCommoditiesTotal());
                        message = "Gained No Commodities (at max already)";
                    } else {
                        player.setCommodities(player.getCommodities() + 1);
                        message = "Gained 1 Commodity";
                    }
                    addReaction(event, false, false, message, "");
                    event.getMessage().delete().queue();
                    if (!activeMap.isFoWMode() && (event.getChannel() != activeMap.getActionsChannel())) {
                        String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                        MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                    }
                }
                case "comm_for_AC" -> {
                    boolean hasSchemingAbility = player.hasAbility("scheming");
                    int count2 = hasSchemingAbility ? 2 : 1;
                    String commOrTg = "";
                    if (player.getCommodities() > 0) {
                        commOrTg = "commodity";
                        player.setCommodities(player.getCommodities() - 1);

                    } else if (player.getTg() > 0) {
                        player.setTg(player.getTg() - 1);
                        commOrTg = "trade good";
                    } else {
                        addReaction(event, false, false, "Didn't have any comms/tg to spend, no AC drawn", "");
                        break;
                    }
                    for (int i = 0; i < count2; i++) {
                        activeMap.drawActionCard(player.getUserID());  
                    }
                     ButtonHelper.checkACLimit(activeMap, event, player);
                    if(player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")){
                            ButtonHelper.commanderUnlockCheck(player, activeMap, "yssaril", event);
                        }
                    ACInfo.sendActionCardInfo(activeMap, player, event);
                    String message = hasSchemingAbility
                            ? "Spent 1 " + commOrTg + " to draw " + count2
                                    + " Action Card (Scheming) - please discard an Action Card from your hand"
                            : "Spent 1 " + commOrTg + " to draw " + count2 + " AC";
                    addReaction(event, false, false, message, "");
                    event.getMessage().delete().queue();
                    if (!activeMap.isFoWMode() && (event.getChannel() != activeMap.getActionsChannel())) {
                        String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                        MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                    }
                }
                case "comm_for_mech" -> {
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ") + 1, labelP.length());
                    String commOrTg = "";
                    if (player.getCommodities() > 0) {
                        player.setCommodities(player.getCommodities() - 1);
                        commOrTg = "commodity";
                    } else if (player.getTg() > 0) {
                        player.setTg(player.getTg() - 1);
                        commOrTg = "tg";
                    } else {
                        addReaction(event, false, false, "Didn't have any comms/tg to spend, no mech placed", "");
                        break;
                    }
                    new AddUnits().unitParsing(event, player.getColor(),
                            activeMap.getTile(AliasHandler.resolveTile(planetName)), "mech " + planetName,
                            activeMap);
                    addReaction(event, false, false, "Spent 1 " + commOrTg + " for a mech on " + planetName, "");
                    event.getMessage().delete().queue();
                    if (!activeMap.isFoWMode() && (event.getChannel() != activeMap.getActionsChannel())) {
                        String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                        MessageHelper.sendMessageToChannel(actionsChannel,
                                pF + " Spent 1 " + commOrTg + " for a mech on " + planetName);
                    }
                }
                case "increase_strategy_cc" -> {
                    String originalCCs = Helper.getPlayerCCs(player);
                    player.setStrategicCC(player.getStrategicCC() + 1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace(
                                "Your current CCs are " + originalCCs + ". Use buttons to gain CCs",
                                "CCs have gone from " + originalCCs + " -> " + Helper.getPlayerCCs(player)
                                        + ". Net gain of: 1");
                    } else {
                        String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "), editedMessage.length());
                        shortCCs = shortCCs.replace("CCs have gone from ", "");
                        shortCCs = shortCCs.substring(0,shortCCs.indexOf(" "));
                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                                + Helper.getPlayerCCs(player) + ". Net gain of: " + netGain;
                    }
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "increase_tactic_cc" -> {
                    String originalCCs = Helper.getPlayerCCs(player);
                    player.setTacticalCC(player.getTacticalCC() + 1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace(
                                "Your current CCs are " + originalCCs + ". Use buttons to gain CCs",
                                "CCs have gone from " + originalCCs + " -> " + Helper.getPlayerCCs(player)
                                        + ". Net gain of: 1");
                    } else {
                        
                        String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "), editedMessage.length());
                        shortCCs = shortCCs.replace("CCs have gone from ", "");
                        shortCCs = shortCCs.substring(0,shortCCs.indexOf(" "));
                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                                + Helper.getPlayerCCs(player) + ". Net gain of: " + netGain;
                    }
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "increase_fleet_cc" -> {
                    String originalCCs = Helper.getPlayerCCs(player);
                    player.setFleetCC(player.getFleetCC() + 1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace(
                                "Your current CCs are " + originalCCs + ". Use buttons to gain CCs",
                                "CCs have gone from " + originalCCs + " -> " + Helper.getPlayerCCs(player)
                                        + ". Net gain of: 1");
                    } else {
                        String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "), editedMessage.length());
                        shortCCs = shortCCs.replace("CCs have gone from ", "");
                        shortCCs = shortCCs.substring(0,shortCCs.indexOf(" "));
                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                                + Helper.getPlayerCCs(player) + ". Net gain of: " + netGain;
                    }
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_strategy_cc" -> {
                    String originalCCs = Helper.getPlayerCCs(player);
                    player.setStrategicCC(player.getStrategicCC() - 1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace(
                                "Your current CCs are " + originalCCs + ". Use buttons to gain CCs",
                                "CCs have gone from " + originalCCs + " -> " + Helper.getPlayerCCs(player)
                                        + ". Net gain of: -1");
                    } else {
                        String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "), editedMessage.length());
                        shortCCs = shortCCs.replace("CCs have gone from ", "");
                        shortCCs = shortCCs.substring(0,shortCCs.indexOf(" "));
                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                                + Helper.getPlayerCCs(player) + ". Net gain of: " + netGain;
                    }
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_tactic_cc" -> {
                    String originalCCs = Helper.getPlayerCCs(player);
                    player.setTacticalCC(player.getTacticalCC() - 1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace(
                                "Your current CCs are " + originalCCs + ". Use buttons to gain CCs",
                                "CCs have gone from " + originalCCs + " -> " + Helper.getPlayerCCs(player)
                                        + ". Net gain of: -1");
                    } else {
                        String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "), editedMessage.length());
                        shortCCs = shortCCs.replace("CCs have gone from ", "");
                        shortCCs = shortCCs.substring(0,shortCCs.indexOf(" "));
                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                                + Helper.getPlayerCCs(player) + ". Net gain of: " + netGain;
                    }
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "decrease_fleet_cc" -> {
                    String originalCCs = Helper.getPlayerCCs(player);
                    player.setFleetCC(player.getFleetCC() - 1);
                    String editedMessage = event.getMessage().getContentRaw();
                    if (editedMessage.contains("Use buttons to gain CCs")) {
                        editedMessage = editedMessage.replace(
                                "Your current CCs are " + originalCCs + ". Use buttons to gain CCs",
                                "CCs have gone from " + originalCCs + " -> " + Helper.getPlayerCCs(player)
                                        + ". Net gain of: -1");
                    } else {
                        String shortCCs = editedMessage.substring(editedMessage.indexOf("CCs have gone from "), editedMessage.length());
                        shortCCs = shortCCs.replace("CCs have gone from ", "");
                        shortCCs = shortCCs.substring(0,shortCCs.indexOf(" "));
                        int netGain = ButtonHelper.checkNetGain(player, shortCCs);
                        editedMessage = editedMessage.substring(0, editedMessage.indexOf("->") + 3)
                                + Helper.getPlayerCCs(player) + ". Net gain of: " + netGain;
                    }
                    event.getMessage().editMessage(editedMessage).queue();
                }
                case "gain_1_tg" -> {
                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ") + 1, labelP.length());
                    boolean failed = false;
                    if (labelP.contains("Inf") && labelP.contains("Mech")) {
                        message = message + mechOrInfCheck(planetName, activeMap, player);
                        failed = message.contains("Please try again.");
                    }
                    if (!failed) {
                        message = message + "Gained 1 tg (" + player.getTg() + "->" + (player.getTg() + 1) + ").";
                        player.setTg(player.getTg() + 1);
                    }
                    addReaction(event, false, false, message, "");
                    if (!failed) {
                        event.getMessage().delete().queue();
                        if (!activeMap.isFoWMode() && (event.getChannel() != activeMap.getActionsChannel())) {
                            String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                            MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                        }
                    }
                }
                case "mallice_2_tg" -> {
                    String playerRep = Helper.getFactionIconFromDiscord(player.getFaction());
                    String message = playerRep + " exhausted Mallice ability and gained 2 tg (" + player.getTg() + "->"
                            + (player.getTg() + 2) + ").";
                    player.setTg(player.getTg() + 2);
                    ButtonHelper.pillageCheck(player, activeMap);
                    if(player.getLeaderIDs().contains("hacancommander") && !player.hasLeaderUnlocked("hacancommander")){
                        ButtonHelper.commanderUnlockCheck(player, activeMap, "hacan", event);
                    }
                    ButtonHelper.pillageCheck(player, activeMap);
                    if (!activeMap.isFoWMode() && event.getMessageChannel() != activeMap.getMainGameChannel()) {
                        MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message);
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    event.getMessage().delete().queue();
                }
                case "mallice_convert_comm" -> {

                    String playerRep = Helper.getFactionIconFromDiscord(player.getFaction());

                    String message = playerRep + " exhausted Mallice ability and converted comms to tg (TGs: "
                            + player.getTg() + "->" + (player.getTg() + player.getCommodities()) + ").";
                    player.setTg(player.getTg() + player.getCommodities());
                    player.setCommodities(0);
                    if (!activeMap.isFoWMode() && event.getMessageChannel() != activeMap.getMainGameChannel()) {
                        MessageHelper.sendMessageToChannel(activeMap.getMainGameChannel(), message);
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
                    event.getMessage().delete().queue();
                }
                case "decline_explore" -> {
                    addReaction(event, false, false, "Declined Explore", "");
                    event.getMessage().delete().queue();
                    if (!activeMap.isFoWMode() && (event.getChannel() != activeMap.getActionsChannel())) {
                        String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                        MessageHelper.sendMessageToChannel(actionsChannel, pF + " declined explore");
                    }
                }
                case "confirm_cc" -> {
                    if (player.getMahactCC().size() > 0) {
                        addReaction(event, true, false,
                                "Confirmed CCs: " + player.getTacticalCC() + "/" + player.getFleetCC() + "(+"
                                        + player.getMahactCC().size() + ")/" + player.getStrategicCC(),
                                "");
                    } else {
                        addReaction(event, true, false, "Confirmed CCs: " + player.getTacticalCC() + "/"
                                + player.getFleetCC() + "/" + player.getStrategicCC(), "");
                    }
                }
                case "draw_1_AC" -> {
                    activeMap.drawActionCard(player.getUserID());
                    ACInfo.sendActionCardInfo(activeMap, player, event);
                    if(player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")){
                        ButtonHelper.commanderUnlockCheck(player, activeMap, "yssaril", event);
                    }
                    addReaction(event, true, false, "Drew 1 AC", "");
                     ButtonHelper.checkACLimit(activeMap, event, player);
                }
                case "draw_1_ACDelete" -> {
                    activeMap.drawActionCard(player.getUserID());
                    if(player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")){
                        ButtonHelper.commanderUnlockCheck(player, activeMap, "yssaril", event);
                    }
                    ACInfo.sendActionCardInfo(activeMap, player, event);
                    addReaction(event, true, false, "Drew 1 AC", "");
                    event.getMessage().delete().queue();
                    ButtonHelper.checkACLimit(activeMap, event, player);
                }
                case "draw_2_AC" -> {
                    activeMap.drawActionCard(player.getUserID());
                    activeMap.drawActionCard(player.getUserID());
                    if(player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")){
                        ButtonHelper.commanderUnlockCheck(player, activeMap, "yssaril", event);
                    }
                    ACInfo.sendActionCardInfo(activeMap, player, event);
                    addReaction(event, true, false, "Drew 2 AC", "");
                     ButtonHelper.checkACLimit(activeMap, event, player);
                }
                case "pass_on_abilities" -> {
                    addReaction(event, false, false, " Is " + event.getButton().getLabel(), "");
                }
                case "tacticalAction" -> {
                    String message = "Doing a tactical action. Please select the ring of the map that the system you want to activate is located in. Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Rex.";
                    List<Button> ringButtons = ButtonHelper.getPossibleRings(player, activeMap);
                    activeMap.resetCurrentMovedUnitsFrom1TacticalAction();
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
                }
                case "ChooseDifferentDestination" -> {
                    String message = "Choosing a different system to activate. Please select the ring of the map that the system you want to activate is located in. Reminder that a normal 6 player map is 3 rings, with ring 1 being adjacent to Rex.";
                    List<Button> ringButtons = ButtonHelper.getPossibleRings(player, activeMap);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, ringButtons);
                    event.getMessage().delete().queue();
                }
                case "componentAction" -> {
                    String message = "Use Buttons to decide what kind of component action you want to do";
                    List<Button> systemButtons = ButtonHelper.getAllPossibleCompButtons(activeMap, player, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);

                }
                case "drawRelicFromFrag" -> {
                    MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Drew Relic");
                    DrawRelic.drawRelicAndNotify(player, event, activeMap);
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeMap, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                }
                case "finishComponentAction" -> {
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeMap, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                }

                case "doneWithTacticalAction" -> {
                    ButtonHelper.exploreDET(player, activeMap, event);

                   
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeMap, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                    ButtonHelper.updateMap(activeMap, event);

                }
                case "doAnotherAction" -> {
                    String message = "Use buttons to end turn or do another action.";
                    List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeMap, true, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                }
                case "concludeMove" -> {

                    String message = "Moved all units to the space area.";
                    Tile tile = activeMap.getTileByPosition(activeMap.getActiveSystem());
                    List<Button> systemButtons = null;
                    if (activeMap.getMovedUnitsFromCurrentActivation().isEmpty()) {
                        message = "Nothing moved. Use buttons to decide if you want to build (if you can) or finish the activation";
                        systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeMap, event);
                        systemButtons = ButtonHelper.landAndGetBuildButtons(player, activeMap, event);
                    } else {
                        systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, activeMap, event);
                        List<Player> players = ButtonHelper.getOtherPlayersWithShipsInTheSystem(player, activeMap, tile);
                        if(players.size() > 0){
                            Player player2 = players.get(0);
                            String messageCombat = "Resolve space combat.";
                            if(!activeMap.isFoWMode() && !activeMap.isAllianceMode()){
                                MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent(messageCombat);
                                TextChannel textChannel = (TextChannel)mainGameChannel;
                                String threadName =  activeMap.getName() + "-round-" + activeMap.getRound() + "-system-" + tile.getPosition()+"-"+player.getFaction()+"-vs-"+player2.getFaction();
                                Player p1 = player;
                                mainGameChannel.sendMessage(baseMessageObject.build()).queue(message_ -> {
                                    ThreadChannelAction threadChannel = textChannel.createThreadChannel(threadName, message_.getId());
                                    threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
                                    threadChannel.queue(m5 -> {
                                        List<ThreadChannel> threadChannels = activeMap.getActionsChannel().getThreadChannels();
                                        if (threadChannels != null) {
                                            for (ThreadChannel threadChannel_ : threadChannels) {
                                                if (threadChannel_.getName().equals(threadName)) {
                                                    MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, Helper.getPlayerRepresentation(p1, activeMap, activeMap.getGuild(), true) + Helper.getPlayerRepresentation(player2, activeMap, activeMap.getGuild(), true) + " Please resolve the interaction here. The first step is any pds fire or playing of experimental battle station. Then the playing of any start of combat or start of a combat round abilities (includes skilled retreat). Then the rolling of anti-fighter-barrage. Then the declaration of retreats (includes the playing of rout). Then the rolling of dice. None of this is automated yet.");
                                                    List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(p1, activeMap, tile.getPosition());
                                                    int context = 0;
                                                    if(playersWithPds2.size()> 0){
                                                        context =1;
                                                    }
                                                    File systemWithContext = GenerateTile.getInstance().saveImage(activeMap, context, tile.getPosition(), event);
                                                    MessageHelper.sendMessageWithFile((MessageChannel) threadChannel_, systemWithContext, "Picture of system", false);
                                                    if (playersWithPds2.size() > 0) {
                                                        String pdsMessage = "The following players have pds2 cover in the region:";
                                                        for(Player playerWithPds : playersWithPds2){
                                                            pdsMessage = pdsMessage + " "+Helper.getPlayerRepresentation(playerWithPds, activeMap, activeMap.getGuild(), false);
                                                        }
                                                        MessageHelper.sendMessageToChannel((MessageChannel) threadChannel_, pdsMessage);
                                                    }
                                                }
                                            }
                                        }
                                    });
                                });
                            }
                            else{
                                message = message + " Make sure to resolve space combat";
                            }
                        }
                        if(systemButtons.size() > 1){
                            message = message + " Use the buttons to land troops.";
                        }
                    }
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                    ButtonHelper.updateMap(activeMap, event);

                }
                 case "doneRemoving" -> {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
                    event.getMessage().delete().queue();
                    ButtonHelper.updateMap(activeMap, event);
                 }
                case "doneLanding" -> {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
                    String message = "Landed troops. Use buttons to decide if you want to build or finish the activation";
                    List<Button> systemButtons = ButtonHelper.landAndGetBuildButtons(player, activeMap, event);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
                    event.getMessage().delete().queue();
                }
                case "vote" -> {
                    String pfaction2 = null;
                    if (player != null) {
                        pfaction2 = player.getFaction();
                    }
                    if (pfaction2 != null) {
                        String voteMessage = "Chose to Vote. Click buttons for which outcome to vote for.";
                        String agendaDetails = activeMap.getCurrentAgendaInfo();
                        agendaDetails = agendaDetails.substring(agendaDetails.indexOf("_") + 1,
                                agendaDetails.lastIndexOf("_"));
                        List<Button> outcomeActionRow = null;
                        outcomeActionRow = AgendaHelper.getAgendaButtons(null, activeMap, "outcome");
                        if (agendaDetails.contains("For") || agendaDetails.contains("for")) {
                            outcomeActionRow = AgendaHelper.getForAgainstOutcomeButtons(null, "outcome");
                        } else if (agendaDetails.contains("Player") || agendaDetails.contains("player")) {
                            outcomeActionRow = AgendaHelper.getPlayerOutcomeButtons(activeMap, null, "outcome", null);
                        } else if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
                            voteMessage = "Chose to Vote. Too many planets in the game to represent all as buttons. Click buttons for which player owns the planet you wish to elect.";
                            outcomeActionRow = AgendaHelper.getPlayerOutcomeButtons(activeMap, null, "planetOutcomes",
                                    null);
                        } else if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
                            outcomeActionRow = AgendaHelper.getSecretOutcomeButtons(activeMap, null, "outcome");
                        } else if (agendaDetails.contains("Strategy") || agendaDetails.contains("strategy")) {
                            outcomeActionRow = AgendaHelper.getStrategyOutcomeButtons(null, "outcome");
                        } else {
                            outcomeActionRow = AgendaHelper.getLawOutcomeButtons(activeMap, null, "outcome");
                        }
                        event.getMessage().delete().queue();
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage,
                                outcomeActionRow);
                    }
                }
                case "planet_ready" -> {
                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ") + 1, labelP.length());
                    boolean failed = false;
                    if (labelP.contains("Inf") && labelP.contains("Mech")) {
                        message = message + mechOrInfCheck(planetName, activeMap, player);
                        failed = message.contains("Please try again.");
                    }

                    if (!failed) {
                        new PlanetRefresh().doAction(player, planetName, activeMap);
                        message = message + "Readied " + planetName;
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
                case "quash" -> {
                    int stratCC = player.getStrategicCC();
                    player.setStrategicCC(stratCC-1);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Quashed agenda. Strategic CCs went from "+stratCC+ " -> "+(stratCC-1));
                    new RevealAgenda().revealAgenda(event, false, activeMap, activeMap.getMainGameChannel());
                    event.getMessage().delete().queue();
                }
                case "scoreAnObjective" -> {
                    List<Button> poButtons = new Turn().getScoreObjectiveButtons(event, activeMap);
                    poButtons.add(Button.danger("deleteButtons", "Delete These Buttons"));
                    MessageChannel channel = event.getMessageChannel();
                    if(activeMap.isFoWMode()){
                        channel = player.getPrivateChannel();
                    }
                    MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to score an objective", poButtons);
                }
                case "startChaosMapping" -> {
                    ButtonHelper.firstStepOfChaos(activeMap, player, event);
                }
                case "orbitolDropFollowUp" -> {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
                    List<Button> startButtons = new ArrayList<>();
                    Button tacticalAction = Button.success("dropAMechToo", "Spend 3 resource to Drop a Mech Too");
                    startButtons.add(tacticalAction);
                    Button componentAction = Button.danger("finishComponentAction", "Decline Mech");
                    startButtons.add(componentAction);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Decide whether to drop mech",
                            startButtons);
                    event.getMessage().delete().queue();
                }
                case "dropAMechToo" -> {
                    String message = "Please select the same planet you dropped the infantry on";
                    List<Button> buttons = Helper.getPlanetPlaceUnitButtons(player, activeMap, "mech", "place");
                    buttons.add(Button.danger("orbitolDropExhaust", "Pay for mech"));
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
                    event.getMessage().delete().queue();
                }
                case "orbitolDropExhaust" -> {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeMap, player, event);
                    Button DoneExhausting = Button.danger("finishComponentAction", "Done Exhausting Planets");
                    buttons.add(DoneExhausting);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                            "Use Buttons to Pay For The Mech", buttons);
                    event.getMessage().delete().queue();
                }
                case "eraseMyVote" -> {
                    String pfaction = player.getFaction();
                    if (activeMap.isFoWMode()) {
                        pfaction = player.getColor();
                    }
                    AgendaHelper.eraseVotesOfFaction(activeMap, pfaction);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                            "Erased previous votes made by " + Helper.getFactionIconFromDiscord(player.getFaction())
                                    + "\n \n" + AgendaHelper.getSummaryOfVotes(activeMap, true));
                    Button Vote = Button.success("vote",
                            StringUtils.capitalize(player.getFaction()) + " Choose To Vote");
                    Button Abstain = Button.danger("delete_buttons_0",
                            StringUtils.capitalize(player.getFaction()) + " Choose To Abstain");

                    List<Button> buttons = List.of(Vote, Abstain);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                            "Use buttons to vote again. Reminder that this erasing of old votes did not refresh any planets.",
                            buttons);
                    event.getMessage().delete().queue();

                }
                case "gain_CC" -> {
                    String message = "";
                    String labelP = event.getButton().getLabel();
                    String planetName = labelP.substring(labelP.lastIndexOf(" ") + 1, labelP.length());
                    boolean failed = false;
                    if (labelP.contains("Inf") && labelP.contains("Mech")) {
                        message = message + mechOrInfCheck(planetName, activeMap, player);
                        failed = message.contains("Please try again.");
                    }

                    if (!failed) {

                        String message2 = trueIdentity + "! Your current CCs are " + Helper.getPlayerCCs(player)
                                + ". Use buttons to gain CCs";
                        Button getTactic = Button.success("increase_tactic_cc", "Gain 1 Tactic CC");
                        Button getFleet = Button.success("increase_fleet_cc", "Gain 1 Fleet CC");
                        Button getStrat = Button.success("increase_strategy_cc", "Gain 1 Strategy CC");
                        Button DoneGainingCC = Button.danger("deleteButtons_explore", "Done Gaining CCs");
                        List<Button> buttons = List.of(getTactic, getFleet, getStrat, DoneGainingCC);
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);

                    }

                    addReaction(event, false, false, message, "");
                    if (!failed && !event.getMessage().getContentRaw().contains("fragment")) {
                        event.getMessage().delete().queue();
                        if (!activeMap.isFoWMode() && (event.getChannel() != activeMap.getActionsChannel())) {
                            String pF = Helper.getFactionIconFromDiscord(player.getFaction());
                            MessageHelper.sendMessageToChannel(actionsChannel, pF + " " + message);
                        }
                    }
                }
                case "run_status_cleanup" -> {
                    new Cleanup().runStatusCleanup(activeMap);
                    List<ActionRow> actionRow2 = new ArrayList<>();
                    for (ActionRow row : event.getMessage().getActionRows()) {
                        List<ItemComponent> buttonRow = row.getComponents();
                        int buttonIndex = buttonRow.indexOf(event.getButton());
                        if (buttonIndex > -1) {
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
        } catch (Exception e) {
            BotLogger.log(event, "Something went wrong with a button press", e);
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
            if (unitHolder.getUnits().get(infKey) != null) {
                numInf = unitHolder.getUnits().get(infKey);
            }
        }
        if (numMechs > 0 || numInf > 0) {
            if (numMechs > 0) {
                message = "Planet had a mech. ";
            } else {
                message = "Planet did not have a mech. Removed 1 infantry (" + numInf + "->" + (numInf - 1) + "). ";
                tile.removeUnit(planetName, infKey, 1);
            }
        } else {
            message = "Planet did not have a mech or infantry. Please try again.";
        }
        return message;
    }

    private boolean addUsedSCPlayer(String messageID, Map activeMap, Player player,
            @NotNull ButtonInteractionEvent event, String defaultText) {
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
        // messageRestAction.queue(m -> {
        // RestAction<Void> voidRestAction = m.clearReactions();
        // voidRestAction.queue();
        // });
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

    private void addReaction(@NotNull ButtonInteractionEvent event, boolean skipReaction, boolean sendPublic,
            String message, String additionalMessage) {
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
            if (event.getMessageChannel() instanceof ThreadChannel) {
                
                activeMap.getActionsChannel().addReactionById(event.getChannel().getId(), emojiToUse).queue();
            }

            event.getChannel().addReactionById(messageId, emojiToUse).queue();
            checkForAllReactions(event, activeMap);
            if (message == null || message.isEmpty())
                return;
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

        Message mainMessage = event.getMessageChannel().retrieveMessageById(messageId).completeAfter(500,
                TimeUnit.MILLISECONDS);

        int matchingFactionReactions = 0;
        for (Player player : activeMap.getPlayers().values()) {
            if (!player.isRealPlayer()) {
                matchingFactionReactions++;
                continue;
            }

            String faction = player.getFaction();
            if (faction == null || faction.isEmpty() || faction.equals("null")){
                matchingFactionReactions++;
                continue;
            }

            Emoji reactionEmoji = Emoji.fromFormatted(Helper.getFactionIconFromDiscord(faction));
            if (activeMap.isFoWMode()) {
                int index = 0;
                for (Player player_ : activeMap.getPlayers().values()) {
                    if (player_ == player)
                        break;
                    index++;
                }
                reactionEmoji = Emoji.fromFormatted(Helper.getRandomizedEmoji(index, event.getMessageId()));
            }
            MessageReaction reaction = mainMessage.getReaction(reactionEmoji);
            if (reaction != null)
                matchingFactionReactions++;
        }
        int numberOfPlayers = activeMap.getPlayers().size();
        if (matchingFactionReactions >= numberOfPlayers) {
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
            case Constants.SC_FOLLOW, "sc_no_follow", "sc_refresh", "sc_refresh_and_wash", "trade_primary",
                    "sc_ac_draw", "sc_draw_so", "sc_trade_follow", "sc_leadership_follow" -> {
                if (activeMap.isFoWMode()) {
                    event.getInteraction().getMessage().reply("All players have reacted to this Strategy Card")
                            .queueAfter(1, TimeUnit.SECONDS);
                } else {
                    GuildMessageChannel guildMessageChannel = Helper.getThreadChannelIfExists(event);
                    guildMessageChannel.sendMessage("All players have reacted to this Strategy Card").queueAfter(10,
                            TimeUnit.SECONDS);
                    if (guildMessageChannel instanceof ThreadChannel)
                        ((ThreadChannel) guildMessageChannel).getManager().setArchived(true).queueAfter(5,
                                TimeUnit.MINUTES);
                }
            }
            case "no_when", "no_when_persistent" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Whens'").queueAfter(1,
                        TimeUnit.SECONDS);
            }
            case "no_after", "no_after_persistent" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Afters'").queue();
                AgendaHelper.startTheVoting(activeMap, event);
                event.getMessage().delete().queue();

            }
            case "no_sabotage" -> {
                event.getInteraction().getMessage().reply("All players have indicated 'No Sabotage'").queueAfter(1,
                        TimeUnit.SECONDS);
            }

            case Constants.PO_SCORING, Constants.PO_NO_SCORING -> {
                String message2 = "All players have indicated scoring. Flip the relevant PO using the buttons. This will automatically run status clean-up if it has not been run already.";
                Button drawStage2 = Button.success("reveal_stage_2", "Reveal Stage 2");
                Button drawStage1 = Button.success("reveal_stage_1", "Reveal Stage 1");
                // Button runStatusCleanup = Button.primary("run_status_cleanup", "Run Status
                // Cleanup");
                List<Button> buttons = List.of(drawStage1, drawStage2);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, buttons);
                event.getMessage().delete().queueAfter(20, TimeUnit.SECONDS);
            }
            case "pass_on_abilities" -> {
                if (activeMap.isCustodiansScored()) {
                    // new RevealAgenda().revealAgenda(event, false, map, event.getChannel());
                    Button flipAgenda = Button.primary("flip_agenda", "Press this to flip agenda");
                    List<Button> buttons = List.of(flipAgenda);
                    MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Please flip agenda now",
                            buttons);
                } else {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), Helper.getGamePing(event.getGuild(),
                            activeMap)
                            + " All players have indicated completion of status phase. Proceed to Strategy Phase.");
                    Player speaker = null;
                    if (activeMap.getPlayer(activeMap.getSpeaker()) != null) {
                        speaker = activeMap.getPlayers().get(activeMap.getSpeaker());
                    } else {
                        speaker = null;
                    }
                    String message = Helper.getPlayerRepresentation(speaker, activeMap, event.getGuild(), true)
                            + " UP TO PICK SC\n";
                    activeMap.updateActivePlayer(speaker);
                    activeMap.setCurrentPhase("strategy");
                    ButtonHelper.giveKeleresCommsNTg(activeMap, event);
                    if (activeMap.isFoWMode()) {
                        // MessageHelper.sendPrivateMessageToPlayer(speaker, activeMap, message);
                        if (!activeMap.isHomeBrewSCMode()) {
                            MessageHelper.sendMessageToChannelWithButtons(speaker.getPrivateChannel(),
                                    message + "Use Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeMap));
                        } else {
                            MessageHelper.sendPrivateMessageToPlayer(speaker, activeMap, message);
                        }
                    } else {
                        // MessageHelper.sendMessageToChannel(event.getChannel(), message);
                        if (!activeMap.isHomeBrewSCMode()) {
                            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                                    message + "Use Buttons to Pick SC", Helper.getRemainingSCButtons(event, activeMap));
                        } else {
                            MessageHelper.sendMessageToChannel(event.getChannel(), message);
                        }

                    }
                }

            }
        }
    }
}