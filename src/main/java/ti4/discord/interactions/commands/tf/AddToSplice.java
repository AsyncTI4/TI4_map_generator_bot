package ti4.discord.interactions.commands.tf;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class AddToSplice extends GameStateSubcommand {

    public AddToSplice() {
        super(Constants.ADD_TO_SPLICE, "Add to an existing Splice", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.SPLICE_TYPE, "The splice type")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "A player who should be added to the splice")
                .setRequired(false));
        addOptions(new OptionData(
                        OptionType.INTEGER,
                        Constants.POSITION,
                        "Player's position in the splice (1st, 2nd, etc). Default is last.")
                .setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String spliceType = event.getOption(Constants.SPLICE_TYPE, OptionMapping::getAsString);

        String card = ButtonHelperTwilightsFall.addSpliceCardToSplice(game, spliceType);
        MessageHelper.sendMessageToChannel(event.getChannel(), "Added " + card + " to the splice!");
        List<Player> participants = new ArrayList<>();
        for (String faction : game.getStoredValue("savedParticipants").split("_")) {
            if (game.getPlayerFromColorOrFaction(faction) != null)
                participants.add(game.getPlayerFromColorOrFaction(faction));
        }
        if (event.getOption("player1") != null) {
            String playerUserId2 = event.getOption("player1").getAsUser().getId();
            if (game.getPlayer(playerUserId2) != null) {
                if (event.getOption("position") != null) {
                    int position = event.getOption("position").getAsInt() - 1;
                    if (position < 0 || position > participants.size()) {
                        event.reply("Invalid position! Please enter a number between 1 and "
                                        + (participants.size() + 1))
                                .setEphemeral(true)
                                .queue();
                        return;
                    }
                    participants.add(position, game.getPlayer(playerUserId2));
                } else {
                    participants.add(game.getPlayer(playerUserId2));
                }
            }
            game.removeStoredValue("savedParticipants");
            for (Player p : participants) {
                if (game.getStoredValue("savedParticipants").isEmpty()) {
                    game.setStoredValue("savedParticipants", p.getFaction());
                } else {
                    game.setStoredValue(
                            "savedParticipants", game.getStoredValue("savedParticipants") + "_" + p.getFaction());
                }
            }
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "The splice has the following order of participants:\n"
                            + ButtonHelperTwilightsFall.getSpliceOrderString(participants));
        }
        ButtonHelperTwilightsFall.sendPlayerSpliceOptions(game, participants.getFirst());
    }
}
