package ti4.discord.interactions.commands.uncategorized;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateCommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.image.map.MapRenderPipeline;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;
import ti4.service.megagame.MegagameShowAsService;

public class ShowGameCommand extends GameStateCommand {

    private static final List<DisplayType> SHOW_AS_DISPLAY_TYPES =
            List.of(DisplayType.all, DisplayType.map, DisplayType.stats);

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
                new OptionData(OptionType.STRING, Constants.GAME_NAME, "Map name to be shown").setAutoComplete(true),
                new OptionData(
                                OptionType.STRING,
                                Constants.DISPLAY_TYPE,
                                "Show map in specific format. all, map, stats")
                        .setAutoComplete(true),
                new OptionData(OptionType.STRING, Constants.MAP_SEGMENT, "Fog of War: which part of the map to show")
                        .setAutoComplete(true),
                new OptionData(
                                OptionType.STRING,
                                Constants.SHOW_AS,
                                "Compact view around this faction or colour, plus their stats (only to you; not in Fog of War)")
                        .setAutoComplete(true),
                new OptionData(
                                OptionType.INTEGER,
                                Constants.SHOW_AS_DISTANCE,
                                "With show_as: also show systems up to this many steps away (default 1)")
                        .setRequiredRange(0, MegagameShowAsService.MAX_DISTANCE),
                new OptionData(
                        OptionType.BOOLEAN,
                        Constants.SHOW_AS_WHOLE_MAP,
                        "With show_as: whole map with the rest darkened instead of the compact view"));
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return event.getOption(Constants.SHOW_AS) != null
                || event.getOption(Constants.SHOW_AS_DISTANCE) != null
                || event.getOption(Constants.SHOW_AS_WHOLE_MAP) != null;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!ShowGameService.mayShowMap(game, event)) {
            return;
        }
        OptionMapping showAs = event.getOption(Constants.SHOW_AS);
        if (showAs != null) {
            showAs(event, game, showAs.getAsString());
            return;
        }
        if (isEphemeral(event)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event,
                    "`show_as_distance` and `show_as_whole_map` only work together with `show_as`."
                            + " Pick a faction or colour in `show_as`.");
            return;
        }
        String segment = event.getOption(Constants.MAP_SEGMENT, null, OptionMapping::getAsString);
        DisplayType displayType = null;
        OptionMapping statsOption = event.getOption(Constants.DISPLAY_TYPE);
        if (statsOption != null) {
            String temp = statsOption.getAsString();
            if (temp.equals(DisplayType.split.getValue())) {
                showMapHalf(game, event, segment);
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
        ShowGameService.simpleShowGame(game, event, displayType, segment);
    }

    private static void showAs(SlashCommandInteractionEvent event, Game game, String factionOrColor) {
        int distance = event.getOption(
                Constants.SHOW_AS_DISTANCE, MegagameShowAsService.DEFAULT_DISTANCE, OptionMapping::getAsInt);
        boolean wholeMap = event.getOption(Constants.SHOW_AS_WHOLE_MAP, false, OptionMapping::getAsBoolean);
        String requested =
                event.getOption(Constants.DISPLAY_TYPE, DisplayType.all.getValue(), OptionMapping::getAsString);
        DisplayType displayType = SHOW_AS_DISPLAY_TYPES.stream()
                .filter(type -> type.getValue().equals(requested))
                .findFirst()
                .orElse(null);
        if (displayType == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "With `show_as`, `display_type` can only be all, map or stats.");
            return;
        }
        MegagameShowAsService.show(event, game, factionOrColor, distance, wholeMap, displayType);
    }

    private static void showMapHalf(Game game, SlashCommandInteractionEvent event, String segment) {
        if (game.isFowMode()) {
            ShowGameService.showMapWithoutButtons(game, event, segment);
            return;
        }
        MapRenderPipeline.queue(
                game,
                event,
                DisplayType.map,
                segment,
                fileUpload -> MessageHelper.sendFileUploadToChannel(event.getChannel(), fileUpload));
    }
}
