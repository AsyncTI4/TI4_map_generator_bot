package ti4.discord.interactions.commands.testbed;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.testbed.TestBedService;

class TestBedEnable extends GameStateSubcommand {

    private static final String ALLOW_REAL_PLAYERS = "allow_real_players";

    TestBedEnable() {
        super("enable", "Mark this game as a test bed (developers may then act as any seat)", true, false);
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                ALLOW_REAL_PLAYERS,
                "Also allow it with real players seated; every action taken as them is announced"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        boolean allowRealPlayers = event.getOption(ALLOW_REAL_PLAYERS, false, OptionMapping::getAsBoolean);
        Player nonDeveloper = TestBedService.findNonDeveloper(event.getGuild(), game.getRealPlayers());
        if (nonDeveloper != null && !allowRealPlayers) {
            MessageHelper.replyToMessage(
                    event,
                    "Refused: " + nonDeveloper.getUserName() + " is a real player without the developer role. "
                            + "Add `allow_real_players:true` to enable it anyway; the game will be told.");
            return;
        }
        TestBedService.markAsTestBed(game, true);
        if (nonDeveloper == null) {
            MessageHelper.replyToMessage(
                    event,
                    "**" + game.getName() + "** is now a test bed. Buttons pressed inside a seat's channel act as"
                            + " that seat; use `/testbed act_as` or `/testbed panel` for buttons in shared channels.");
            return;
        }
        TestBedService.allowRealPlayers(game);
        String where = game.isFowMode() ? "in the GM activity log" : "in this channel";
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "🛠️ **Developer test mode** was enabled in this game by "
                        + event.getUser().getEffectiveName()
                        + ". Developers may press buttons on behalf of seats; every such action is posted " + where
                        + ".");
        MessageHelper.replyToMessage(
                event,
                "**" + game.getName() + "** is now a test bed with real players. Act-as works for buttons, selects,"
                        + " modals and the panel only; slash commands, chat and views stay your own. `/testbed reset`,"
                        + " `apply` and `run` stay blocked here.");
    }
}
