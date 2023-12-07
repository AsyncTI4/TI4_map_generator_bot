package ti4.selections;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.helpers.Constants;
import ti4.helpers.FrankenDraftHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

import java.util.Date;

public class SelectionMenuListener extends ListenerAdapter {
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!AsyncTI4DiscordBot.readyToReceiveCommands) {
            event.reply("Please try again in a moment. The bot is rebooting.").setEphemeral(true).queue();
            return;
        }

        try {
            SelectionMenuProvider.resolveSelectionMenu(event);
        } catch (Exception e) {
            String message = "Selection Menu issue in event: " + event.getComponentId() + "\n> Channel: " + event.getChannel().getAsMention() + "\n> Command: " + event.getValues();
            BotLogger.log(message, e);
        }
    }
}
