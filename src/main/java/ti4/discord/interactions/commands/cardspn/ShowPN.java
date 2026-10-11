package ti4.discord.interactions.commands.cardspn;

import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

class ShowPN extends GameStateSubcommand {

    public ShowPN() {
        super(Constants.SHOW_PN, "Show Promissory Note to player", true, true);
        addOptions(new OptionData(
                        OptionType.INTEGER,
                        Constants.PROMISSORY_NOTE_ID,
                        "Promissory Note ID which is found between ()")
                .setRequired(true));
        addOptions(CommonOptions.targetFactionOrColor());
        addOptions(CommonOptions.factionColor("Source faction or color (default is you)"));
        addOptions(new OptionData(
                OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, \"y\" or \"yes\" to enable"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        int index = event.getOption(Constants.PROMISSORY_NOTE_ID).getAsInt();
        String pnID = CommandHelper.requireOrReport(
                event,
                CommandHelper.cardIdForNumber(player.getPromissoryNotes(), index),
                "No such promissory note ID found, please retry.");
        if (pnID == null) return;

        OptionMapping longPNOption = event.getOption(Constants.LONG_PN_DISPLAY);
        boolean longPNDisplay = false;
        if (longPNOption != null) {
            longPNDisplay = "y".equalsIgnoreCase(longPNOption.getAsString())
                    || "yes".equalsIgnoreCase(longPNOption.getAsString());
        }

        Game game = getGame();
        Player targetPlayer = CommandHelper.getOtherPlayerOrReply(game, event);
        if (targetPlayer == null) return;

        MessageEmbed pnEmbed = Mapper.getPromissoryNote(pnID).getRepresentationEmbed(!longPNDisplay, false, false);
        player.setPromissoryNote(pnID);

        if (game.isFowMode()) {
            MessageHelper.sendMessageToEventChannel(event, "Promissory note has been shown.");
        } else {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    player.getRepresentation() + " has shown a promissory note to " + targetPlayer.getRepresentation()
                            + ".");
        }
        String message = player.getRepresentation(false, false) + " has shown you a promissory note:";
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, longPNDisplay);
        MessageHelper.sendMessageEmbedsToCardsInfoThread(targetPlayer, message, List.of(pnEmbed));
    }
}
