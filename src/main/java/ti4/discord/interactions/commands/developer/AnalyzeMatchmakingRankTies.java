package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.message.MessageHelper;
import ti4.service.statistics.game.MatchmakingRankTieAnalysis;

class AnalyzeMatchmakingRankTies extends Subcommand {

    private static final int DEFAULT_SAMPLE_SIZE = 8;
    private static final int MAX_SAMPLE_SIZE = 40;

    AnalyzeMatchmakingRankTies() {
        super("analyze_matchmaking_rank_ties", "Report why simulated matchmaking ranks still tie.");
        addOptions(
                new OptionData(OptionType.INTEGER, "sample_size", "Tied groups to print in detail - default 8", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int sampleSize =
                Math.min(MAX_SAMPLE_SIZE, event.getOption("sample_size", DEFAULT_SAMPLE_SIZE, OptionMapping::getAsInt));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "Analyzing simulated matchmaking rank ties across all games.");

        var analysis = new MatchmakingRankTieAnalysis(sampleSize);
        ConsumeGameUtility.consumeAllGames(analysis::consume, ExecutionLockType.READ);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), analysis.summary());
        for (String sample : analysis.getSamples()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), sample);
        }
    }
}
