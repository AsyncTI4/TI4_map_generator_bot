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
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;

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
        MessageHelper.sendMessageToEventChannel(event, getActionHeaderMessage(player));

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
                BotLogger.warning(new BotLogger.LogMessageOrigin(event), "`PlanetAddRemove.parseParameter - " + getName() + " - isValidPlanet(" + planetID + ") = false` - attempting to use planet: " + planet);
                doAction(event, player, planet, game);
                MessageHelper.sendMessageToEventChannel(event, "> " + resolvePlanetMessage(planet));
            }
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event, player), "Error parsing planet: " + planetID, e);
        }
    }

    public abstract void doAction(GenericInteractionCreateEvent event, Player player, String techID, Game game);

    private String getActionHeaderMessage(Player player) {
        String message = player.getRepresentation();
        return switch (getName()) {
            case Constants.PLANET_ADD -> message + " added planet(s):";
            case Constants.PLANET_REMOVE -> message + " removed planet(s):";
            case Constants.PLANET_EXHAUST -> message + " exhausted planet(s):";
            case Constants.PLANET_REFRESH -> message + " readied planet(s):";
            case Constants.PLANET_EXHAUST_ABILITY -> message + " exhausted the legendary ability:";
            case Constants.PLANET_REFRESH_ABILITY -> message + " readied the legendary ability:";
            default -> "";
        };
    }

    private String resolvePlanetMessage(String planet) {
        if (getName().equals(Constants.PLANET_EXHAUST_ABILITY)) {
            return switch (planet) {
                case "hopesend" -> PlanetEmojis.HopesEnd + "" + MiscEmojis.LegendaryPlanet + " _Imperial Arms Vault_: You may exhaust this card at the end of your turn to place 1 mech from your reinforcements on any planet you control, or draw 1 action card";
                case "primor" -> PlanetEmojis.Primor + "" + MiscEmojis.LegendaryPlanet + " _The Atrament_: You may exhaust this card at the end of your turn to place up to 2 infantry from your reinforcements on any planet you control";
                case "mallice" -> PlanetEmojis.Mallice + "" + MiscEmojis.LegendaryPlanet + " _Exterrix Headquarters_: You may exhaust this card at the end of your turn to gain 2 trade goods or convert all of your commodities into trade goods";
                case "mirage" -> MiscEmojis.LegendaryPlanet + " _Mirage Flight Academy_: You may exhaust this card at the end of your turn to place up to 2 fighters from your reinforcements in any system that contains 1 or more of your ships";
                default -> planet;
            };
        }
        if (getName().equals(Constants.PLANET_REFRESH_ABILITY)) {
            return switch (planet) {
                case "hopesend" -> PlanetEmojis.HopesEnd + "" + MiscEmojis.LegendaryPlanet + " _Imperial Arms Vault_";
                case "primor" -> PlanetEmojis.Primor + "" + MiscEmojis.LegendaryPlanet + " _The Atrament_";
                case "mallice" -> PlanetEmojis.Mallice + "" + MiscEmojis.LegendaryPlanet + " _Exterrix Headquarters_";
                case "mirage" -> MiscEmojis.LegendaryPlanet + " _Mirage Flight Academy_";
                default -> planet;
            };
        }
        return Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, getGame());
    }
}
