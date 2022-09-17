package ti4.buttons;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;

public class ButtonListener extends ListenerAdapter {

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String buttonID = event.getButton().getId();

        switch (buttonID) {
            case "sabotage" -> addReactionForSabo(event, true);
            case "no_sabotage" -> addReactionForSabo(event, false);
            default -> event.reply("Button " + buttonID + " pressed.").queue();
        }
    }

    private void addReactionForSabo(@NotNull ButtonInteractionEvent event, boolean sabotage) {
        JDA jda = event.getJDA();
        String id = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(id);
        Player player = Helper.getGamePlayer(activeMap, null, event.getMember(), id);
        if (player == null) {
            event.reply("Your not a player of the game").queue();
            return;
        }
        String playerFaction = player.getFaction();
        Emote emoteToUse = null;
        for (Emote emote : jda.getEmotes()) {
            if (emote.getName().toLowerCase().contains(playerFaction.toLowerCase())) {
                emoteToUse = emote;
                break;
            }
        }
        if (emoteToUse == null) {
            event.reply("Could not find faction (" + playerFaction + ") symbol for reaction").queue();
            return;
        }
        event.getChannel().addReactionById(event.getInteraction().getMessage().getId(), emoteToUse).queue();
        if (sabotage) {
            String text = Helper.getGamePing(event.getGuild(), activeMap);
            text += " " + Helper.getFactionIconFromDiscord(playerFaction) + " Sabotaging Action Card Play";
            event.reply(text).queue();
        } else {
            String text = Helper.getFactionIconFromDiscord(playerFaction) + " No Sabotage";
            event.reply(text).queue();
        }
    }
}
