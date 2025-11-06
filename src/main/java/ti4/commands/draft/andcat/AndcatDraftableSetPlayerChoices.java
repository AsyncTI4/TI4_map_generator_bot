package ti4.commands.draft.andcat;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.draft.AndcatReferenceCardsMessageHelper;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable.ReferenceCardPackage;

class AndcatDraftableSetPlayerChoices extends GameStateSubcommand {

    protected AndcatDraftableSetPlayerChoices() {
        super(Constants.DRAFT_ANDCAT_SET_PLAYER_CHOICES, "Set which faction does what", true, true);
        addOption(OptionType.USER, Constants.PLAYER, "Player to make the pick for", true);
        addOption(
                OptionType.STRING,
                Constants.HOMESYSTEM_FACTION_OPTION,
                "The faction to use for the homesystem",
                true,
                true);
        addOption(
                OptionType.STRING,
                Constants.STARTING_FLEET_FACTION_OPTION,
                "The faction to use for the starting fleet",
                true,
                true);
        addOption(
                OptionType.STRING,
                Constants.PRIORITY_NUMBER_FACTION_OPTION,
                "The faction to use for the priority number",
                true,
                true);
        addOption(
                OptionType.BOOLEAN,
                Constants.LOCK_CHOICES_OPTION,
                "Lock in the choices so they can't be changed later",
                false);
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
        String homeSystemFaction =
                event.getOption(Constants.HOMESYSTEM_FACTION_OPTION, null, OptionMapping::getAsString);
        String startingFleetFaction =
                event.getOption(Constants.STARTING_FLEET_FACTION_OPTION, null, OptionMapping::getAsString);
        String priorityNumberFaction =
                event.getOption(Constants.PRIORITY_NUMBER_FACTION_OPTION, null, OptionMapping::getAsString);
        Boolean lockChoices = event.getOption(Constants.LOCK_CHOICES_OPTION, false, OptionMapping::getAsBoolean);

        // Ensure factions exist
        if (Mapper.getFaction(homeSystemFaction) == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Could not find faction for homesystem: " + homeSystemFaction);
            return;
        }
        if (Mapper.getFaction(startingFleetFaction) == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Could not find faction for starting fleet: " + startingFleetFaction);
            return;
        }
        if (Mapper.getFaction(priorityNumberFaction) == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Could not find faction for priority number: " + priorityNumberFaction);
            return;
        }
        if (homeSystemFaction.equals(startingFleetFaction)
                || homeSystemFaction.equals(priorityNumberFaction)
                || startingFleetFaction.equals(priorityNumberFaction)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Factions for homesystem, starting fleet, and priority number must be different.");
            return;
        }

        // Ensure factions are part of the picked package
        if (!refPackage.factions().contains(homeSystemFaction)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Homesystem faction " + homeSystemFaction + " is not part of package " + refPackage.key() + ": "
                            + refPackage.factions());
            return;
        }
        if (!refPackage.factions().contains(startingFleetFaction)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Starting fleet faction " + startingFleetFaction + " is not part of package " + refPackage.key()
                            + ": " + refPackage.factions());
            return;
        }
        if (!refPackage.factions().contains(priorityNumberFaction)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Priority number faction " + priorityNumberFaction + " is not part of package " + refPackage.key()
                            + ": " + refPackage.factions());
            return;
        }

        ReferenceCardPackage updatedPackage = new ReferenceCardPackage(
                refPackage.key(),
                refPackage.factions(),
                homeSystemFaction,
                startingFleetFaction,
                priorityNumberFaction,
                lockChoices);
        draftable.getReferenceCardPackages().put(refPackage.key(), updatedPackage);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Set choices for player " + player.getRepresentationNoPing() + " for package "
                        + refPackage.key() + ": Homesystem=" + homeSystemFaction
                        + ", Starting Fleet=" + startingFleetFaction
                        + ", Priority Number=" + priorityNumberFaction
                        + (lockChoices ? " (locked)" : ""));

        // If the draft is showing as finished, try to setup players now
        if (draftManager.whatsStoppingDraftEnd() == null && draftManager.whatsStoppingSetup() == null) {
            draftManager.trySetupPlayers(event);
        } else {
            AndcatReferenceCardsMessageHelper refPackageMessageHelper =
                    new AndcatReferenceCardsMessageHelper(draftable);
            refPackageMessageHelper.updatePackageButtons(event, draftManager, player, updatedPackage);
            refPackageMessageHelper.updatePackagePickSummary(draftManager);
        }
    }
}
