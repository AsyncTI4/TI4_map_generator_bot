package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftManager.CommandSource;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;

class DraftManagerSendCustomDraftableCommand extends GameStateSubcommand {
    public DraftManagerSendCustomDraftableCommand() {
        super(
                Constants.DRAFT_MANAGE_CUSTOM_COMMAND,
                "Send a custom command to a Draftable Type in the draft",
                true,
                false);
        addOption(OptionType.USER, Constants.PLAYER, "Player sending the command", true);
        addOption(
                OptionType.STRING,
                Constants.DRAFTABLE_TYPE_OPTION,
                "Type of draftable to receive the command",
                true,
                true);
        addOption(OptionType.STRING, Constants.DRAFT_COMMAND_OPTION, "Key of the command", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        String playerUserId = event.getOption(Constants.PLAYER).getAsUser().getId();
        DraftableType draftableType = DraftableType.of(
                event.getOption(Constants.DRAFTABLE_TYPE_OPTION).getAsString());
        String commandKey = event.getOption(Constants.DRAFT_COMMAND_OPTION).getAsString();
        Draftable draftable = draftManager.getDraftable(draftableType);
        if (draftable == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No draftable of type: " + draftableType);
            return;
        }
        try {
            String outcome = draftManager.routeCommand(
                    event,
                    game.getPlayer(playerUserId),
                    draftable.makeCommandKey(commandKey),
                    CommandSource.SLASH_COMMAND);
            if (DraftButtonService.isError(outcome)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not deliver command: " + outcome);
                return;
            }
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Sent custom command for player "
                            + game.getPlayer(playerUserId).getPing()
                            + ": "
                            + commandKey
                            + " from "
                            + draftable.getDisplayName());
            // TODO: Handle magic strings from routeCommand, such as "delete button"
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not deliver command: " + e.getMessage());
        }
    }
}
