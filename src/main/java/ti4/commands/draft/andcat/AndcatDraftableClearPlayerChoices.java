package ti4.commands.draft.andcat;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.draft.AndcatReferenceCardsMessageHelper;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable.ReferenceCardPackage;

class AndcatDraftableClearPlayerChoices extends GameStateSubcommand {

    protected AndcatDraftableClearPlayerChoices() {
        super(Constants.DRAFT_ANDCAT_CLEAR_PLAYER_CHOICES, "Clear player choices", true, true);
        addOption(OptionType.USER, Constants.PLAYER, "Player to clear choices for", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        DraftManager draftManager = getGame().getDraftManager();
        AndcatReferenceCardsDraftable draftable = AndcatDraftableGroup.getDraftable(event, getGame());
        if (draftable == null) {
            // Error already sent
            return;
        }

        Player player = getPlayer();
        if (!draftManager.getPlayerStates().containsKey(player.getUserID())) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Player " + player.getRepresentationNoPing() + " is not part of the draft.");
            return;
        }
        List<DraftChoice> picks = draftManager.getPlayerPicks(player.getUserID(), draftable.getType());
        if (picks.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Player " + player.getRepresentationNoPing() + " has not made any picks for " + draftable.getType()
                            + " yet.");
            return;
        }
        ReferenceCardPackage refPackage =
                draftable.getPackageByChoiceKey(picks.get(0).getChoiceKey());
        if (refPackage == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Could not find package for player " + player.getRepresentationNoPing() + "'s picks.");
            return;
        }

        ReferenceCardPackage updatedPackage =
                new ReferenceCardPackage(refPackage.key(), refPackage.factions(), null, null, null, false);
        draftable.getReferenceCardPackages().put(refPackage.key(), updatedPackage);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Cleared choices for player " + player.getRepresentationNoPing() + " for package " + refPackage.key()
                        + ".");

        AndcatReferenceCardsMessageHelper refPackageMessageHelper = new AndcatReferenceCardsMessageHelper(draftable);
        refPackageMessageHelper.updatePackageButtons(event, draftManager, player, updatedPackage);
        refPackageMessageHelper.updatePackagePickSummary(draftManager);
    }
}
