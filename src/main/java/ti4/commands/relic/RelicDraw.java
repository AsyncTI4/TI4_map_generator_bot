package ti4.commands.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.RelicHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;

class RelicDraw extends GameStateSubcommand {

    public RelicDraw() {
        super(Constants.RELIC_DRAW, "Draw a relic", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        RelicHelper.drawRelicAndNotify(getPlayer(), event, getGame());
    }

    @ButtonHandler("drawRelicAtPosition_")
    public static void resolveDrawRelicAtPosition(Player player, ButtonInteractionEvent event, Game game, String buttonID) {
        int position = Integer.parseInt(buttonID.split("_")[1]);
        if (player.getPromissoryNotes().containsKey("dspnflor") && game.getPNOwner("dspnflor") != player) {
            PromissoryNoteHelper.resolvePNPlay("dspnflorChecked", player, game, event);
        }
        RelicHelper.drawRelicAndNotify(player, event, game, position, true);
        event.getMessage().delete().queue();
    }
}
