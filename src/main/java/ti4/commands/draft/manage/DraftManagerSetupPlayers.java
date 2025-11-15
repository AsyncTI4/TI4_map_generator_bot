package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.draft.DraftManager;

class DraftManagerSetupPlayers extends GameStateSubcommand {

    public DraftManagerSetupPlayers() {
        super(Constants.DRAFT_MANAGE_SETUP_PLAYERS, "Have the draft elements set up players", true, false);
        addOption(
                OptionType.BOOLEAN,
                Constants.FORCE_OPTION,
                "Attempt to ignore any blocking reason and setup anyway",
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        boolean force = event.getOption(Constants.FORCE_OPTION, false, OptionMapping::getAsBoolean);
        if (force) {
            draftManager.setupPlayers(event);
        } else {
            draftManager.trySetupPlayers(event);
        }
    }
}
