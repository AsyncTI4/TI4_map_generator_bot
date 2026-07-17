package ti4.discord.interactions.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.SecretObjectiveHelper;

class ScoreSO extends GameStateSubcommand {

    ScoreSO() {
        super(Constants.SCORE_SO, "Score Secret Objective", true, true);
        addOptions(new OptionData(
                        OptionType.INTEGER,
                        Constants.SECRET_OBJECTIVE_ID,
                        "Secret objective ID, which is found between ()")
                .setRequired(true));
        addOptions(new OptionData(
                        OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color who scores the secret objective")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int soID = event.getOption(Constants.SECRET_OBJECTIVE_ID).getAsInt();
        SecretObjectiveHelper.scoreSO(event, getGame(), getPlayer(), soID, event.getChannel());
    }
}
