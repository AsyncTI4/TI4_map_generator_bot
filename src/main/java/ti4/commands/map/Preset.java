package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.map.MapPresetService;

class Preset extends GameStateSubcommand {

    public Preset() {
        super(Constants.PRESET, "Create a map from a template", true, false);
        addOption(OptionType.STRING, Constants.MAP_TEMPLATE, "Which map template do you wish to use", true, true);
        addOption(OptionType.STRING, Constants.SLICE_1, "Player 1's milty draft slice", false);
        addOption(OptionType.STRING, Constants.SLICE_2, "Player 2's slice", false);
        addOption(OptionType.STRING, Constants.SLICE_3, "Player 3's slice", false);
        addOption(OptionType.STRING, Constants.SLICE_4, "Player 4's slice", false);
        addOption(OptionType.STRING, Constants.SLICE_5, "Player 5's slice", false);
        addOption(OptionType.STRING, Constants.SLICE_6, "Player 6's slice", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MapPresetService.build(event, getGame(), event.getOption(Constants.MAP_TEMPLATE).getAsString());
    }
}
