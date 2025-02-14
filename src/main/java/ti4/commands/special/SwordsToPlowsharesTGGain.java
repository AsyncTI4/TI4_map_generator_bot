package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;

class SwordsToPlowsharesTGGain extends GameStateSubcommand {

    public SwordsToPlowsharesTGGain() {
        super(
                Constants.SWORDS_TO_PLOWSHARES,
                "Swords to Plowshares: kill half your infantry to get that many trade goods",
                true,
                true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        AgendaHelper.doSwords(getPlayer(), event, getGame());
    }
}
