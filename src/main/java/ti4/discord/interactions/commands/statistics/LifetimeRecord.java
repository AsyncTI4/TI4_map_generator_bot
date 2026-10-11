package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.spring.service.statistics.LifeTimeRecordService;

class LifetimeRecord extends Subcommand {

    LifetimeRecord() {
        super(Constants.LIFETIME_RECORD, "Dice luck for all games of specific players");
        addOptions(CommonOptions.playerOptions(true, i -> "Player" + i));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        LifeTimeRecordService.getBean().getLifeTimeRecords(event);
    }
}
