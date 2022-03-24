package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class Undo extends GameSubcommandData{
    public Undo() {
        super(Constants.UNDO, "Undo last action");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm undo command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MapManager mapManager = MapManager.getInstance();
        Map userActiveMap = mapManager.getUserActiveMap(event.getUser().getId());
        if (userActiveMap == null){
            MessageHelper.replyToMessage(event, "Must set active Game");
            return;
        }
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())){
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }

        MapSaveLoadManager.undo(userActiveMap);
        userActiveMap = MapManager.getInstance().getMap(userActiveMap.getName());
        File file = GenerateMap.getInstance().saveImage(userActiveMap);
        MessageHelper.replyToMessage(event, file);
    }
}
