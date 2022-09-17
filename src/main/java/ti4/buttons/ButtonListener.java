package ti4.buttons;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;

import java.util.HashMap;
import java.util.List;

public class ButtonListener extends ListenerAdapter {

    private static HashMap<Guild, HashMap<String, Emote>> emoteMap = new HashMap<>();

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        event.deferEdit().queue();
        String buttonID = event.getButton().getId();
        if (buttonID == null){
            return;
        }
        switch (buttonID) {
            case "sabotage" -> addReactionForSabo(event, true);
            case "no_sabotage" -> addReactionForSabo(event, false);
            default -> event.getHook().sendMessage("Button " + buttonID + " pressed.").queue();
        }
    }

    private void addReactionForSabo(@NotNull ButtonInteractionEvent event, boolean sabotage) {
        String id = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(id);
        Player player = Helper.getGamePlayer(activeMap, null, event.getMember(), id);
        if (player == null) {
            event.getChannel().sendMessage("Your not a player of the game").queue();
            return;
        }
        String playerFaction = player.getFaction();
        Guild guild = event.getGuild();
        if (guild == null){
            event.getChannel().sendMessage("Could not find server Emojis").queue();
            return;
        }
        HashMap<String, Emote> emojiMap = emoteMap.get(guild);
        List<Emote> emotes = guild.getEmotes();
        if (emojiMap != null && emojiMap.size() != emotes.size()){
            emojiMap.clear();
        }
        if (emojiMap == null || emojiMap.isEmpty()){
            emojiMap = new HashMap<>();
            for (Emote emote : emotes) {
                emojiMap.put(emote.getName().toLowerCase(), emote);
            }
        }
        Emote emoteToUse = emojiMap.get(playerFaction.toLowerCase());

        if (!sabotage) {
            if (emoteToUse == null) {
                event.reply("Could not find faction (" + playerFaction + ") symbol for reaction").queue();
                return;
            }
            event.getChannel().addReactionById(event.getInteraction().getMessage().getId(), emoteToUse).queue();
        }
        if (sabotage) {
            String text = Helper.getFactionIconFromDiscord(playerFaction) + " Sabotaging Action Card Play";
            event.getChannel().sendMessage(text).queue();
            event.getChannel().sendMessage(Helper.getGamePing(event.getGuild(), activeMap) + " Sabotage played").queue();
        } else {
            String text = Helper.getFactionIconFromDiscord(playerFaction) + " No Sabotage";
            event.getChannel().sendMessage(text).queue();
        }
    }
}
