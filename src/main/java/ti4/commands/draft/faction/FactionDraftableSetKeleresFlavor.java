package ti4.commands.draft.faction;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.draftables.FactionDraftable;

class FactionDraftableSetKeleresFlavor extends GameStateSubcommand {

    protected FactionDraftableSetKeleresFlavor() {
        super(Constants.DRAFT_FACTION_SET_KELERES_FLAVOR, "Set the Keleres flavor for the draft", true, false);
        addOption(
                OptionType.STRING,
                Constants.KELERES_FLAVOR_OPTION,
                "One of 'mentak', 'xxcha', or 'argent'",
                true,
                true);
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
        String flavor = event.getOption(Constants.KELERES_FLAVOR_OPTION).getAsString();
        try {
            draftable.setKeleresFlavor(flavor);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Keleres flavor set to '" + flavor + "''.");
            draftable.validateState(getGame().getDraftManager());
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), e.getMessage());
        }
    }
}
