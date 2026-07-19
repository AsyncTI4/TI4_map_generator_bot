package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.ActionCardStatsService;

class ActionCardStats extends Subcommand {

    ActionCardStats() {
        super(Constants.ACTION_CARD_STATS, "Action card play statistics");
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.AC_DECK,
                        "Action card deck games must use (default: Prophecy of Kings deck)")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ActionCardStatsService.queueReply(event);
    }
}
