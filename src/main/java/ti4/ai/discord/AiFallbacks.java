package ti4.ai.discord;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ai.fallback.FallbackLog;
import ti4.ai.fallback.FallbackRecord;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.testbed.TestBedService;

class AiFallbacks extends GameStateSubcommand {

    AiFallbacks() {
        super("fallbacks", "Show the last times an AI was unsure, made no progress or timed out", false, false);
        addOptions(AiCommandSupport.seatOption("Only this AI seat (default: all of them)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!AiCommandSupport.mayManage(event, game)) {
            MessageHelper.replyToMessage(event, AiCommandSupport.notAllowed());
            return;
        }
        List<Player> seats = AiCommandSupport.targetSeats(event, game);
        if (seats.isEmpty()) {
            MessageHelper.replyToMessage(event, AiCommandSupport.noSeat(event));
            return;
        }
        List<String> factions = seats.stream().map(Player::getFaction).toList();
        boolean developer = TestBedService.isDeveloper(event.getMember());
        boolean openGame = !game.isFowMode();
        List<String> lines = new ArrayList<>();
        for (FallbackRecord record : FallbackLog.recent(game)) {
            if (factions.contains(record.faction()))
                lines.addAll(describe(record, developer || openGame && !record.privatePrompt()));
        }
        MessageHelper.replyToMessage(
                event, lines.isEmpty() ? "No AI fallback has been recorded in this game." : String.join("\n", lines));
    }

    private static List<String> describe(FallbackRecord record, boolean detailed) {
        List<String> lines = new ArrayList<>();
        lines.add("- Round " + record.round() + ", " + record.phase() + ", **" + record.faction() + "** "
                + record.kind() + ": " + record.reason() + " -> " + record.outcome());
        if (!detailed) return lines;
        lines.add("  - prompt: " + record.promptText() + " " + record.buttonIds());
        lines.add("  - last decisions: " + record.recentDecisions());
        if (!record.pendingDecision().isBlank()) lines.add("  - about to: " + record.pendingDecision());
        return lines;
    }
}
