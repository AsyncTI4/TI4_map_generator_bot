package ti4.commands2.uncategorized;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateCommand;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.image.MapRenderPipeline;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;

public class ShowGameCommand extends GameStateCommand {

    public ShowGameCommand() {
        super(false, false);
    }

    @Override
    public String getName() {
        return Constants.SHOW_GAME;
    }

    @Override
    public String getDescription() {
        return "Show selected map";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
            new OptionData(OptionType.STRING, Constants.GAME_NAME, "Map name to be shown")
                .setAutoComplete(true),
            new OptionData(OptionType.STRING, Constants.DISPLAY_TYPE, "Show map in specific format. all, map, stats")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DisplayType displayType = null;
        OptionMapping statsOption = event.getOption(Constants.DISPLAY_TYPE);
        if (statsOption != null) {
            String temp = statsOption.getAsString();
            if (temp.equals(DisplayType.split.getValue())) {
                displayType = DisplayType.map;
                MapRenderPipeline.queue(game, event, displayType,
                                fileUpload -> MessageHelper.sendFileUploadToChannel(event.getChannel(), fileUpload));
                displayType = DisplayType.stats;
            } else {
                for (DisplayType i : DisplayType.values()) {
                    if (temp.equals(i.getValue())) {
                        displayType = i;
                        break;
                    }
                }
            }
        }
        if (displayType == null) {
            displayType = DisplayType.all;
        }
        ShowGameService.simpleShowGame(game, event, displayType);
    }
}
