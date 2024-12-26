package ti4.commands2.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.draft.FrankenDraft;
import ti4.draft.OnePickFrankenDraft;
import ti4.draft.PoweredFrankenDraft;
import ti4.draft.PoweredOnePickFrankenDraft;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.franken.FrankenDraftBagService;
import ti4.service.franken.FrankenDraftMode;

class StartFrankenDraft extends GameStateSubcommand {

    public StartFrankenDraft() {
        super(Constants.START_FRANKEN_DRAFT, "Start a franken draft", true, false);
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FORCE, "'True' to forcefully overwrite existing faction setups (Default: False)"));
        addOptions(new OptionData(OptionType.STRING, Constants.DRAFT_MODE, "Special draft mode").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        boolean force = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        if (!force && game.getPlayers().values().stream().anyMatch(Player::isRealPlayer)) {
            String message = "There are players that are currently set up already. Please rerun the command with the force option set to True to overwrite them.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            return;
        }

        String draftOption = event.getOption(Constants.DRAFT_MODE, "", OptionMapping::getAsString);
        FrankenDraftMode draftMode = FrankenDraftMode.fromString(draftOption);
        if (!"".equals(draftOption) && draftMode == null) {
          MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid draft mode.");
          return;
        }

        FrankenDraftBagService.setUpFrankenFactions(game, event, force);
        FrankenDraftBagService.clearPlayerHands(game);

        if (draftMode == null) {
            game.setBagDraft(new FrankenDraft(game));
        } else {
            switch (draftMode) {
                case POWERED -> game.setBagDraft(new PoweredFrankenDraft(game));
                case ONEPICK -> game.setBagDraft(new OnePickFrankenDraft(game));
                case POWEREDONEPICK -> game.setBagDraft(new PoweredOnePickFrankenDraft(game));
            }
        }

        FrankenDraftBagService.startDraft(game);
    }
}


