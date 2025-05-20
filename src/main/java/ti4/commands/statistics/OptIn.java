package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.service.statistics.StatisticsOptInOutService;

class OptIn extends Subcommand {

    public OptIn() {
        super("opt_in", "Opt in or out of other players being able to view your individual stats");
        addOptions(
            new OptionData(OptionType.BOOLEAN, "win_rates", "True or false, if you want win related stats").setRequired(true),
            new OptionData(OptionType.BOOLEAN, "turns", "True or false if you want turn related stats").setRequired(true),
            new OptionData(OptionType.BOOLEAN, "combats", "True or false if you want combat related stats").setRequired(true),
            new OptionData(OptionType.BOOLEAN, "victory_points", "True or false if you want victory point related stats").setRequired(true),
            new OptionData(OptionType.BOOLEAN, "factions", "True or false if you want faction related stats").setRequired(true),
            new OptionData(OptionType.BOOLEAN, "opponents", "True or false if you want opponent related stats").setRequired(true),
            new OptionData(OptionType.BOOLEAN, "games", "True or false if you want game related stats").setRequired(true)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        StatisticsOptInOutService.optIn(event);
    }
}
