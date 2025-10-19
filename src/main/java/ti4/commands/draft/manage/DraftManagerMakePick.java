package ti4.commands.draft.manage;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftManager.CommandSource;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;

class DraftManagerMakePick extends GameStateSubcommand {
    public DraftManagerMakePick() {
        super(Constants.DRAFT_MANAGE_MAKE_PICK, "Make a pick for a player in the draft", true, false);
        addOption(OptionType.USER, Constants.PLAYER, "Player to make the pick for", true);
        addOption(
                OptionType.STRING,
                Constants.DRAFTABLE_TYPE_OPTION,
                "Type of draftable to make the pick from",
                true,
                true);
        addOption(OptionType.STRING, Constants.DRAFTABLE_CHOICE_KEY_OPTION, "Key of the choice to make", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        DraftManager draftManager = game.getDraftManager();
        String playerUserId = event.getOption(Constants.PLAYER).getAsString();
        DraftableType draftableType = DraftableType.of(
                event.getOption(Constants.DRAFTABLE_TYPE_OPTION).getAsString());
        String choiceKey =
                event.getOption(Constants.DRAFTABLE_CHOICE_KEY_OPTION).getAsString();
        Draftable draftable = draftManager.getDraftable(draftableType);
        if (draftable == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No draftable of type: " + draftableType);
            return;
        }
        try {
            DraftChoice choice = draftable.getDraftChoice(choiceKey);
            if (choice == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "No choice with key: " + choiceKey + " in draftable of type: " + draftableType);
                return;
            }
            String outcome = draftManager.routeCommand(
                    event,
                    game.getPlayer(playerUserId),
                    draftable.makeCommandKey(choiceKey),
                    CommandSource.SLASH_COMMAND);
            if (DraftButtonService.isError(outcome)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not make pick: " + outcome);
                return;
            }
            // TODO: Handle magic strings from routeCommand, such as "delete button"
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not make pick: " + e.getMessage());
        }
    }
}
