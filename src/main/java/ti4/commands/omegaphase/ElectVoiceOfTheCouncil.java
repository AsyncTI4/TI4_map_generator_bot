package ti4.commands.omegaphase;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.omegaPhase.VoiceOfTheCouncilHelper;

class ElectVoiceOfTheCouncil extends GameStateSubcommand {
    public ElectVoiceOfTheCouncil() {
        super(Constants.ELECT_VOICE_OF_THE_COUNCIL, "Resolve Voice of the Council, electing the specified player", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to elect as Voice of the Council"))
            .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR,
                "Elect another Faction or Color")
                    .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        VoiceOfTheCouncilHelper.ElectVoiceOfTheCouncil(getGame(), getPlayer());
    }
}