package ti4.commands.draft.andcat;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable.ReferenceCardPackage;

class AndcatDraftableAddPackage extends GameStateSubcommand {

    protected AndcatDraftableAddPackage() {
        super(Constants.DRAFT_ANDCAT_ADD_PACKAGE, "Add a package to the draft", true, false);
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

        try {
            Integer key = 1;
            while (draftable.getReferenceCardPackages().containsKey(key)) {
                key++;
            }
            ReferenceCardPackage refPackage = new ReferenceCardPackage(key, factions, null, null, null, null);
            draftable.getReferenceCardPackages().put(key, refPackage);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Added package " + key + " (" + factions + ") to the draft.");
            draftable.validateState(getGame().getDraftManager());
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), e.getMessage());
        }
    }
}
