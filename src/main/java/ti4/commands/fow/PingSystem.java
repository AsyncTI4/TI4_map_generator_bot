package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;

class PingSystem extends GameStateSubcommand {

    public PingSystem() {
        super(Constants.PING_SYSTEM, "Alert players adjacent to a system with a message.", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.MESSAGE, "Message to send").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String position = event.getOption(Constants.POSITION).getAsString().toLowerCase();
        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.replyToMessage(event, "Tile position is not allowed");
            return;
        }

        FoWHelper.pingSystem(
                getGame(), position, event.getOption(Constants.MESSAGE).getAsString(), false);
    }
}
