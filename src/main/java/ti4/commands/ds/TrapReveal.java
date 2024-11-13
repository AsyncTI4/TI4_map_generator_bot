package ti4.commands.ds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.GameStateSubcommand;
import ti4.commands.units.RemoveUnits;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.GenericCardModel;

class TrapReveal extends GameStateSubcommand {

    public TrapReveal() {
        super(Constants.LIZHO_REVEAL_TRAP, "Select planets were to reveal trap tokens", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.LIZHO_TRAP_ID, "Trap ID").setRequired(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        String planetName = event.getOption(Constants.PLANET).getAsString();
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }

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
        revealTrapForPlanet(event, game, planetName, stringTrapID, player, true);
    }

    public static void revealTrapForPlanet(GenericInteractionCreateEvent event, Game game, String planetName, String trap, Player player, boolean reveal) {
        if (player.getTrapCardsPlanets().containsValue(planetName) || planetName == null) {
            Map<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
            for (Map.Entry<String, String> entry : trapCardsPlanets.entrySet()) {
                String planet = entry.getValue();
                if (planetName.equals(planet) || planet == null) {
                    ButtonHelperAbilities.removeATrapToken(game, planetName);
                    player.removeTrapCardPlanet(trap);
                    player.setTrapCard(trap);
                    GenericCardModel trapCard = Mapper.getTrap(trap);
                    Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
                    String representation = planetRepresentations.get(planet);
                    if (representation == null) {
                        representation = planet;
                    }
                    if (reveal && planet != null) {

                        String sb = trapCard.getRepresentation() + "\n" + "__**" + "Has been revealed on planet: " + representation + "**__";
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb);
                        if ("Minefields".equalsIgnoreCase(trapCard.getName())) {
                            for (Player p2 : game.getRealPlayers()) {
                                if (p2 == player) {
                                    continue;
                                }
                                new RemoveUnits().unitParsing(event, p2.getColor(), game.getTileFromPlanet(planet), "2 inf " + planet, game);
                            }
                            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Destroyed up to 2 enemy infantry from " + representation);
                        }
                        if ("Account Siphon".equalsIgnoreCase(trapCard.getName())) {
                            for (Player p2 : game.getRealPlayers()) {
                                if (p2 == player) {
                                    continue;
                                }
                                if (p2.getPlanets().contains(planet)) {
                                    List<Button> buttons = new ArrayList<>();
                                    buttons.add(Buttons.green("steal2tg_" + p2.getFaction(), "Steal 2TGs from " + p2.getFactionEmojiOrColor()));
                                    buttons.add(Buttons.blue("steal3comm_" + p2.getFaction(), "Steal 3 comms from " + p2.getFactionEmojiOrColor()));
                                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentationUnfogged() + " use buttons to resolve",
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
                player.getRepresentationUnfogged() + " could not find a trap for the planet " + Helper.getPlanetRepresentation(planetName, game));

        }
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
