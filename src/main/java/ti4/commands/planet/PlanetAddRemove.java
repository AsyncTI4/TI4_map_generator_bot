package ti4.commands.planet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

abstract class PlanetAddRemove extends GameStateSubcommand {

    public PlanetAddRemove(String id, String description) {
        super(id, description, true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET2, "2nd Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET3, "3rd Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET4, "4th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET5, "5th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET6, "6th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<OptionMapping> planetOptions = new ArrayList<>();
        planetOptions.add(event.getOption(Constants.PLANET));
        planetOptions.add(event.getOption(Constants.PLANET2));
        planetOptions.add(event.getOption(Constants.PLANET3));
        planetOptions.add(event.getOption(Constants.PLANET4));
        planetOptions.add(event.getOption(Constants.PLANET5));
        planetOptions.add(event.getOption(Constants.PLANET6));

        Set<String> planetIDs = new LinkedHashSet<>(planetOptions.stream().filter(Objects::nonNull).map(OptionMapping::getAsString).map(s -> AliasHandler.resolvePlanet(StringUtils.substringBefore(s, " (").replace(" ", ""))).toList());

        Player player = getPlayer();
        MessageHelper.sendMessageToEventChannel(event, getActionHeaderMessage(player) + ":");

        for (String planetID : planetIDs) {
            parseParameter(event, player, planetID, getGame());
        }
    }

    private void parseParameter(SlashCommandInteractionEvent event, Player player, String planetID, Game game) {
        try {
            if (Mapper.isValidPlanet(planetID)) {
                doAction(event, player, planetID, game);
                MessageHelper.sendMessageToEventChannel(event, "> " + resolvePlanetMessage(planetID));
            } else {
                Set<String> planets = game.getPlanets();
                List<String> possiblePlanets = planets.stream().filter(value -> value.toLowerCase().contains(planetID)).toList();
                if (possiblePlanets.isEmpty()) {
                    if (player.getPlanets().remove(planetID)) { //To remove an invalid planet from player
                        MessageHelper.sendMessageToEventChannel(event, "> Invalid planet '" + planetID + "'' removed.");
                        return;
                    }
                    MessageHelper.sendMessageToEventChannel(event, "> No matching Planet '" + planetID + "'' found - please try again.");
                    return;
                } else if (possiblePlanets.size() > 1) {
                    MessageHelper.sendMessageToEventChannel(event, "> More than one Planet matching '" + planetID + "'' found: " + possiblePlanets + " - please try again.");
                    return;
                }
                String planet = possiblePlanets.getFirst();
                BotLogger.log(event, "`PlanetAddRemove.parseParameter - " + getName() + " - isValidPlanet(" + planetID + ") = false` - attempting to use planet: " + planet);
                doAction(event, player, planet, game);
                MessageHelper.sendMessageToEventChannel(event, "> " + resolvePlanetMessage(planet));
            }
        } catch (Exception e) {
            BotLogger.log(event, "Error parsing planet: " + planetID);
            BotLogger.log(ExceptionUtils.getStackTrace(e));
        }
    }

    public abstract void doAction(GenericInteractionCreateEvent event, Player player, String techID, Game game);

    private String getActionHeaderMessage(Player player) {
        StringBuilder message = new StringBuilder(player.getRepresentation()).append(" ");
        return switch (getName()) {
            case Constants.PLANET_ADD -> message.append(" added planet(s):").toString();
            case Constants.PLANET_REMOVE -> message.append(" removed planet(s):").toString();
            case Constants.PLANET_EXHAUST -> message.append(" exhausted planet(s):").toString();
            case Constants.PLANET_REFRESH -> message.append(" readied planet(s):").toString();
            case Constants.PLANET_EXHAUST_ABILITY -> message.append(" exhausted the legendary ability").toString();
            case Constants.PLANET_REFRESH_ABILITY -> message.append(" readied the legendary ability:").toString();
            default -> "";
        };
    }

    private String resolvePlanetMessage(String planet) {
        if (getName().equals(Constants.PLANET_EXHAUST_ABILITY)) {
            return switch (planet) {
                case "hopesend" -> Emojis.HopesEnd + Emojis.LegendaryPlanet + " **Imperial Arms Vault**: You may exhaust this card at the end of your turn to place 1 mech from your reinforcements on any planet you control, or draw 1 action card";
                case "primor" -> Emojis.Primor + Emojis.LegendaryPlanet + " **The Atrament**: You may exhaust this card at the end of your turn to place up to 2 infantry from your reinforcements on any planet you control";
                case "mallice" -> Emojis.Mallice + Emojis.LegendaryPlanet + " **Exterrix Headquarters**: You may exhaust this card at the end of your turn to gain 2 trade goods or convert all of your commodities into trade goods";
                case "mirage" -> Emojis.LegendaryPlanet + " **Mirage Flight Academy**: You may exhaust this card at the end of your turn to place up to 2 fighters from your reinforcements in any system that contains 1 or more of your ships";
                default -> planet;
            };
        }
        if (getName().equals(Constants.PLANET_REFRESH_ABILITY)) {
            return switch (planet) {
                case "hopesend" -> Emojis.HopesEnd + Emojis.LegendaryPlanet + " **Imperial Arms Vault**";
                case "primor" -> Emojis.Primor + Emojis.LegendaryPlanet + " **The Atrament**";
                case "mallice" -> Emojis.Mallice + Emojis.LegendaryPlanet + " **Exterrix Headquarters**";
                case "mirage" -> Emojis.LegendaryPlanet + " **Mirage Flight Academy**";
                default -> planet;
            };
        }
        return Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, getGame());
    }
}
