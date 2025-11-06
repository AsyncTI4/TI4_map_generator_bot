package ti4.commands.draft.faction;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.draftables.FactionDraftable;

class FactionDraftableRemoveFaction extends GameStateSubcommand {

    protected FactionDraftableRemoveFaction() {
        super(Constants.DRAFT_FACTION_REMOVE, "Remove a faction from the draft", true, true);
        addOption(OptionType.STRING, Constants.DRAFT_FACTION_OPTION, "The faction to remove", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        FactionDraftable draftable = FactionDraftableGroup.getDraftable(getGame());
        if (draftable == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Factions aren't draftable; you may need `/draft manage add_draftable Faction`.");
            return;
        }
        String factionAlias = event.getOption(Constants.DRAFT_FACTION_OPTION).getAsString();
        try {
            draftable.removeFaction(factionAlias);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Removed faction " + factionAlias + " from the draft.");
            draftable.validateState(getGame().getDraftManager());
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), e.getMessage());
        }
    }
}
