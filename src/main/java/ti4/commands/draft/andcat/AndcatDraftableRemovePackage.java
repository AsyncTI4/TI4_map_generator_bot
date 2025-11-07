package ti4.commands.draft.andcat;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable;

class AndcatDraftableRemovePackage extends GameStateSubcommand {

    protected AndcatDraftableRemovePackage() {
        super(Constants.DRAFT_ANDCAT_REMOVE_PACKAGE, "Remove a package from the draft", true, false);
        addOption(
                OptionType.INTEGER,
                Constants.PACKAGE_KEY_OPTION,
                "The key of the package to remove (just the number)",
                true,
                true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        AndcatReferenceCardsDraftable draftable = AndcatDraftableGroup.getDraftable(event, getGame());
        if (draftable == null) {
            // Error already sent
            return;
        }
        Integer packageNum = event.getOption(Constants.PACKAGE_KEY_OPTION, null, OptionMapping::getAsInt);
        if (!draftable.getReferenceCardPackages().containsKey(packageNum)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "No package with key " + packageNum + " exists in the draft.");
            return;
        }

        DraftManager draftManager = getGame().getDraftManager();

        try {
            String choiceKey = AndcatReferenceCardsDraftable.getChoiceKey(packageNum);
            draftable.getReferenceCardPackages().remove(packageNum);
            // Also remove any picks players have made for that package
            for (var playerState : draftManager.getPlayerStates().values()) {
                playerState.getPicks(draftable.getType()).removeIf(c -> c.getChoiceKey()
                        .equals(choiceKey));
            }

            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Removed package " + packageNum + " from the draft.");
            draftable.validateState(getGame().getDraftManager());
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), e.getMessage());
        }
    }
}
