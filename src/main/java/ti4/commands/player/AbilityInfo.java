package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.info.AbilityInfoService;

class AbilityInfo extends GameStateSubcommand {

    public AbilityInfo() {
        super(Constants.ABILITY_INFO, "Send faction abilities information to your #cards-info thread", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        AbilityInfoService.sendAbilityInfo(getGame(), getPlayer(), event);
    }


}
