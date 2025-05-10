package ti4.commands.player;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class SCUnpick extends GameStateSubcommand {

    public SCUnpick() {
        super(Constants.SC_UNPICK, "Unpick a Strategy Card", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy card initiative number").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color returning strategy card").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        boolean isFowPrivateGame = FoWHelper.isPrivateGame(game, event);

        Collection<Player> activePlayers = game.getPlayers().values().stream()
            .filter(player_ -> player_.getFaction() != null && !player_.getFaction().isEmpty() && !"null".equals(player_.getColor()))
            .toList();
        int maxSCsPerPlayer = game.getSCList().size() / activePlayers.size();

        OptionMapping option = event.getOption(Constants.STRATEGY_CARD);
        int scUnpicked = option.getAsInt();

        Player player = getPlayer();
        player.removeSC(scUnpicked);
        List<Button> scButtons = Helper.getRemainingSCButtons(game, player);
        game.updateActivePlayer(player);
        game.setPhaseOfGame("strategy");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation()+" pick an SC please", scButtons);
        

        

       
    }
}
