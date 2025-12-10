package ti4.commands.tf;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;

class StartNewSplice extends GameStateSubcommand {

    public StartNewSplice() {
        super(Constants.START_NEW_SPLICE, "Start a new Splice", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.SPLICE_TYPE, "The splice type")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "The player starting the splice")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "2nd player in the splice"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String spliceType = event.getOption(Constants.SPLICE_TYPE, OptionMapping::getAsString);
        List<Player> participants = new ArrayList<>();
        Player player = getPlayer();
        participants.add(player);
        for (int x = 2; x < 9; x++) {
            String constant = "player" + x;
            if (event.getOption(constant) != null) {
                String playerUserId2 = event.getOption(constant).getAsUser().getId();
                if (game.getPlayer(playerUserId2) != null && !participants.contains(game.getPlayer(playerUserId2))) {
                    participants.add(game.getPlayer(playerUserId2));
                }
            }
        }

        ButtonHelperTwilightsFall.initiateASplice(game, player, spliceType, participants);
    }
}
