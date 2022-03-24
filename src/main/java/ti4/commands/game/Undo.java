package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class Undo extends GameSubcommandData{
    public static final String NEW_LINE = "\n";

    public Undo() {
        super(Constants.INFO, "Undo last action");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm undo command with YES"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MapManager mapManager = MapManager.getInstance();
        if (mapManager.getUserActiveMap(event.getUser().getId()) == null){
            MessageHelper.replyToMessage(event, "Must set active Game");
        }
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())){
            MessageHelper.replyToMessage(event, "Must confirm with YES");
        }

        Storage.

        MessageHelper.replyToMessage(event, sb.toString());
    }

}
