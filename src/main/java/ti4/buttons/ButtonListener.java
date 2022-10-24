package ti4.buttons;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import ti4.MessageListener;
import ti4.commands.cards.CardsInfo;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ButtonListener extends ListenerAdapter {

    public static HashMap<Guild, HashMap<String, Emote>> emoteMap = new HashMap<>();

    private static HashMap<String, Set<Player>> playerUsedSC = new HashMap<>();

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        event.deferEdit().queue();
        MessageListener.setActiveGame(event.getMessageChannel(), event.getUser().getId(), "button");
        String buttonID = event.getButton().getId();
        if (buttonID == null) {
            event.getChannel().sendMessage("Button command not found").queue();
            return;
        }
        String messageID = event.getMessage().getId();
        String id = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(id);
        Player player = Helper.getGamePlayer(activeMap, null, event.getMember(), id);
        if (player == null) {
            event.getChannel().sendMessage("You're not a player of the game").queue();
            return;
        }
        switch (buttonID) {
            case "sabotage" -> addReaction(event, true, "Sabotaging Action Card Play", " Sabotage played");
            case "no_sabotage" -> addReaction(event, false, "No Sabotage", "");
            case "sc_follow" -> {
                boolean used = addUsedSCPlayer(messageID, player, event, "");
                if (used){
                    break;
                }
                String message = deductCC(player, event);
                addReaction(event, true, message, "");
            }
            case "sc_ac_draw" -> {
                boolean used = addUsedSCPlayer(messageID+"ac", player, event, "");
                if (used){
                    break;
                }
                String message =  "Drew 2 Actions cards";
                int count = player.getFaction().equals("yssaril") ? 3 : 2;
                for (int i = 0; i < count; i++) {
                    activeMap.drawActionCard(player.getUserID());
                }
                CardsInfo.sentUserCardInfo(null, activeMap, player, event);
                addReaction(event, true, message, "");
            } case "sc_draw_so" -> {
                boolean used = addUsedSCPlayer(messageID+"so", player, event, "");
                if (used){
                    break;
                }
                String message = "Drew Secret Objective";
                activeMap.drawSecretObjective(player.getUserID());
                CardsInfo.sentUserCardInfo(null, activeMap, player, event);
                addReaction(event, true, message, "");
            }
            case "sc_follow_trade" -> {
                boolean used = addUsedSCPlayer(messageID, player, event, "");
                if (used){
                    break;
                }
                String message = deductCC(player, event);
                player.setCommodities(player.getCommoditiesTotal());
                addReaction(event, true, message, "");
                addReaction(event, true, "Replenishing Commodities", "");
            }
            case "sc_follow_leadership" -> {
                String message = Helper.getPlayerPing(event, player) + " following.";
                addReaction(event, true, message, "");
            }
            case "sc_no_follow" -> { addReaction(event, false, "Not Following", "");
                Set<Player> players = playerUsedSC.get(messageID);
                if (players == null){
                    players = new HashSet<>();
                }
                players.remove(player);
                playerUsedSC.put(messageID, players);
            }
            case "play_when" -> {
                clearAllReactions(event);
                addReaction(event, true, "Playing When", "When Played");
            }
            case "no_when" -> addReaction(event, false, "No Whens", "");
            case "play_after" -> {
                clearAllReactions(event);
                addReaction(event, true, "Playing After", "After Played");
            }
            case "no_after" -> addReaction(event, false, "No Afters", "");
            case "sc_refresh" -> {
                boolean used = addUsedSCPlayer(messageID, player, event, "Replenish");
                if (used){
                    break;
                }
                player.setCommodities(player.getCommoditiesTotal());
                addReaction(event, true, "Replenishing Commodities", "");
            }
            case "sc_refresh_and_wash" -> {
                boolean used = addUsedSCPlayer(messageID, player, event, "Replenish and Wash");
                if (used){
                    break;
                }
                int commoditiesTotal = player.getCommoditiesTotal();
                int tg = player.getTg();
                player.setTg(tg + commoditiesTotal);
                player.setCommodities(0);
                addReaction(event, true, "Replenishing and washing", "");
            }
            default -> event.getHook().sendMessage("Button " + buttonID + " pressed.").queue();
        }
    }

    private boolean addUsedSCPlayer(String messageID, Player player, @NotNull ButtonInteractionEvent event, String text) {
        Set<Player> players = playerUsedSC.get(messageID);
        if (players == null){
            players = new HashSet<>();
        }
        boolean contains = players.contains(player);
        players.add(player);
        playerUsedSC.put(messageID, players);
        if (contains){
            String defaultText = text.isEmpty() ? "Secondary of Strategy Card" : text;
            event.getChannel().sendMessage("Player: " + Helper.getPlayerPing(event, player) + " already used " + defaultText).queue();
        }
        return contains;
    }

    @NotNull
    private String deductCC(Player player, @NotNull ButtonInteractionEvent event) {
        int strategicCC = player.getStrategicCC();
        String message;
        if (strategicCC == 0){
            message = "Have 0 CC in Strategy, can't follow";
        } else {
            strategicCC--;
            player.setStrategicCC(strategicCC);
            message = Helper.getPlayerPing(event, player) + " following SC, deducted 1 CC from Strategy Tokens";
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

    private void addReaction(@NotNull ButtonInteractionEvent event, boolean skipReaction, String message, String additionalMessage) {
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
        HashMap<String, Emote> emojiMap = emoteMap.get(guild);
        List<Emote> emotes = guild.getEmotes();
        if (emojiMap != null && emojiMap.size() != emotes.size()) {
            emojiMap.clear();
        }
        if (emojiMap == null || emojiMap.isEmpty()) {
            emojiMap = new HashMap<>();
            for (Emote emote : emotes) {
                emojiMap.put(emote.getName().toLowerCase(), emote);
            }
        }
        Emote emoteToUse = emojiMap.get(playerFaction.toLowerCase());
        Message mainMessage = event.getInteraction().getMessage();
        String messageId = mainMessage.getId();

//        RestAction<Message> messageRestAction = event.getChannel().retrieveMessageById(messageId);
//        messageRestAction.queue(m -> {
//            List<MessageReaction> reactions = m.getReactions();
//            // rest of your code in here (you cannot have it outside the { } scope
//            // ...
//        });
        if (!skipReaction) {
            if (emoteToUse == null) {
                event.getChannel().sendMessage("Could not find faction (" + playerFaction + ") symbol for reaction").queue();
                return;
            }
            event.getChannel().addReactionById(messageId, emoteToUse).queue();
            return;
        }
        boolean foundThread = false;
        String text = Helper.getFactionIconFromDiscord(playerFaction) + " " + message;
        if (!additionalMessage.isEmpty()) {
            text += Helper.getGamePing(event.getGuild(), activeMap) + " " + additionalMessage;
        }
        List<ThreadChannel> threadChannels = guild.getThreadChannels();
        for (ThreadChannel threadChannel : threadChannels) {
            if (threadChannel.getId().equals(messageId)) {
                threadChannel.sendMessage(text).queue();
                foundThread = true;
                break;
            }
        }

        if (!foundThread){
            event.getChannel().sendMessage(text).queue();
        }
    }
}
