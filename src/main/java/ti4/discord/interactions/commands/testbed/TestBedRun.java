package ti4.discord.interactions.commands.testbed;

import java.util.List;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.URLReaderHelper;
import ti4.message.MessageHelper;
import ti4.model.TestBedScript;
import ti4.service.testbed.TestBedScriptRunner;
import ti4.service.testbed.TestBedScriptService;
import ti4.service.testbed.TestBedService;
import tools.jackson.core.JacksonException;

class TestBedRun extends GameStateSubcommand {

    static final String SCRIPT = "script";
    private static final String FILE = "file";
    private static final String STOP_ON_FAIL = "stop_on_fail";

    TestBedRun() {
        super("run", "Run a test bed script: press buttons as seats and check the results", false, false);
        addOptions(
                new OptionData(OptionType.STRING, SCRIPT, "Shipped script").setAutoComplete(true),
                new OptionData(OptionType.ATTACHMENT, FILE, "Your own script .json (overrides script)"),
                new OptionData(OptionType.BOOLEAN, STOP_ON_FAIL, "Stop at the first failing step"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player nonDeveloper = TestBedService.findNonDeveloper(
                event.getGuild(), game.getPlayers().values());
        if (nonDeveloper != null) {
            MessageHelper.replyToMessage(
                    event, "Refused: " + nonDeveloper.getUserName() + " is in this game and is not a developer.");
            return;
        }
        TestBedScript script = readScript(event);
        if (script == null) return;
        List<String> errors = TestBedScriptService.validate(script);
        if (!errors.isEmpty()) {
            MessageHelper.replyToMessage(event, "The script is invalid:\n- " + String.join("\n- ", errors));
            return;
        }
        if (!TestBedService.isTestBed(game)
                && (script.getPreset() == null || !game.getRealPlayers().isEmpty())) {
            MessageHelper.replyToMessage(
                    event,
                    "This game is not a test bed. Use a script with a `preset` in a fresh game, or apply one first.");
            return;
        }
        OptionMapping stopOnFail = event.getOption(STOP_ON_FAIL);
        if (stopOnFail != null) script.setStopOnFail(stopOnFail.getAsBoolean());
        MessageHelper.replyToMessage(
                event,
                "Running script **" + (script.getName() == null ? "custom" : script.getName()) + "** ("
                        + script.getSteps().size() + " steps). The report follows in this channel.");
        TestBedScriptRunner.start(game, script, event);
    }

    @Nullable
    private static TestBedScript readScript(SlashCommandInteractionEvent event) {
        OptionMapping fileOption = event.getOption(FILE);
        if (fileOption != null) return readAttachedScript(event, fileOption.getAsAttachment());
        String name = event.getOption(SCRIPT, null, OptionMapping::getAsString);
        if (name == null) {
            MessageHelper.replyToMessage(
                    event,
                    "Pick a `script` or attach a `file`. Shipped scripts: "
                            + TestBedScriptService.loadShippedScripts().keySet());
            return null;
        }
        TestBedScript script = TestBedScriptService.getShippedScript(name);
        if (script == null) MessageHelper.replyToMessage(event, "No shipped script named `" + name + "`.");
        return script;
    }

    @Nullable
    private static TestBedScript readAttachedScript(SlashCommandInteractionEvent event, Attachment attachment) {
        if (!"json".equalsIgnoreCase(attachment.getFileExtension())) {
            MessageHelper.replyToMessage(event, "The script file must be a .json file.");
            return null;
        }
        String json = URLReaderHelper.readFromURL(attachment.getUrl(), event.getChannel());
        if (json == null) return null;
        try {
            return TestBedScriptService.parse(json);
        } catch (JacksonException e) {
            MessageHelper.replyToMessage(event, "Could not read the script: " + e.getOriginalMessage());
            return null;
        }
    }
}
