package ti4.discord.interactions.commands.uncategorized;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateCommand;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.fow.WhisperService;

public class RemindMeCommand extends GameStateCommand {

    public RemindMeCommand() {
        super(true, true);
    }

    @Override
    public String getName() {
        return Constants.REMIND_ME;
    }

    @Override
    public String getDescription() {
        return "Send a reminder message to yourself at start of your next turn";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(new OptionData(OptionType.STRING, Constants.MESSAGE, "Message to send").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        if (!player.isRealPlayer()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "This command only works on real players.");
            return;
        }

        String message = event.getOption(Constants.MESSAGE, "", OptionMapping::getAsString);
        WhisperService.whisperToFutureMe(getGame(), player, message, event.getChannel());
    }
}
