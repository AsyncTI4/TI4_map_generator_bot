package ti4.discord.interactions.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.draft.FrankenDraft;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.Source.ComponentSource;

public class EnableFactionSource extends GameStateSubcommand {

    public EnableFactionSource() {
        super("enable_faction_source", "Add a faction source to a Franken draft", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.SOURCE, "Faction source")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (getGame().getActiveBagDraft() != null) {
            MessageHelper.sendMessageToEventChannel(event, "Faction sources cannot be changed after the draft starts.");
            return;
        }

        ComponentSource source =
                ComponentSource.fromString(event.getOption(Constants.SOURCE, OptionMapping::getAsString));
        if (source == null || Mapper.getFactionsValues().stream().noneMatch(faction -> faction.getSource() == source)) {
            MessageHelper.sendMessageToEventChannel(
                    event, "That source has no factions available for Franken drafting.");
            return;
        }

        if (!FrankenDraft.enableFactionSource(getGame(), source)) {
            MessageHelper.sendMessageToEventChannel(event, source.prettyName() + " factions are already enabled.");
            return;
        }

        MessageHelper.sendMessageToEventChannel(
                event, "Enabled " + source.prettyName() + " factions for Franken drafts.");
    }
}
