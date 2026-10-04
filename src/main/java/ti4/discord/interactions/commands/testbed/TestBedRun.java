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
import ti4.helpers.URLReaderHelper;
import ti4.message.MessageHelper;
import ti4.model.TestBedScript;
import ti4.service.testbed.TestBedScriptRunner;
import ti4.service.testbed.TestBedScriptService;
import ti4.service.testbed.TestBedService;
import tools.jackson.core.JacksonException;

class TestBedRun extends GameStateSubcommand {

    static final String SCRIPT = "script";
    static final String ALL = "all";
    private static final String FILE = "file";

    TestBedRun() {
        super("run", "Run a test bed script (or `all`): press buttons as seats and check the results", false, false);
        addOptions(
                new OptionData(OptionType.STRING, SCRIPT, "Shipped script, or `all` for every script")
                        .setAutoComplete(true),
                new OptionData(OptionType.ATTACHMENT, FILE, "Your own script .json (overrides script)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String refusal = TestBedService.destructiveCommandRefusal(
                event.getGuild(), game, game.getPlayers().values());
        if (refusal != null) {
            MessageHelper.replyToMessage(event, refusal);
            return;
        }
        if (event.getOption(FILE) == null && ALL.equals(event.getOption(SCRIPT, null, OptionMapping::getAsString))) {
            List<TestBedScript> scripts =
                    List.copyOf(TestBedScriptService.loadScripts().values());
            MessageHelper.replyToMessage(
                    event,
                    "Running " + scripts.size() + " scripts, each from a fresh reset. The summary follows here.");
            TestBedScriptRunner.startSuite(game, scripts, event);
            return;
        }
        TestBedScript script = readScript(event);
        if (script == null) return;
        List<String> errors = TestBedScriptService.validate(script);
        if (!errors.isEmpty()) {
            MessageHelper.replyToMessage(event, "The script is invalid:\n- " + String.join("\n- ", errors));
            return;
        }
        if (!TestBedService.isTestBed(game) && script.getPreset() == null) {
            MessageHelper.replyToMessage(
                    event, "This game is not a test bed. Use a script with a `preset`, or apply a preset first.");
            return;
        }
        MessageHelper.replyToMessage(
                event,
                "Running script **" + (script.getName() == null ? "custom" : script.getName()) + "** ("
                        + script.getSteps().size() + " steps"
                        + (script.getPreset() == null ? "" : ", starting from a fresh `" + script.getPreset() + "`")
                        + "). The report follows here.");
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
                            + TestBedScriptService.loadScripts().keySet());
            return null;
        }
        TestBedScript script = TestBedScriptService.getScript(name);
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
