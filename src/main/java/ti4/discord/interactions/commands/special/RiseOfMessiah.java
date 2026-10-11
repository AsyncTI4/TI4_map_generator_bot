package ti4.discord.interactions.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;

class RiseOfMessiah extends GameStateSubcommand {

    RiseOfMessiah() {
        super(Constants.RISE_OF_A_MESSIAH, "RiseOfMessiah +1 Inf to each planet", true, true);
        markSuspicious();
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(CommonOptions.factionColorForStats());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ActionCardHelper.doRise(getPlayer(), event, getGame());
    }
}
