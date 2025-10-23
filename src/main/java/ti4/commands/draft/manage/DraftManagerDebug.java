package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.StringHelper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftSaveService;

class DraftManagerDebug extends GameStateSubcommand {

    public DraftManagerDebug() {
        super(Constants.DRAFT_MANAGE_DEBUG, "Print the raw draft state. WARNING: Can print secret info.", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        String saveData = DraftSaveService.saveDraftManager(draftManager);
        StringBuilder sb = new StringBuilder();
        for (String saveLine : StringHelper.safeSplit(saveData, DraftSaveService.ENCODED_DATA_SEPARATOR)) {
            if (saveLine.startsWith(DraftSaveService.PLAYER_DATA + DraftSaveService.KEY_SEPARATOR)) {
                saveLine = saveLine.replaceFirst(
                        DraftSaveService.PLAYER_DATA + DraftSaveService.KEY_SEPARATOR,
                        "Player UserIDs (w/ short codes): ");
            }
            if (saveLine.startsWith(DraftSaveService.DRAFTABLE_DATA + DraftSaveService.KEY_SEPARATOR)) {
                saveLine = saveLine.replaceFirst(
                        DraftSaveService.DRAFTABLE_DATA + DraftSaveService.KEY_SEPARATOR,
                        "Draftable things w/ state data: ");
            }
            if (saveLine.startsWith(DraftSaveService.ORCHESTRATOR_DATA + DraftSaveService.KEY_SEPARATOR)) {
                saveLine = saveLine.replaceFirst(
                        DraftSaveService.ORCHESTRATOR_DATA + DraftSaveService.KEY_SEPARATOR,
                        "Orchestrator w/ state data: ");
            }
            if (saveLine.startsWith(DraftSaveService.PLAYER_PICK_DATA + DraftSaveService.KEY_SEPARATOR)) {
                saveLine = saveLine.replaceFirst(
                        DraftSaveService.PLAYER_PICK_DATA + DraftSaveService.KEY_SEPARATOR, "Player pick: ");
            }
            if (saveLine.startsWith(DraftSaveService.PLAYER_ORCHESTRATOR_STATE_DATA + DraftSaveService.KEY_SEPARATOR)) {
                saveLine = saveLine.replaceFirst(
                        DraftSaveService.PLAYER_ORCHESTRATOR_STATE_DATA + DraftSaveService.KEY_SEPARATOR,
                        "Orchestrator-specific player state: ");
            }
            sb.append(saveLine);
            sb.append(System.lineSeparator());
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        draftManager.validateState();
    }
}
