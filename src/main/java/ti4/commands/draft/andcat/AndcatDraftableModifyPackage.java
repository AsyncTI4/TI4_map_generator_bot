package ti4.commands.draft.andcat;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable;

class AndcatDraftableModifyPackage extends GameStateSubcommand {

    protected AndcatDraftableModifyPackage() {
        super(Constants.DRAFT_ANDCAT_MODIFY_PACKAGE, "Modify a package in the draft", true, false);
        addOption(
                OptionType.INTEGER,
                Constants.PACKAGE_KEY_OPTION,
                "The key of the package to modify (just the number)",
                true,
                true);
        addOption(OptionType.STRING, Constants.FACTION, "The first faction in the package", true, true);
        addOption(OptionType.STRING, Constants.FACTION2, "The second faction in the package", true, true);
        addOption(OptionType.STRING, Constants.FACTION3, "The third faction in the package", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        AndcatReferenceCardsDraftable draftable = AndcatDraftableGroup.getDraftable(event, getGame());
        if (draftable == null) {
            // Error already sent
            return;
        }
        List<String> factions = AndcatDraftableGroup.getFactionList(event);
        if (factions == null) {
            // Error message already sent
            return;
        }
        Integer packageKey = event.getOption(Constants.PACKAGE_KEY_OPTION, null, OptionMapping::getAsInt);
        if (!draftable.getReferenceCardPackages().containsKey(packageKey)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "No package with key " + packageKey + " exists in the draft.");
            return;
        }

        try {
            AndcatReferenceCardsDraftable.ReferenceCardPackage refPackage =
                    new AndcatReferenceCardsDraftable.ReferenceCardPackage(
                            packageKey, factions, null, null, null, null);
            draftable.getReferenceCardPackages().put(packageKey, refPackage);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Modified package " + packageKey + " to (" + factions + ") in the draft.");
            draftable.validateState(getGame().getDraftManager());
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), e.getMessage());
        }
    }
}
