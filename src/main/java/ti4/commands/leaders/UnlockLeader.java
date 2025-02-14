package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.leader.UnlockLeaderService;

class UnlockLeader extends GameStateSubcommand {

    public UnlockLeader() {
        super(Constants.UNLOCK_LEADER, "Unlock leader", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader for which to do action")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String leaderID = event.getOption(Constants.LEADER, null, OptionMapping::getAsString);
        UnlockLeaderService.unlockLeader(leaderID, getGame(), getPlayer());
    }
}
