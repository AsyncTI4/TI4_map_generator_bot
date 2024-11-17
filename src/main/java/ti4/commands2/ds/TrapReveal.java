package ti4.commands2.ds;

import java.util.Collection;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiscordantStarsHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class TrapReveal extends GameStateSubcommand {

    public TrapReveal() {
        super(Constants.LIZHO_REVEAL_TRAP, "Select planets were to reveal trap tokens", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.LIZHO_TRAP_ID, "Trap ID").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String planetName = event.getOption(Constants.PLANET).getAsString();
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }

        Player player = getPlayer();
        Collection<Integer> values = player.getTrapCards().values();
        int trapID = event.getOption(Constants.LIZHO_TRAP_ID).getAsInt();
        if (!values.contains(trapID)) {
            MessageHelper.replyToMessage(event, "Trap ID not found");
            return;
        }
        String stringTrapID = "";
        for (String trapIDS : player.getTrapCards().keySet()) {
            if (player.getTrapCards().get(trapIDS) == trapID) {
                stringTrapID = trapIDS;
            }
        }
        DiscordantStarsHelper.revealTrapForPlanet(event, game, planetName, stringTrapID, player, true);
    }

    @ButtonHandler("steal2tg_")
    public static void steal2Tg(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        int count = Math.min(p2.getTg(), 2);
        p2.setTg(p2.getTg() - count);
        player.setTg(player.getTg() + count);
        String msg1 = p2.getRepresentationUnfogged() + " you had " + count + " TG" + (count == 1 ? "" : "s") + " stolen by a trap";
        String msg2 = player.getRepresentationUnfogged() + " you stole " + count + " TG" + (count == 1 ? "" : "s") + " via a trap";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("steal3comm_")
    public static void steal3Comm(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        int count = Math.min(p2.getCommodities(), 3);
        p2.setCommodities(p2.getCommodities() - count);
        player.setTg(player.getTg() + count);
        String msg1 = p2.getRepresentationUnfogged() + " you had " + count + " comm" + (count == 1 ? "" : "s") + " stolen by a trap";
        String msg2 = player.getRepresentationUnfogged() + " you stole " + count + " comm" + (count == 1 ? "" : "s") + " via a trap";
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        ButtonHelper.deleteMessage(event);
    }
}
