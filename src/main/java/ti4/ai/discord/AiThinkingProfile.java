package ti4.ai.discord;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.runtime.AiRuntime;
import ti4.ai.trace.ThinkingStats;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.message.MessageHelper;
import ti4.service.testbed.TestBedService;

class AiThinkingProfile extends GameStateSubcommand {

    static final int MAX_LENGTH = 1900;
    static final int SPAN_ROWS = 8;
    static final int SLOW_ROWS = 3;

    AiThinkingProfile() {
        super("profile", "Show where the AI players' thinking time has gone in this game", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!AiCommandSupport.mayManage(event, game)) {
            MessageHelper.replyToMessage(event, AiCommandSupport.notAllowed());
            return;
        }
        boolean developer = TestBedService.isDeveloper(event.getMember());
        MessageHelper.replyToMessage(event, describe(game, developer));
    }

    static String describe(Game game, boolean developer) {
        if (game.isFowMode() && !developer) return "AI thinking profiles are hidden in Fog of War games.";
        return AiRuntime.thinkingStats(game.getName())
                .filter(stats -> stats.decisions() > 0)
                .map(AiThinkingProfile::render)
                .orElse("The AI players have not made a decision in this game since the bot started.");
    }

    static String render(ThinkingStats stats) {
        List<String> lines = stats.lines(SPAN_ROWS, SLOW_ROWS);
        return StringUtils.abbreviate("- " + String.join("\n- ", lines), MAX_LENGTH);
    }
}
