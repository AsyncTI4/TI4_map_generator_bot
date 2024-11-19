package ti4.commands2.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.info.LeaderInfoService;

class LeaderInfo extends GameStateSubcommand {

    public LeaderInfo() {
        super(Constants.INFO, "Send Leader info to your Cards-Info thread", false, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        LeaderInfoService.sendLeadersInfo(getGame(), getPlayer(), event);
    }
}
