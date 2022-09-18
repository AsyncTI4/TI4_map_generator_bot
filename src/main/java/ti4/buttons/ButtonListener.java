package ti4.buttons;

import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ti4.MessageListener;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class ButtonListener extends ListenerAdapter {

    private static HashMap<Guild, HashMap<String, Emote>> emoteMap = new HashMap<>();

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
            case "sabotage" -> addReactionForSabo(event, true, "Sabotaging Action Card Play", " Sabotage played");
            case "no_sabotage" -> addReactionForSabo(event, false, "No Sabotage", "");
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
                addReactionForSabo(event, true, message, "");
            } case "sc_follow_leadership" -> {
                String message = Helper.getPlayerPing(event, player) + " following SC.";
                addReactionForSabo(event, true, message, "");
            }
            case "sc_no_follow" -> addReactionForSabo(event, false, "Not Following SC", "");
            default -> event.getHook().sendMessage("Button " + buttonID + " pressed.").queue();
        }
    }

    private void addReactionForSabo(@NotNull ButtonInteractionEvent event, boolean skipReaction, String message, String additionalMessage) {
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
        String messageId = event.getInteraction().getMessage().getId();
        if (!skipReaction) {
            if (emoteToUse == null) {
                event.getChannel().sendMessage("Could not find faction (" + playerFaction + ") symbol for reaction").queue();
                return;
            }
            event.getChannel().addReactionById(messageId, emoteToUse).queue();
        }
        if (skipReaction) {
            String text = Helper.getFactionIconFromDiscord(playerFaction) + " " + message;
            event.getChannel().sendMessage(text).queue();
            if (!additionalMessage.isEmpty()) {
                event.getChannel().sendMessage(Helper.getGamePing(event.getGuild(), activeMap) + " " + additionalMessage).queue();
            }
        } else {
            String text = Helper.getFactionIconFromDiscord(playerFaction) + " " + message;
            event.getChannel().sendMessage(text).queue();
            List<ThreadChannel> threadChannels = guild.getThreadChannels();
            for (ThreadChannel threadChannel : threadChannels) {
                if (threadChannel.getId().equals(messageId)){
                    threadChannel.sendMessage(text).queue();
                }
            }
        }
    }
}
