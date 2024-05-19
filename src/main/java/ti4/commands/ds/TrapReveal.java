package ti4.commands.ds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TrapReveal extends DiscordantStarsSubcommandData {

    public TrapReveal() {
        super(Constants.LIZHO_REVEAL_TRAP, "Select planets were to reveal trap tokens");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.LIZHO_TRAP_ID, "Trap ID").setRequired(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping planetOption = event.getOption(Constants.PLANET);
        if (planetOption == null) {
            return;
        }
        String planetName = planetOption.getAsString();
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }

        OptionMapping trapIDOption = event.getOption(Constants.LIZHO_TRAP_ID);
        if (trapIDOption == null) {
            return;
        }

        Collection<Integer> values = player.getTrapCards().values();
        int trapID = trapIDOption.getAsInt();
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
        revealTrapForPlanet(event, game, planetName, stringTrapID, player, true);
    }

    public void revealTrapForPlanet(GenericInteractionCreateEvent event, Game game, String planetName, String trap, Player player, boolean reveal) {
        if (player.getTrapCardsPlanets().containsValue(planetName) || planetName == null) {
            Map<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
            for (Map.Entry<String, String> entry : trapCardsPlanets.entrySet()) {
                String planet = entry.getValue();
                if (planetName.equals(planet) || planet == null) {
                    ButtonHelperAbilities.removeATrapToken(game, planetName);
                    player.removeTrapCardPlanet(trap);
                    player.setTrapCard(trap);
                    Map<String, String> dsHandcards = Mapper.getDSHandcards();
                    String info = dsHandcards.get(trap);
                    String[] split = info.split(";");
                    String trapType = split[0];
                    String trapName = split[1];
                    String trapText = split[2];
                    Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
                    String representation = planetRepresentations.get(planet);
                    if (representation == null) {
                        representation = planet;
                    }
                    if (reveal && planet != null) {

                        String sb = "__**" + "Trap: " + trapName + "**__" + " - " + trapText + "\n" +
                            "__**" + "Has been revealed on planet: " + representation + "**__";
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb);
                        if ("Minefields".equalsIgnoreCase(trapName)) {
                            for (Player p2 : game.getRealPlayers()) {
                                if (p2 == player) {
                                    continue;
                                }
                                new RemoveUnits().unitParsing(event, p2.getColor(), game.getTileFromPlanet(planet), "2 inf " + planet, game);
                            }
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Destroyed up to 2 enemy infantry from " + representation);
                        }
                        if ("Account Siphon".equalsIgnoreCase(trapName)) {
                            for (Player p2 : game.getRealPlayers()) {
                                if (p2 == player) {
                                    continue;
                                }
                                if (p2.getPlanets().contains(planet)) {
                                    List<Button> buttons = new ArrayList<>();
                                    buttons.add(Button.success("steal2tg_" + p2.getFaction(), "Steal 2tg from " + ButtonHelper.getIdentOrColor(p2, game)));
                                    buttons.add(Button.primary("steal3comm_" + p2.getFaction(), "Steal 3 comms from " + ButtonHelper.getIdentOrColor(p2, game)));
                                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation(true, true) + " use buttons to resolve",
                                        buttons);
                                }
                            }
                        }
                    } else {
                        String sb = "A trap has been removed from planet: " + representation;
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb);
                    }

                    return;
                }
            }
        } else {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                player.getRepresentation(true, true) + " could not find a trap for the planet " + Helper.getPlanetRepresentation(planetName, game));

        }
    }

    public void steal2Tg(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        int count = Math.min(p2.getTg(), 2);
        p2.setTg(p2.getTg() - count);
        player.setTg(player.getTg() + count);
        String msg1 = p2.getRepresentation(true, true) + " you had " + count + " tgs stolen by a trap";
        String msg2 = player.getRepresentation(true, true) + " you stole " + count + " tgs via trap";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, game), msg1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        event.getMessage().delete().queue();
    }

    public void steal3Comm(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        int count = Math.min(p2.getCommodities(), 3);
        p2.setCommodities(p2.getCommodities() - count);
        player.setTg(player.getTg() + count);
        String msg1 = p2.getRepresentation(true, true) + " you had " + count + " comms stolen by a trap";
        String msg2 = player.getRepresentation(true, true) + " you stole " + count + " comms via trap";
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, game), msg1);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        event.getMessage().delete().queue();
    }
}
