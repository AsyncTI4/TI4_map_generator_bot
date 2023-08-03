package ti4.commands.planet;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.BotLogger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

public abstract class PlanetAddRemove extends PlanetSubcommandData {
    public PlanetAddRemove(String id, String description) {
        super(id, description);
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET2, "2nd Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET3, "3rd Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET4, "4th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET5, "5th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET6, "6th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        ArrayList<OptionMapping> planetOptions = new ArrayList<>();
        planetOptions.add(event.getOption(Constants.PLANET));
        planetOptions.add(event.getOption(Constants.PLANET2));
        planetOptions.add(event.getOption(Constants.PLANET3));
        planetOptions.add(event.getOption(Constants.PLANET4));
        planetOptions.add(event.getOption(Constants.PLANET5));
        planetOptions.add(event.getOption(Constants.PLANET6));

        LinkedHashSet<String> planetIDs = new LinkedHashSet<>(planetOptions.stream().filter(Objects::nonNull).map(p -> p.getAsString()).map(s -> AliasHandler.resolvePlanet(StringUtils.substringBefore(s, " (").replace(" ", ""))).toList());

        sendMessage(getActionHeaderMessage(activeMap, player) + resolveSpendAs(event, planetIDs) + ":");

        for (String planetID : planetIDs) {
            parseParameter(event, player, planetID, activeMap);
        }
    }

    private void parseParameter(SlashCommandInteractionEvent event, Player player, String planetID, Map activeMap) {
        try {
            if (Mapper.isValidPlanet(planetID)) {
                doAction(player, planetID, activeMap);
                sendMessage("> " + resolvePlanetMessage(planetID));
            } else {
                Set<String> planets = activeMap.getPlanets();
                List<String> possiblePlanets = planets.stream().filter(value -> value.toLowerCase().contains(planetID)).toList();
                if (possiblePlanets.isEmpty()){
                    sendMessage("> No matching Planet '" + planetID + "'' found - please try again.");
                    return;
                } else if (possiblePlanets.size() > 1) {
                    sendMessage("> More than one Planet matching '" + planetID + "'' found: " + possiblePlanets + " - please try again.");
                    return;
                }
                String planet = possiblePlanets.get(0);
                BotLogger.log(event, "`PlanetAddRemove.parseParameter - " + getActionID() + " - isValidPlanet(" + planetID + ") = false` - attempting to use planet: " + planet);
                doAction(player, planet, activeMap);
                sendMessage("> " + resolvePlanetMessage(planet));
            }
        } catch (Exception e) {
            BotLogger.log(event, "Error parsing planet: " + planetID);
            BotLogger.log(ExceptionUtils.getStackTrace(e));
        }
    }

    public abstract void doAction(Player player, String techID, Map activeMap);

    /** Customize the initial header response depending on ActionID (which /player planet_* action is used)
     * @param activeMap
     * @param player
     * @return
     */
    private String getActionHeaderMessage(Map activeMap, Player player) {
        StringBuilder message = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap)).append(" ");
        return switch (getActionID()) {
            case Constants.PLANET_ADD -> message.append(" added planet(s)").toString();
            case Constants.PLANET_REMOVE -> message.append(" removed planet(s)").toString();
            case Constants.PLANET_EXHAUST -> message.append(" exhausted planet(s)").toString();
            case Constants.PLANET_REFRESH -> message.append(" readied planet(s)").toString();
            case Constants.PLANET_EXHAUST_ABILITY -> message.append(" exhausted the legendary ability").toString();
            case Constants.PLANET_REFRESH_ABILITY -> message.append(" readied the legendary ability").toString();
            default -> "";
        };
    }

    /** Customize the message depending on ActionID and planet name
     * @param planet
     * @return special message depending on which action was used and which planet was targeted
     */
    private String resolvePlanetMessage(String planet) {
        // System.out.println("resolving " + getActionID() + " message for " + planet);
        if (getActionID().equals(Constants.PLANET_EXHAUST_ABILITY)) {
            return switch (planet) {
                case "hopesend" ->  Emojis.HopesEnd + Emojis.LegendaryPlanet + " **Imperial Arms Vault**: You may exhaust this card at the end of your turn to place 1 mech from your reinforcements on any planet you control, or draw 1 action card";
                case "primor" ->  Emojis.Primor + Emojis.LegendaryPlanet + " **The Atrament**: You may exhaust this card at the end of your turn to place up to 2 infantry from your reinforcements on any planet you control";
                case "mallice" ->  Emojis.Mallice + Emojis.LegendaryPlanet + " **Exterrix Headquarters**: You may exhaust this card at the end of your turn to gain 2 trade goods or convert all of your commodities into trade goods";
                case "mirage" ->  Emojis.Mirage + Emojis.LegendaryPlanet + " **Mirage Flight Academy**: You may exhaust this card at the end of your turn to place up to 2 fighters from your reinforcements in any system that contains 1 or more of your ships";
                default -> Emojis.planet + " " + planet;
            };
        } else if (getActionID().equals(Constants.PLANET_REFRESH_ABILITY)) {
            return switch (planet) {
                case "hopesend" ->  Emojis.HopesEnd + Emojis.LegendaryPlanet + " **Imperial Arms Vault**";
                case "primor" ->  Emojis.Primor + Emojis.LegendaryPlanet + " **The Atrament**";
                case "mallice" ->  Emojis.Mallice + Emojis.LegendaryPlanet + " **Exterrix Headquarters**";
                case "mirage" ->  Emojis.Mirage + Emojis.LegendaryPlanet + " **Mirage Flight Academy**";
                default -> Emojis.planet + " " + planet;
            };
        } else {
            return Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, getActiveMap());
        }
    }

    /** Customize the message to add what planets were exhausted for
     * @param event - if "spend_as" option is used for "planet_exhaust"
     * @return message describing what the planets were exhausted for
     */
    private String resolveSpendAs(SlashCommandInteractionEvent event, LinkedHashSet<String> planetIDs) {
        OptionMapping option = event.getOption(Constants.SPEND_AS);
        if (option != null) {
            StringBuilder message = new StringBuilder(", spent as ");
            String spendAs = option.getAsString();
            int sum = 0;
            switch (spendAs.toLowerCase()) {
                case "r", "resources" -> {
                    for (String planetID : planetIDs) {
                        sum += Helper.getPlanetResources(planetID, getActiveMap());
                    }
                    message.append(Helper.getResourceEmoji(sum) + " resources").toString();
                }
                case "i", "influence" -> {
                    for (String planetID : planetIDs) {
                        sum += Helper.getPlanetInfluence(planetID, getActiveMap());
                    }
                    message.append(Helper.getInfluenceEmoji(sum) + " influence").toString();
                }
                case "v", "votes" -> {
                    message.append(" votes").toString();
                }
                case "t", "techskip" -> message.append(" a tech skip").toString();
                case "toes" -> { //For HolyT
                    for (String planetID : planetIDs) {
                        sum += Helper.getPlanetInfluence(planetID, getActiveMap());
                    }
                    message.append(Helper.getToesEmoji(sum) + " toes").toString();
                }
                default -> message.append(spendAs).toString();
            };
            return message.toString();
        }
        return "";
    }
}
