package ti4.commands2.uncategorized;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.generator.MapRenderPipeline;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.ShowGameHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ShowGame extends ti4.commands2.GameStateCommand {

    public ShowGame() {
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
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DisplayType displayType = null;
        OptionMapping statsOption = event.getOption(Constants.DISPLAY_TYPE);
        if (statsOption != null) {
            String temp = statsOption.getAsString();
            if (temp.equals(DisplayType.split.getValue())) {
                displayType = DisplayType.map;
                MapRenderPipeline.render(game, event, displayType,
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
        ShowGameHelper.simpleShowGame(game, event, displayType);
    }

    @ButtonHandler("showGameAgain")
    public static void simpleShowGame(Game game, GenericInteractionCreateEvent event) {
        ShowGameHelper.simpleShowGame(game, event, DisplayType.all);
    }

    @ButtonHandler("showMap")
    public static void showMap(Game game, ButtonInteractionEvent event) {
        MapRenderPipeline.render(game, event, DisplayType.map, fileUpload -> MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event));
    }

    @ButtonHandler("showPlayerAreas")
    public static void showPlayArea(Game game, ButtonInteractionEvent event) {
        MapRenderPipeline.render(game, event, DisplayType.stats, fileUpload -> MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event));
    }

    @Override
    public void register(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getName(), "Shows selected map")
                .addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Map name to be shown").setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.DISPLAY_TYPE, "Show map in specific format. all, map, stats").setAutoComplete(true)));
    }
}
