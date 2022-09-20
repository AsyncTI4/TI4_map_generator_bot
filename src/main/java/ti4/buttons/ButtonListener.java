package ti4.buttons;

import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ti4.MessageListener;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ButtonListener extends ListenerAdapter {

    public static HashMap<Guild, HashMap<String, Emote>> emoteMap = new HashMap<>();

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        event.deferEdit().queue();
        MessageListener.setActiveGame(event.getMessageChannel(), event.getUser().getId(), "button");
        String buttonID = event.getButton().getId();
        if (buttonID == null) {
            event.getChannel().sendMessage("Button command not found").queue();
            return;
        }

        String id = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(id);
        Player player = Helper.getGamePlayer(activeMap, null, event.getMember(), id);
        if (player == null) {
            event.getChannel().sendMessage("Your not a player of the game").queue();
            return;
        }
        switch (buttonID) {
            case "sabotage" -> addReaction(event, true, "Sabotaging Action Card Play", " Sabotage played");
            case "no_sabotage" -> addReaction(event, false, "No Sabotage", "");
            case "sc_follow" -> {
                int strategicCC = player.getStrategicCC();
                String message;
                if (strategicCC == 0){
                    message = "Have 0 CC in Strategy, can't follow";
                } else {
                    strategicCC--;
                    player.setStrategicCC(strategicCC);
                    message = Helper.getPlayerPing(event, player) + " following SC, deducted 1 CC from Strategy Tokens";
                }
                addReaction(event, true, message, "");
            } case "sc_follow_leadership" -> {
                String message = Helper.getPlayerPing(event, player) + " following.";
                addReaction(event, true, message, "");
            }
            case "sc_no_follow" -> addReaction(event, false, "Not Following", "");
            default -> event.getHook().sendMessage("Button " + buttonID + " pressed.").queue();
        }
    }

    private void addReaction(@NotNull ButtonInteractionEvent event, boolean skipReaction, String message, String additionalMessage) {
        String id = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(id);
        Player player = Helper.getGamePlayer(activeMap, null, event.getMember(), id);
        if (player == null) {
            event.getChannel().sendMessage("Your not a player of the game").queue();
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
