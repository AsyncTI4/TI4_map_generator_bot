package ti4.discord.interactions.commands.testbed;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.testbed.TestBedResetService;
import ti4.service.testbed.TestBedResetService.ResetResult;
import ti4.service.testbed.TestBedService;

class TestBedReset extends GameStateSubcommand {

    private static final String CONFIRM = "confirm";

    TestBedReset() {
        super("reset", "Remove virtual seats and their channels, unseat you and clear the map", true, false);
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
        Player nonDeveloper = TestBedService.findNonDeveloper(event.getGuild(), game.getRealPlayers());
        if (nonDeveloper != null) {
            MessageHelper.replyToMessage(
                    event, "Refused: " + nonDeveloper.getUserName() + " is seated and is not a developer.");
            return;
        }
        if (!event.getOption(CONFIRM, false, option -> option.getAsBoolean())) {
            MessageHelper.replyToMessage(event, "Nothing reset. Run again with `confirm:True` to reset.");
            return;
        }
        ResetResult result = TestBedResetService.reset(game);
        String summary = "Test bed reset: removed " + result.removedSeats() + " virtual seats, unseated "
                + result.resetSeats() + " developer seats, cleared the map and restored the action card, secret"
                + " objective and relic decks. Run `/testbed apply` to start again.";
        if (result.missingChannels() > 0) {
            summary += "\n" + result.missingChannels() + " test bed channels were already gone.";
        }
        MessageHelper.replyToMessage(event, summary);
    }
}
