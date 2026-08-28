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
                OptionType.BOOLEAN,
                ActionCardStatsService.FULL_DETAILS_OPTION,
                "'true' to add the raw win, play and cancel counts behind each Impact Score"));
        // Read as proportions, so 2/1/1 weighs the same as the 0.5/0.25/0.25 default. Pass one and
        // the other two keep their defaults, which is rarely what you want - pass all three.
        addOptions(
                impactWeightOption(ActionCardStatsService.WIN_WEIGHT_OPTION, "win rate", "0.5"),
                impactWeightOption(ActionCardStatsService.PLAY_WEIGHT_OPTION, "play rate", "0.25"),
                impactWeightOption(ActionCardStatsService.CANCEL_WEIGHT_OPTION, "cancel rate", "0.25"));
    }

    private static OptionData impactWeightOption(String name, String figure, String defaultWeight) {
        return new OptionData(
                        OptionType.NUMBER,
                        name,
                        "Impact Score weight for " + figure + ", relative to the other two (default " + defaultWeight
                                + ")")
                .setMinValue(0);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ActionCardStatsService.queueReply(event);
    }
}
