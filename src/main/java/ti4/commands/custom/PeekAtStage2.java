package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

class PeekAtStage2 extends GameStateSubcommand {

    public PeekAtStage2() {
        super(Constants.PEEK_AT_STAGE2, "Peek at a stage 2 objective", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.LOCATION1, "Location Of Objective (typical 1-5)").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        Integer loc1 = event.getOption(Constants.LOCATION1, null, OptionMapping::getAsInt);
        secondHalfOfPeak(event, game, player, loc1);

    }

    public void secondHalfOfPeak(GenericInteractionCreateEvent event, Game game, Player player, int loc1) {
        String obj = game.peekAtStage2(loc1, player);
        PublicObjectiveModel po = Mapper.getPublicObjective(obj);
        String sb = player.getRepresentationUnfogged() +
            " **Stage 2 Public Objective at location " + loc1 + "**" + "\n" +
            po.getRepresentation() + "\n";
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb);
    }
}
