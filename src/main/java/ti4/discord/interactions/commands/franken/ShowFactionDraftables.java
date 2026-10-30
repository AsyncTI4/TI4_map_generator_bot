package ti4.discord.interactions.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.draft.FrankenDrazDraft;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.Mapper;

public class ShowFactionDraftables extends Subcommand {

    public ShowFactionDraftables() {
        super("show_faction_draftables", "Show a faction's draftable Franken components");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String faction = event.getOption(Constants.FACTION, OptionMapping::getAsString);
        if (!Mapper.isValidFaction(faction)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid faction.");
            return;
        }

        FrankenDrazDraft.sendFactionComponentCards(event, faction);
    }
}
