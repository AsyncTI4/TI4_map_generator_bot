package ti4.buttons;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import ti4.MapGenerator;
import ti4.MessageListener;
import ti4.commands.cards.CardsInfo;
import ti4.commands.cards.PlayAC;
import ti4.commands.cardsso.ScoreSO;
import ti4.commands.status.ScorePublic;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class ButtonListener extends ListenerAdapter {
    public static HashMap<Guild, HashMap<String, Emoji>> emoteMap = new HashMap<>();
    private static HashMap<String, Set<Player>> playerUsedSC = new HashMap<>();
    private static HashMap<String, HashMap<Player, Integer>> playerReacted = new HashMap<>();

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        event.deferEdit().queue();
        String id = event.getUser().getId();
        MessageListener.setActiveGame(event.getMessageChannel(), id, "button");
        String buttonID = event.getButton().getId();
        if (buttonID == null) {
            event.getChannel().sendMessage("Button command not found").queue();
            return;
        }
        String messageID = event.getMessage().getId();

        String gameName = event.getChannel().getName();
        gameName = gameName.replace(CardsInfo.CARDS_INFO, "");
        gameName = gameName.substring(0, gameName.indexOf("-"));
        Map activeMap = MapManager.getInstance().getMap(gameName);
        Player player = activeMap.getPlayer(id);
        player = Helper.getGamePlayer(activeMap, player, event.getMember(), id);
        if (player == null) {
            event.getChannel().sendMessage("You're not a player of the game").queue();
            return;
        }

        MessageChannel privateChannel = event.getChannel();
        boolean inform = true;
        if (activeMap.isFoWMode()) {
            if (player.getPrivateChannel() == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Private channels are not set up for this game. Messages will be suppressed.");
                inform = false;
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
                    PlayAC.playAC(null, activeMap, player, acID, channel, event.getGuild(), event);
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse AC ID: " + acID);
                    event.getChannel().sendMessage("Could not parse AC ID: " + acID + " Please play manually.").queue();
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
                    ScoreSO.scoreSO(null, activeMap, player, soIndex, channel, event);
                } catch (Exception e) {
                    BotLogger.log(event, "Could not parse SO ID: " + soID);
                    event.getChannel().sendMessage("Could not parse SO ID: " + soID + " Please Score manually.").queue();
                    return;
                }
            } else {
                event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            }
        } else if (buttonID.startsWith(Constants.PO_SCORING)) {
            String poID = buttonID.replace(Constants.PO_SCORING, "");
            try {
                int poIndex = Integer.parseInt(poID);
                ScorePublic.scorePO(event, privateChannel, activeMap, player, poIndex, inform);
                addReaction(event, false, false, "", "");
            } catch (Exception e) {
                BotLogger.log("Could not parse PO ID: " + poID);
                event.getChannel().sendMessage("Could not parse PO ID: " + poID + " Please Score manually.").queue();
                return;
            }
        } else {
            switch (buttonID) {
                case Constants.PO_NO_SCORING -> {
                    String message = Helper.getPlayerRepresentation(event, player) + " - no Public Objective scored.";
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                    }
                    addReaction(event, false, false, "", "");
                    
                }
                case Constants.SO_NO_SCORING -> {
                    String message = Helper.getPlayerRepresentation(event, player) + " - no Secret Objective scored.";
                    if (!activeMap.isFoWMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), message);
                    }
                    addReaction(event, false, false, "", "");
                }
                case "sabotage" -> addReaction(event, true, true, "Sabotaging Action Card Play", " Sabotage played");
                case "no_sabotage" -> addReaction(event, false, false, "No Sabotage", "");
                case "sc_follow" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    String message = deductCC(player, event);
                    addReaction(event, true, false, message, "");
                }
                case "sc_ac_draw" -> {
                    boolean used = addUsedSCPlayer(messageID + "ac", activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    boolean isYssaril = player.getFaction().equals("yssaril");
                    String message = isYssaril ? "Drew 3 Actions cards" : "Drew 2 Actions cards";
                    int count = isYssaril ? 3 : 2;
                    for (int i = 0; i < count; i++) {
                        activeMap.drawActionCard(player.getUserID());
                    }
                    CardsInfo.sentUserCardInfo(null, activeMap, player, event);
                    addReaction(event, true, false, message, "");
                }
                case "sc_draw_so" -> {
                    boolean used = addUsedSCPlayer(messageID + "so", activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    String message = "Drew Secret Objective";
                    activeMap.drawSecretObjective(player.getUserID());
                    CardsInfo.sentUserCardInfo(null, activeMap, player, event);
                    addReaction(event, true, false, message, "");
                }
                case "sc_follow_trade" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "");
                    if (used) {
                        break;
                    }
                    String message = deductCC(player, event);
                    player.setCommodities(player.getCommoditiesTotal());
                    addReaction(event, true, false, message, "");
                    addReaction(event, true, false, "Replenishing Commodities", "");
                }
                case "sc_follow_leadership" -> {
                    String message = Helper.getPlayerPing(player) + " following.";
                    addReaction(event, true, false, message, "");
                }
                case "sc_no_follow" -> {
                    addReaction(event, false, false, "Not Following", "");
                    Set<Player> players = playerUsedSC.get(messageID);
                    if (players == null) {
                        players = new HashSet<>();
                    }
                    players.remove(player);
                    playerUsedSC.put(messageID, players);
                }
                case "play_when" -> {
                    clearAllReactions(event);
                    addReaction(event, true, true, "Playing When", "When Played");
                }
                case "no_when" -> addReaction(event, false, false, "No Whens", "");
                case "play_after" -> {
                    clearAllReactions(event);
                    addReaction(event, true, true, "Playing After", "After Played");
                }
                case "no_after" -> addReaction(event, false, false, "No Afters", "");
                case "sc_refresh" -> {
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "Replenish");
                    if (used) {
                        break;
                    }
                    player.setCommodities(player.getCommoditiesTotal());
                    addReaction(event, true, false, "Replenishing Commodities", "");
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
                    addReaction(event, true, false, "Replenishing and washing", "");
                }
                case "trade_primary" -> {
                    if (5 != player.getSC()){
                        break;
                    }
                    boolean used = addUsedSCPlayer(messageID, activeMap, player, event, "Trade Primary");
                    if (used) {
                        break;
                    }
                    int tg = player.getTg();
                    player.setTg(tg + 3);
                    player.setCommodities(player.getCommoditiesTotal());
                    addReaction(event, true, false, "gained 3" + Emojis.tg + " and replenished commodities (" + String.valueOf(player.getCommodities()) + Emojis.comm + ")", "");
                }
                default -> event.getHook().sendMessage("Button " + buttonID + " pressed.").queue();
            }
        }
        MapSaveLoadManager.saveMap(activeMap);
    }

    private boolean addUsedSCPlayer(String messageID, Map map, Player player, @NotNull ButtonInteractionEvent event, String text) {
        Set<Player> players = playerUsedSC.get(messageID);
        if (players == null) {
            players = new HashSet<>();
        }
        boolean contains = players.contains(player);
        players.add(player);
        playerUsedSC.put(messageID, players);
        if (contains) {
            String defaultText = text.isEmpty() ? "Secondary of Strategy Card" : text;
            String message = "Player: " + Helper.getPlayerPing(player) + " already used " + defaultText;
            if (map.isFoWMode()) {
                MessageHelper.sendPrivateMessageToPlayer(player, map, message, null, null);
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
        String messageId = mainMessage.getId();
        RestAction<Message> messageRestAction = event.getChannel().retrieveMessageById(messageId);
        messageRestAction.queue(m -> {
            RestAction<Void> voidRestAction = m.clearReactions();
            voidRestAction.queue();
        });
    }

    private void addReaction(@NotNull ButtonInteractionEvent event, boolean skipReaction, boolean sendPublic, String message, String additionalMessage) {
        String id = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(id);
        Player player = Helper.getGamePlayer(activeMap, null, event.getMember(), id);
        if (player == null) {
            event.getChannel().sendMessage("You're not a player of the game").queue();
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
        Emoji emojiToUse = emojiMap.get(playerFaction.toLowerCase());
        Message mainMessage = event.getInteraction().getMessage();
        String messageId = mainMessage.getId();

        if (activeMap.isFoWMode()) {
            int index = 0;
            for(java.util.Map.Entry<String, Player> entry : activeMap.getPlayers().entrySet()) {
                if (entry.getValue() == player) break;
                index++;
            }
            emojiToUse = getRandomizedEmoji(index, messageId);
            if (emojiToUse == null) {
                event.getChannel().sendMessage("Could not find faction (" + playerFaction + ") symbol for reaction").queue();
            } else {
                event.getChannel().addReactionById(messageId, emojiToUse).queue();
            }
        }
        
        if (!skipReaction) {
            if (emojiToUse == null) {
                event.getChannel().sendMessage("Could not find faction (" + playerFaction + ") symbol for reaction").queue();
            } else {
                event.getChannel().addReactionById(messageId, emojiToUse).queue();
            }
            return;
        } 
        

        boolean foundThread = false;
        String text = Helper.getPlayerRepresentation(event, player) + " " + message;
        if (activeMap.isFoWMode()) {
            text = message;
        }

        if (!additionalMessage.isEmpty()) {
            text += Helper.getGamePing(event.getGuild(), activeMap) + " " + additionalMessage;
        }
        List<ThreadChannel> threadChannels = guild.getThreadChannels();
        for (ThreadChannel threadChannel : threadChannels) {
            if (threadChannel.getId().equals(messageId)) {
                if (activeMap.isFoWMode() && !sendPublic) {
                    MessageHelper.sendPrivateMessageToPlayer(player, activeMap, text, null, null);
                } else {
                    MessageHelper.sendMessageToChannel(threadChannel, text);
                }
                foundThread = true;
                break;
            }
        }

        if (!foundThread) {
            if (activeMap.isFoWMode() && !sendPublic) {
                MessageHelper.sendPrivateMessageToPlayer(player, activeMap, text, null, null);
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), text);
            }
        }
    }

    public Emoji getRandomizedEmoji(int value, String messageID) {
        List<String> symbols = new ArrayList<>(Emojis.symbols);
        Random seed = new Random(messageID.hashCode());
        Collections.shuffle(symbols, seed);
        
        String emote = symbols.get(value);
        return emote == null ? null : Emoji.fromFormatted(emote);
    }
}