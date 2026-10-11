package ti4.discord.interactions.commands.draft.faction;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.draftables.FactionDraftable;

class FactionDraftableAddFaction extends GameStateSubcommand {

    FactionDraftableAddFaction() {
        super(Constants.DRAFT_FACTION_ADD, "Add a faction to the draft", true, true);
        addOption(OptionType.STRING, Constants.FACTION, "The faction to add", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        FactionDraftable draftable = CommandHelper.requireOrReport(
                event,
                FactionDraftableGroup.getDraftable(getGame()),
                "Factions aren't draftable; you may need `/draft manage add_draftable Faction`.");
        if (draftable == null) return;
        String factionAlias = event.getOption(Constants.FACTION).getAsString();
        try {
            draftable.addFaction(factionAlias);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Added faction " + factionAlias + " to the draft. ");
            draftable.validateState(getGame().getDraftManager());
        } catch (IllegalArgumentException e) {
            MessageHelper.sendMessageToChannel(event.getChannel(), e.getMessage());
        }
    }
}
