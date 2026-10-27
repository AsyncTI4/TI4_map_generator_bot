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
import ti4.model.TestBedPreset;
import ti4.service.testbed.TestBedApplyService;
import ti4.service.testbed.TestBedPresetService;
import ti4.service.testbed.TestBedService;
import tools.jackson.core.JacksonException;

class TestBedApply extends GameStateSubcommand {

    static final String PRESET = "preset";
    private static final String FILE = "file";

    TestBedApply() {
        super("apply", "Set up this fresh game from a test bed preset (virtual seats, hands, map)", true, false);
        addOptions(
                new OptionData(OptionType.STRING, PRESET, "Shipped preset").setAutoComplete(true),
                new OptionData(OptionType.ATTACHMENT, FILE, "Your own preset .json (overrides preset)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String refusal = refusalFor(game, event);
        if (refusal != null) {
            MessageHelper.replyToMessage(event, refusal);
            return;
        }
        TestBedPreset preset = readPreset(event);
        if (preset == null) return;
        List<String> errors = TestBedPresetService.validate(preset);
        if (!errors.isEmpty()) {
            MessageHelper.replyToMessage(event, "The preset is invalid:\n- " + String.join("\n- ", errors));
            return;
        }
        String fogMismatch = fogMismatch(game, preset);
        if (fogMismatch != null) {
            MessageHelper.replyToMessage(event, fogMismatch);
            return;
        }
        List<String> warnings = TestBedApplyService.apply(game, preset, event);
        String summary = "Applied test bed preset **" + presetName(preset) + "** with "
                + preset.allSeats().size() + " seats.";
        if (!warnings.isEmpty()) summary += "\nWarnings:\n- " + String.join("\n- ", warnings);
        MessageHelper.replyToMessage(event, summary);
    }

    @Nullable
    private static String refusalFor(Game game, SlashCommandInteractionEvent event) {
        if (!game.getRealPlayers().isEmpty()) {
            return "Refused: this game already has seated factions. Use a freshly created game, or `/testbed reset`"
                    + " first if this is a test bed.";
        }
        return TestBedService.destructiveCommandRefusal(
                event.getGuild(), game, game.getPlayers().values());
    }

    @Nullable
    private static String fogMismatch(Game game, TestBedPreset preset) {
        if (preset.getFog() == null || preset.getFog() == game.isFowMode()) return null;
        if (preset.getFog()) {
            return "This preset needs a fog game. Create one with the fog game creation flow, then apply it there.";
        }
        return "This preset is for normal games, but this is a fog game.";
    }

    @Nullable
    private static TestBedPreset readPreset(SlashCommandInteractionEvent event) {
        OptionMapping fileOption = event.getOption(FILE);
        if (fileOption != null) return readAttachedPreset(event, fileOption.getAsAttachment());
        String name = event.getOption(PRESET, null, OptionMapping::getAsString);
        if (name == null) {
            MessageHelper.replyToMessage(
                    event,
                    "Pick a `preset` or attach a `file`. Shipped presets: "
                            + TestBedPresetService.loadPresets().keySet());
            return null;
        }
        TestBedPreset preset = TestBedPresetService.getPreset(name);
        if (preset == null) MessageHelper.replyToMessage(event, "No shipped preset named `" + name + "`.");
        return preset;
    }

    @Nullable
    private static TestBedPreset readAttachedPreset(SlashCommandInteractionEvent event, Attachment attachment) {
        if (!"json".equalsIgnoreCase(attachment.getFileExtension())) {
            MessageHelper.replyToMessage(event, "The preset file must be a .json file.");
            return null;
        }
        String json = URLReaderHelper.readFromURL(attachment.getUrl(), event.getChannel());
        if (json == null) return null;
        try {
            return TestBedPresetService.parse(json);
        } catch (JacksonException e) {
            MessageHelper.replyToMessage(event, "Could not read the preset: " + e.getOriginalMessage());
            return null;
        }
    }

    private static String presetName(TestBedPreset preset) {
        return preset.getName() == null ? "custom" : preset.getName();
    }
}
