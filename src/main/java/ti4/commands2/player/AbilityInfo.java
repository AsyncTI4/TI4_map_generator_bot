package ti4.commands2.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.info.AbilityInfoService;

class AbilityInfo extends GameStateSubcommand {

    public AbilityInfo() {
        super(Constants.ABILITY_INFO, "Send faction abilities information to your Cards Info channel", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        AbilityInfoService.sendAbilityInfo(getGame(), getPlayer(), event);
    }


}
