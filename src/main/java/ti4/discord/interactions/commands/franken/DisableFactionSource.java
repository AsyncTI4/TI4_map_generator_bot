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

public class DisableFactionSource extends GameStateSubcommand {

    public DisableFactionSource() {
        super("disable_faction_source", "Remove an enabled faction source", true, false);
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

        if (!FrankenDraft.disableFactionSource(getGame(), source)) {
            MessageHelper.sendMessageToEventChannel(event, source.prettyName() + " factions are not enabled.");
            return;
        }

        MessageHelper.sendMessageToEventChannel(
                event, "Disabled " + source.prettyName() + " factions for Franken drafts.");
    }
}
