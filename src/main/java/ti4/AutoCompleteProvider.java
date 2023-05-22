package ti4;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.model.PublicObjectiveModel;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

public class AutoCompleteProvider {

    private static ArrayList<String> leaders = new ArrayList<>();

    public static void autoCompleteListener(CommandAutoCompleteInteractionEvent event) {
        String optionName = event.getFocusedOption().getName();
        String userID = event.getUser().getId();
        MessageListener.setActiveGame(event.getMessageChannel(), userID, event.getName());
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        Player player = null;
        if (activeMap != null) {
            player = activeMap.getPlayer(userID);
            player = Helper.getGamePlayer(activeMap, player, event, null);
        }

        switch (optionName) {
            case Constants.SETTING_TYPE -> {
                event.replyChoiceStrings("string","number","bool").queue();
            }
            case Constants.COLOR -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Mapper.getColors().stream()
                        .filter(color -> color.startsWith(enteredValue))
                        .limit(25)
                        .map(color -> new Command.Choice(color, color))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.FACTION -> {
                String enteredValue = event.getFocusedOption().getValue();
                HashMap<String, String> factions = Mapper.getFactionRepresentations();
                if (activeMap.isDiscordantStarsMode()) {
                    List<Command.Choice> options = factions.entrySet().stream()
                            .filter(token -> token.getValue().toLowerCase().contains(enteredValue))
                            .limit(25)
                            .map(token -> new Command.Choice(token.getValue(), token.getKey()))
                            .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                } else {
                    List<Command.Choice> options = factions.entrySet().stream()
                            .filter(Predicate.not(token -> token.getValue().toUpperCase().endsWith("(DS)")))
                            .filter(token -> token.getValue().toLowerCase().contains(enteredValue))
                            .limit(25)
                            .map(token -> new Command.Choice(token.getValue(), token.getKey()))
                            .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.FACTION_COLOR, Constants.FACTION_COLOR_1, Constants.FACTION_COLOR_2 -> {
                if (activeMap == null) event.replyChoiceStrings("No game found in this channel").queue();
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                if (activeMap.isFoWMode()) {
                    List<String> factionColors = new ArrayList<>(Mapper.getFactions());
                    factionColors.addAll(Mapper.getColors());

                    List<String> factionColorsRetain = new ArrayList<>();
                    Boolean privateGame = FoWHelper.isPrivateGame(activeMap, null, event.getChannel());
                    for (Player player_ : activeMap.getPlayers().values()) {
                        if (privateGame == null || !privateGame) {
                            factionColorsRetain.add(player_.getFaction());
                        }
                        factionColorsRetain.add(player_.getColor());
                    }
                    factionColors.retainAll(factionColorsRetain);
                    List<Command.Choice> options = factionColors.stream()
                    .filter(token -> token.contains(enteredValue))
                    .limit(25)
                    .map(token -> new Command.Choice(token, token))
                    .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                } else {
                    List<Command.Choice> options = activeMap.getPlayers().values().stream()
                        .filter(p -> p.getAutoCompleteRepresentation().toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(p -> new Command.Choice(p.getAutoCompleteRepresentation(), p.getColor()))
                        .toList();
                    event.replyChoices(options).queue();
                }
            }
            case Constants.CC_USE -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<String> values = Arrays.asList("t/tactics","r/retreat/reinforcements","no");
                List<Command.Choice> options = values.stream()
                        .filter(token -> token.contains(enteredValue))
                        .limit(25)
                        .map(token -> new Command.Choice(token, token))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.TOKEN -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Mapper.getTokens().stream()
                        .filter(token -> token.contains(enteredValue))
                        .limit(25)
                        .map(token -> new Command.Choice(token, token))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.GAME_STATUS -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("open", "locked")
                        .filter(value -> value.contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.DISPLAY_TYPE -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("all", "map", "stats", "split", "none")
                        .filter(value -> value.contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.RELIC -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                HashMap<String, String> relics = Mapper.getRelics();
                if (activeMap.isAbsolMode()){
                    List<Command.Choice> options = relics.entrySet().stream()
                            .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                            .filter(value -> value.getKey().startsWith("absol_") || value.getKey().equals("enigmaticdevice"))
                            .limit(25)
                            .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                            .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                } else {
                    List<Command.Choice> options = relics.entrySet().stream()
                            .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                            .filter(Predicate.not(value -> value.getKey().startsWith("absol_")))
                            .limit(25)
                            .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                            .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.KELERES_HS -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                HashMap<String, String> keleres = Constants.KELERES_CHOICES;
                List<Command.Choice> options = keleres.entrySet().stream()
                        .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.PO_ID -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                java.util.Map<String, PublicObjectiveModel> publicObjectives = Mapper.getPublicObjectives();
                List<Command.Choice> options = publicObjectives.entrySet().stream()
                        .filter(value -> value.getValue().name.toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value.getValue().name, value.getKey()))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.SO_ID -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                HashMap<String, String> secretObjectives = Mapper.getSecretObjectivesJustNames();
                List<Command.Choice> options = secretObjectives.entrySet().stream()
                        .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.AGENDA_ID -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                HashMap<String, String> secretObjectives = Mapper.getAgendaJustNames();
                List<Command.Choice> options = secretObjectives.entrySet().stream()
                        .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.AC_ID -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                HashMap<String, String> secretObjectives = Mapper.getACJustNames();
                List<Command.Choice> options = secretObjectives.entrySet().stream()
                        .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.LEADER -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                if (leaders.isEmpty()) {
                    leaders = new ArrayList<>(Constants.leaderList);
                    HashMap<String, HashMap<String, ArrayList<String>>> leadersInfo = Mapper.getLeadersInfo();
                    for (HashMap<String, ArrayList<String>> value : leadersInfo.values()) {
                        for (ArrayList<String> leaderNames : value.values()) {
                            if (!leaderNames.isEmpty()) {
                                leaders.addAll(leaderNames);
                            }
                        }
                    }
                }
                List<Command.Choice> options = leaders.stream()
                        .filter(value -> value.toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                try {
                    event.replyChoices(options).queue();
                } catch (Exception e) {
                    BotLogger.log(event, "Could not suggest leaders", e);
                }
            }
            case Constants.TECH, Constants.TECH2, Constants.TECH3, Constants.TECH4 -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                HashMap<String, String> techs = Mapper.getTechs();
                if (activeMap.isDiscordantStarsMode()) {
                    List<Command.Choice> options = techs.entrySet().stream()
                        .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                } else {
                    List<Command.Choice> options = techs.entrySet().stream()
                        .filter(Predicate.not(value -> value.getKey().toLowerCase().startsWith("ds") && !value.getKey().equals("ds")))
                        .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.PLANET, Constants.PLANET2, Constants.PLANET3, Constants.PLANET4, Constants.PLANET5, Constants.PLANET6 -> {
                MessageListener.setActiveGame(event.getMessageChannel(), event.getUser().getId(), event.getName());
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Set<String> planetIDs;
                HashMap<String, String> planets = Mapper.getPlanetRepresentations();
                if (activeMap != null && !activeMap.isFoWMode()) {
                    planetIDs = activeMap.getPlanets();
                    List<Command.Choice> options = planets.entrySet().stream()
                            .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                            .filter(value ->  planetIDs.isEmpty() || planetIDs.contains(value.getKey()))
                            .limit(25)
                            .map(value -> new Command.Choice(value.getValue() + " (" + Helper.getPlanetResources(value.getKey(), activeMap) + "/" + Helper.getPlanetInfluence(value.getKey(), activeMap) + ")", value.getKey()))
                            .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                } else if (activeMap != null && activeMap.isFoWMode()) {
                    List<Command.Choice> options = planets.entrySet().stream()
                            .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                            .limit(25)
                            .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                            .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.TRAIT -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of(Constants.CULTURAL, Constants.INDUSTRIAL, Constants.HAZARDOUS, Constants.FRONTIER)
                        .filter(value -> value.contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.NO_MAPGEN -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("True")
                        .filter(value -> value.contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.TTPG_FILE_NAME -> {
                String enteredValue = event.getFocusedOption().getValue();
                String dir = Storage.getTTPGExportDirectory().getPath();

                Set<String> fileSet = Stream.of(new File(dir).listFiles())
                    .filter(file -> !file.isDirectory())
                    .map(File::getName)
                    .collect(Collectors.toSet());

                List<Command.Choice> options = fileSet.stream()
                        .filter(value -> value.contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.SPEND_AS -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("Resources", "Influence", "Votes", "TechSkip", "Other")
                        .filter(value -> value.toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.FOG_FILTER -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("Dark Grey (default)", "Sepia", "White", "Pink", "Purple")
                        .filter(value -> value.toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.PRIMARY_TILE_DIRECTION -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("North", "Northeast", "Southeast", "South", "Southwest", "Northwest")
                        .filter(value -> value.toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.SERVER -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("Primary", "Secondary")
                        .filter(value -> value.toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.CATEGORY -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Category> categories = new ArrayList<>();
                for (Guild guild : MapGenerator.jda.getGuilds()) {
                    categories.addAll(guild.getCategories());
                }
                List<Command.Choice> options = categories.stream()
                        .filter(c -> c.getName().toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(c -> new Command.Choice(c.getGuild().getName() + ": #" + c.getName(), c.getName()))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.ANON -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("y", "n")
                        .filter(value -> value.contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.LARGE_TEXT -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("small", "medium", "large")
                        .filter(value -> value.contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.CREUSS_TOKEN_NAME -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("alpha", "beta", "gamma")
                        .filter(value -> value.contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, "creuss" + value))
                        .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.ABILITY, Constants.ABILITY_1, Constants.ABILITY_2, Constants.ABILITY_3, Constants.ABILITY_4, Constants.ABILITY_5 -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();

                HashMap<String, String> abilities = new HashMap<>();
                try {
                    if (player != null && event.getSubcommandName().equals(Constants.ABILITY_REMOVE)) {
                        for (String abilityID : player.getFactionAbilities()) {
                            abilities.put(abilityID, Mapper.getFactionAbilities().get(abilityID));
                        }
                    } else if (player != null && event.getSubcommandName().equals(Constants.ABILITY_ADD)) {
                        abilities = Mapper.getFactionAbilities();
                        for (String abilityID : player.getFactionAbilities()) {
                            abilities.remove(abilityID);
                        }
                    } else {
                        abilities = Mapper.getFactionAbilities();
                    }
                } catch (Exception e) {
                    BotLogger.log(event, "Ability Autocomplete Setup Error", e);
                    abilities = Mapper.getFactionAbilities();
                }

                abilities.replaceAll((k, v) -> {
                    int index = v.indexOf("|");
                    String abilityName = v.substring(0, index);
                    String factionName = v.substring(index + 1, StringUtils.ordinalIndexOf(v, "|", 2));
                    factionName = Mapper.getFactionRepresentations().get(factionName);
                    return factionName + " - " + abilityName;
                });

                List<Command.Choice> options = abilities.entrySet().stream()
                    .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                    .limit(25)
                    .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();

            }
            case Constants.LATEST_COMMAND -> {
                if (activeMap == null) {
                    event.replyChoiceStrings("No Active Map for this Channel").queue();
                    return;
                }
                String latestCommand = "";
                if (activeMap.isFoWMode()) { //!event.getUser().getID().equals(activeMap.getGMID()); //TODO: Validate that the user running the command is the FoW GM, if so, display command.
                    latestCommand = "Game is Fog of War mode - last command is hidden.";
                } else {
                    latestCommand = StringUtils.left(activeMap.getLatestCommand(), 100);
                }
                event.replyChoice(latestCommand, Constants.LATEST_COMMAND).queue();
            }
            case Constants.TILE_NAME, Constants.TILE_NAME_FROM, Constants.TILE_NAME_TO, Constants.HS_TILE_POSITION -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                if (activeMap == null) {
                    event.replyChoiceStrings("No Active Map for this Channel").queue();
                    return;
                }
                if (activeMap.isFoWMode()) {
                    List<String> positions = new ArrayList<>(activeMap.getTileMap().keySet());
                    List<Command.Choice> options = positions.stream()
                        .filter(value -> value.toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                } else {
                    List<Command.Choice> options = activeMap.getTileNameAutocompleteOptionsCache().stream()
                        .filter(value -> value.getKey().toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value.getKey(), value.getValue()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.VERBOSITY -> {
                event.replyChoiceStrings(Constants.VERBOSITY_OPTIONS).queue();
            }
            case Constants.AUTO_ARCHIVE_DURATION -> {
                event.replyChoiceStrings("1_HOUR", "24_HOURS", "3_DAYS", "1_WEEK").queue();
            }
        }
    }
}
