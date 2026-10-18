package ti4.discord.interactions.commands.draft.manage;

import java.util.regex.Pattern;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.StringHelper;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftSaveService;

class DraftManagerDebug extends GameStateSubcommand {

    private static final Pattern PLAYER_ORCHESTRATOR_STATE_KEY_PATTERN =
            Pattern.compile(DraftSaveService.PLAYER_ORCHESTRATOR_STATE_DATA + DraftSaveService.KEY_SEPARATOR);
    private static final Pattern PLAYER_PICK_KEY_PATTERN =
            Pattern.compile(DraftSaveService.PLAYER_PICK_DATA + DraftSaveService.KEY_SEPARATOR);
    private static final Pattern ORCHESTRATOR_KEY_PATTERN =
            Pattern.compile(DraftSaveService.ORCHESTRATOR_DATA + DraftSaveService.KEY_SEPARATOR);
    private static final Pattern DRAFTABLE_KEY_PATTERN =
            Pattern.compile(DraftSaveService.DRAFTABLE_DATA + DraftSaveService.KEY_SEPARATOR);
    private static final Pattern PLAYER_KEY_PATTERN =
            Pattern.compile(DraftSaveService.PLAYER_DATA + DraftSaveService.KEY_SEPARATOR);

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
                saveLine = PLAYER_KEY_PATTERN.matcher(saveLine).replaceFirst("Player UserIDs (w/ short codes): ");
            }
            if (saveLine.startsWith(DraftSaveService.DRAFTABLE_DATA + DraftSaveService.KEY_SEPARATOR)) {
                saveLine = DRAFTABLE_KEY_PATTERN.matcher(saveLine).replaceFirst("Draftable things w/ state data: ");
            }
            if (saveLine.startsWith(DraftSaveService.ORCHESTRATOR_DATA + DraftSaveService.KEY_SEPARATOR)) {
                saveLine = ORCHESTRATOR_KEY_PATTERN.matcher(saveLine).replaceFirst("Orchestrator w/ state data: ");
            }
            if (saveLine.startsWith(DraftSaveService.PLAYER_PICK_DATA + DraftSaveService.KEY_SEPARATOR)) {
                saveLine = PLAYER_PICK_KEY_PATTERN.matcher(saveLine).replaceFirst("Player pick: ");
            }
            if (saveLine.startsWith(DraftSaveService.PLAYER_ORCHESTRATOR_STATE_DATA + DraftSaveService.KEY_SEPARATOR)) {
                saveLine = PLAYER_ORCHESTRATOR_STATE_KEY_PATTERN
                        .matcher(saveLine)
                        .replaceFirst("Orchestrator-specific player state: ");
            }
            sb.append(saveLine);
            sb.append(System.lineSeparator());
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        draftManager.validateState();
    }
}
