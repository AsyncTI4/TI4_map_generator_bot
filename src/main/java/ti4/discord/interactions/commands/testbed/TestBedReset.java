package ti4.discord.interactions.commands.testbed;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.message.MessageHelper;
import ti4.service.testbed.TestBedPress;
import ti4.service.testbed.TestBedResetService;
import ti4.service.testbed.TestBedResetService.ResetResult;
import ti4.service.testbed.TestBedService;

class TestBedReset extends GameStateSubcommand {

    private static final String CONFIRM = "confirm";

    TestBedReset() {
        super("reset", "Remove virtual seats and their channels, unseat you and clear the map", false, false);
        addOptions(new OptionData(OptionType.BOOLEAN, CONFIRM, "Deletes the test bed's channels; cannot be undone")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!TestBedService.isTestBed(game)) {
            MessageHelper.replyToMessage(event, "This game is not a test bed; nothing to reset.");
            return;
        }
        String refusal = TestBedService.destructiveCommandRefusal(event.getGuild(), game, game.getRealPlayers());
        if (refusal != null) {
            MessageHelper.replyToMessage(event, refusal);
            return;
        }
        if (!event.getOption(CONFIRM, false, option -> option.getAsBoolean())) {
            MessageHelper.replyToMessage(event, "Nothing reset. Run again with `confirm:True` to reset.");
            return;
        }
        ExecutorServiceManager.runAsync("test bed reset " + game.getName(), () -> resetExclusively(game, event));
    }

    private static void resetExclusively(Game game, SlashCommandInteractionEvent event) {
        ResetResult[] result = new ResetResult[1];
        boolean done = TestBedPress.runLocked(game, false, locked -> {
            result[0] = TestBedResetService.reset(locked);
            if (!result[0].fromSnapshot()) GameManager.save(locked, "Test bed reset");
        });
        if (!done) {
            MessageHelper.replyToMessage(event, "The game is no longer loaded; nothing reset.");
            return;
        }
        MessageHelper.replyToMessage(event, summary(result[0]));
    }

    private static String summary(ResetResult result) {
        String restored = result.fromSnapshot()
                ? "restored the game exactly as it was before `/testbed apply`"
                : "cleared the map, played strategy cards and the action card, secret objective and relic decks";
        String summary = "Test bed reset: removed " + result.removedSeats() + " virtual seats, unseated "
                + result.resetSeats() + " developer seats and " + restored + ". Run `/testbed apply` to start again.";
        if (result.missingChannels() > 0) {
            summary += "\n" + result.missingChannels() + " test bed channels were already gone.";
        }
        return summary;
    }
}
