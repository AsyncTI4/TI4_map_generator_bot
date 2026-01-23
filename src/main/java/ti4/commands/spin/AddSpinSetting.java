package ti4.commands.spin;

import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.map.SpinService;
import ti4.service.map.SpinService.AutoTrigger;
import ti4.service.map.SpinService.Direction;
import ti4.service.map.SpinService.SpinSetting;
import ti4.service.map.SpinService.ToSpin;

class AddSpinSetting extends GameStateSubcommand {
    private static final String RING = "ring";
    private static final String DIRECTION = "direction";
    private static final String STEPS = "steps";
    private static final String AUTO_TRIGGER = "auto_trigger";
    private static final String TOSPIN = "to_spin";

    public AddSpinSetting() {
        super(Constants.SPIN_ADD, "Add a spin setting.", true, true);
        addOptions(new OptionData(OptionType.STRING, RING, "Ring # to spin (separate multiple by comma to randomize)")
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, DIRECTION, "Direction to spin (CW, CCW, RND)").setRequired(true));
        addOptions(new OptionData(
                        OptionType.STRING, STEPS, "Number of steps to spin (separate multiple by comma to randomize)")
                .setRequired(true));
        addOptions(new OptionData(
                OptionType.STRING, Constants.POSITION, "Center position from where to spin (default 000)"));
        addOptions(new OptionData(
                OptionType.STRING,
                AUTO_TRIGGER,
                "When to auto-trigger: " + String.join("|", AutoTrigger.valuesAsStringList()) + " (default: STATUS)"));
        addOptions(new OptionData(
                OptionType.STRING,
                TOSPIN,
                "What to spin: " + String.join("|", ToSpin.valuesAsStringList()) + " (default: ALL)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (game.isFowMode() && !getPlayer().isGM()) {
            MessageHelper.replyToMessage(event, "You are not authorized to use this command.");
            return;
        }

        Set<String> ringInput = Helper.getSetFromCSV(event.getOption(RING).getAsString());
        Direction direction = Direction.fromString(event.getOption(DIRECTION).getAsString());
        if (direction == null) {
            MessageHelper.replyToMessage(event, "Invalid direction");
            return;
        }
        Set<String> stepsInput = Helper.getSetFromCSV(event.getOption(STEPS).getAsString());
        String position = event.getOption(Constants.POSITION, "000", OptionMapping::getAsString);
        if (PositionMapper.getPositionsInRing("corners", game).contains(position)
                || !PositionMapper.isTilePositionValid(position)) {
            MessageHelper.replyToMessage(event, "Invalid position");
            return;
        }
        AutoTrigger trigger =
                AutoTrigger.fromString(event.getOption(AUTO_TRIGGER, "STATUS", OptionMapping::getAsString));
        if (trigger == null) {
            MessageHelper.replyToMessage(event, "Invalid trigger");
            return;
        }
        ToSpin tomove = ToSpin.fromString(event.getOption(TOSPIN, "ALL", OptionMapping::getAsString));
        if (tomove == null) {
            MessageHelper.replyToMessage(event, "Invalid option for to_spin");
            return;
        }

        List<Integer> ringNumbers = ringInput.stream().map(Integer::parseInt).toList();
        List<Integer> stepsNumbers = stepsInput.stream().map(Integer::parseInt).toList();

        SpinSetting newSetting = new SpinSetting(position, ringNumbers, direction, stepsNumbers, trigger, tomove, game);
        if (!newSetting.isValid()) {
            MessageHelper.replyToMessage(event, "Invalid spin setting parameters.");
            return;
        }

        SpinService.addSpinSetting(getGame(), newSetting);
        SpinService.listSpinSettings(event, game);
    }
}
