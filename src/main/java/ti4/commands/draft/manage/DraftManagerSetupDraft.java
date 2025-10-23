package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.map.Game;

class DraftManagerSetupDraft extends GameStateSubcommand {
    public DraftManagerSetupDraft() {
        super(Constants.DRAFT_MANAGE_SETUP_DRAFT, "Setup a new draft, with nothing preset", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftSystemSettings settings = new DraftSystemSettings(game, null);
        game.setDraftSystemSettings(settings);
        settings.postMessageAndButtons(event);
    }
}
