package ti4.commands.spin;

import java.util.List;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.map.SpinService;
import ti4.service.map.SpinService.SpinSetting;

class RemoveSpinSetting extends GameStateSubcommand {
    private static final String ID = "setting_id";

    public RemoveSpinSetting() {
        super(Constants.SPIN_REMOVE, "Remove spin setting", true, true);
        addOptions(new OptionData(OptionType.STRING, ID, "Setting ID to remove or ALL").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (game.isFowMode() && !getPlayer().isGM()) {
            MessageHelper.replyToMessage(event, "You are not authorized to use this command.");
            return;
        }

        String id = event.getOption(ID, "", OptionMapping::getAsString);
        if ("ALL".equals(id)) {
            game.setSpinMode("OFF");
            MessageHelper.replyToMessage(event, "Spin settings removed.");
        } else {
            List<SpinSetting> settings = SpinService.getSpinSettings(game).stream()
                    .filter(s -> !s.id().equals(id))
                    .collect(Collectors.toList());
            SpinService.setSpinSettings(game, settings);
            SpinService.listSpinSettings(event, game);
        }
    }
}
