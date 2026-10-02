package ti4.discord.interactions.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.franken.FrankenDraftMode;
import ti4.service.franken.FrankenDraftStartService;

class StartFrankenDraft extends GameStateSubcommand {

    public StartFrankenDraft() {
        super(Constants.START_FRANKEN_DRAFT, "Start a franken draft", true, false);
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.FORCE,
                "'True' to forcefully overwrite existing faction setups (Default: False)"));
        addOptions(new OptionData(OptionType.STRING, Constants.DRAFT_MODE, "Special draft mode").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        boolean force = event.getOption(Constants.FORCE, Boolean.FALSE, OptionMapping::getAsBoolean);
        String draftOption = event.getOption(Constants.DRAFT_MODE, "", OptionMapping::getAsString);
        FrankenDraftMode draftMode = FrankenDraftMode.fromString(draftOption);
        if (!"".equals(draftOption) && draftMode == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid draft mode.");
            return;
        }

        String error = FrankenDraftStartService.startFrankenDraft(event, game, force, draftMode);
        if (error != null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), error);
        }
    }
}
