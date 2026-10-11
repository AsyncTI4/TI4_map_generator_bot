package ti4.discord.interactions.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionUnitHandler;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;

public class PurgePN extends GameStateSubcommand {

    public PurgePN() {
        super(Constants.PURGE_PN, "Purge promissory note", true, true);
        addOptions(new OptionData(
                        OptionType.INTEGER,
                        Constants.PROMISSORY_NOTE_ID,
                        "Promissory note ID, which is found between ()")
                .setRequired(true));
        addOptions(CommonOptions.factionColor());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        int pnIndex = event.getOption(Constants.PROMISSORY_NOTE_ID).getAsInt();
        String id = CommandHelper.requireOrReport(
                event,
                CommandHelper.cardIdForNumber(player.getPromissoryNotes(), pnIndex),
                "No such promissory note ID found, please retry");
        if (id == null) return;

        purgePromissoryFromHand(getGame(), player, id);
        MessageHelper.sendMessageToEventChannel(event, "Promissory note purged.");
    }

    private static void purgePromissoryFromHand(Game game, Player player, String pn) {
        game.setPurgedPN(pn);
        player.removePromissoryNote(pn);
        OblivionUnitHandler.doOblivionMechCheck(game, player);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
    }
}
