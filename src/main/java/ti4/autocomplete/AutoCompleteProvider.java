package ti4.autocomplete;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.AsyncTI4DiscordBot;
import ti4.MessageListener;
import ti4.commands.franken.StartFrankenDraft.FrankenDraftMode;
import ti4.commands.game.Undo;
import ti4.commands.map.Preset;
import ti4.commands.player.ChangeUnitDecal;
import ti4.commands.statistics.GameStats.GameStatistics;
import ti4.commands.statistics.PlayerStats;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.GlobalSettings;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.model.AbilityModel;
import ti4.model.BorderAnomalyModel;
import ti4.model.DeckModel;
import ti4.model.ExploreModel;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.MapTemplateModel;
import ti4.model.PlanetTypeModel;
import ti4.model.PromissoryNoteModel;
import ti4.model.PublicObjectiveModel;
import ti4.model.RelicModel;
import ti4.model.ShipPositionModel;
import ti4.model.Source;
import ti4.model.Source.ComponentSource;
import ti4.model.StrategyCardSetModel;
import ti4.model.TechSpecialtyModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.model.WormholeModel;

public class AutoCompleteProvider {

    public static void autoCompleteListener(CommandAutoCompleteInteractionEvent event) {
        // List<OptionMapping> allOptions = event.getOptions();
        //String fullCommandName = event.getFullCommandName();
        String commandName = event.getName();
        //String subCommandGroupName = event.getSubcommandGroup();
        String subCommandName = event.getSubcommandName();
        String optionName = event.getFocusedOption().getName();

        //boolean showAllChoicesInGame = false;
        //OptionMapping factionOrColorOption = event.getOption(Constants.FACTION_COLOR);
        //if (factionOrColorOption != null) showAllChoicesInGame = true;

        String userID = event.getUser().getId();
        MessageListener.setActiveGame(event.getMessageChannel(), userID, event.getName(), event.getSubcommandName());
        Game game = GameManager.getInstance().getUserActiveGame(userID);
        Player player = null;
        if (game != null) {
            player = game.getPlayer(userID);
            player = Helper.getGamePlayer(game, player, event, null);
        }

        // VERY SPECIFIC HANDLING OF OPTIONS
        switch (commandName) {
            case Constants.DEVELOPER -> resolveDeveloperCommandAutoComplete(event, subCommandName, optionName);
            case Constants.DS_COMMAND -> resolveDiscordantStarsCommandAutoComplete(event, subCommandName, optionName);
            case Constants.SEARCH -> resolveSearchCommandAutoComplete(event, subCommandName, optionName);
            case Constants.CARDS_AC -> resolveActionCardAutoComplete(event, subCommandName, optionName, game);
            case Constants.FRANKEN -> resolveFrankenAutoComplete(event, subCommandName, optionName, game);
            case Constants.MAP -> resolveMapAutoComplete(event, subCommandName, optionName, game);
            case Constants.EVENT -> resolveEventAutoComplete(event, subCommandName, optionName, game, player);
            case Constants.EXPLORE -> resolveExploreAutoComplete(event, subCommandName, optionName, game);
        }

        // DON'T APPLY GENERIC HANDLING IF SPECIFIC HANDLING WAS APPLIED
        if (event.isAcknowledged()) return;

        // GENERIC HANDLING OF OPTIONS
        switch (optionName) {
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
                List<FactionModel> factions = Mapper.getFactions();
                List<Command.Choice> options;
                options = factions.stream()
                    .filter(faction -> faction.search(enteredValue))
                    .limit(25)
                    .map(faction -> new Command.Choice(faction.getAutoCompleteName(), faction.getAlias()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.FACTION_COLOR, Constants.FACTION_COLOR_1, Constants.FACTION_COLOR_2 -> {
                if (game == null) {
                    event.replyChoiceStrings("No game found in this channel").queue();
                    break;
                }
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                if (game.isFoWMode()) {
                    List<String> factionColors = new ArrayList<>(Mapper.getFactionIDs());
                    factionColors.addAll(Mapper.getColors());

                    List<String> factionColorsRetain = new ArrayList<>();
                    boolean privateGame = FoWHelper.isPrivateGame(game, null, event.getChannel());
                    for (Player player_ : game.getPlayers().values()) {
                        if (!privateGame) {
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
                    List<Command.Choice> options = game.getPlayers().values().stream()
                        .filter(p -> p.getAutoCompleteRepresentation().toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(p -> new Command.Choice(p.getAutoCompleteRepresentation(), p.getColor()))
                        .toList();
                    event.replyChoices(options).queue();
                }
            }
            case Constants.CC_USE -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<String> values = Arrays.asList("t/tactics", "r/retreat/reinforcements", "no");
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

                List<String> tableRelics = new ArrayList<>();
                if (game != null) {
                    for (Player player_ : game.getPlayers().values()) {
                        List<String> playerRelics = player_.getRelics();
                        tableRelics.addAll(playerRelics);
                    }
                    List<String> relicDeck = game.getAllRelics();
                    tableRelics.addAll(relicDeck);
                    Collections.shuffle(tableRelics);
                }

                List<Command.Choice> options = tableRelics.stream()
                    .filter(value -> value.toLowerCase().contains(enteredValue))
                    .limit(25)
                    .map(value -> new Command.Choice(value, value))
                    .collect(Collectors.toList());

                event.replyChoices(options).queue();
            }
            case Constants.RELIC_ALL -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, RelicModel> relics = Mapper.getRelics();

                List<Command.Choice> options = relics.entrySet().stream()
                    .filter(value -> value.getValue().getName().toLowerCase().contains(enteredValue))
                    .limit(25)
                    .map(value -> new Command.Choice(value.getValue().getName(), value.getKey()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.PO_ID -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, PublicObjectiveModel> publicObjectives = Mapper.getPublicObjectives();
                List<Command.Choice> options = publicObjectives.entrySet().stream()
                    .filter(value -> value.getValue().getName().toLowerCase().contains(enteredValue))
                    .limit(25)
                    .map(value -> new Command.Choice(value.getValue().getName(), value.getKey()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.SO_ID -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, String> actionCards = Mapper.getSecretObjectivesJustNamesAndSource();
                List<Command.Choice> options = actionCards.entrySet().stream()
                    .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                    .limit(25)
                    .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.AGENDA_ID -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                switch (subCommandName) {
                    case Constants.REVEAL_SPECIFIC -> {
                        List<Command.Choice> options = Mapper.getAgendas().entrySet().stream()
                            .filter(value -> value.getValue().getName().toLowerCase().contains(enteredValue) || value.getValue().getAlias().toLowerCase().contains(enteredValue))
                            .limit(25)
                            .map(value -> new Command.Choice(value.getValue().getName() + " (" + value.getValue().getSource() + ")", value.getKey()))
                            .collect(Collectors.toList());
                        event.replyChoices(options).queue();
                    }
                    case Constants.DISCARD_SPECIFIC_AGENDA -> {
                        List<Command.Choice> options = Mapper.getAgendas().entrySet().stream()
                            .filter(value -> value.getValue().getName().toLowerCase().contains(enteredValue) || value.getValue().getAlias().toLowerCase().contains(enteredValue))
                            .limit(25)
                            .map(value -> new Command.Choice(value.getValue().getName() + " (" + value.getValue().getSource() + ")", value.getKey()))
                            .collect(Collectors.toList());
                        event.replyChoices(options).queue();
                    }
                    default -> {
                        Map<String, String> agendas = Mapper.getAgendaJustNames(game);
                        List<Command.Choice> options = agendas.entrySet().stream()
                            .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                            .limit(25)
                            .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                            .collect(Collectors.toList());
                        event.replyChoices(options).queue();
                    }
                }
            }
            case Constants.AC_ID -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, String> actionCards = Mapper.getACJustNames();
                List<Command.Choice> options = actionCards.entrySet().stream()
                    .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                    .limit(25)
                    .map(value -> new Command.Choice(value.getValue(), value.getKey()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.PROMISSORY_NOTE_ID -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, PromissoryNoteModel> PNs = Mapper.getPromissoryNotes();
                List<Command.Choice> options = PNs.values().stream()
                    .filter(pn -> (pn.getAlias() + " " + pn.getName() + " " + pn.getOwner()).toLowerCase().contains(enteredValue))
                    .limit(25)
                    .map(pn -> new Command.Choice(pn.getAlias() + " " + pn.getName() + " " + pn.getOwner(), pn.getAlias()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.UNIT_ID, Constants.UNIT_ID_1, Constants.UNIT_ID_2, Constants.UNIT_ID_3, Constants.UNIT_ID_4, Constants.UNIT_ID_5 -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, UnitModel> units = Mapper.getUnits();
                List<Command.Choice> options = units.values().stream()
                    .filter(unit -> (unit.getId() + " " + unit.getName()).toLowerCase().contains(enteredValue))
                    .limit(25)
                    .map(unit -> new Command.Choice(unit.getId() + " (" + unit.getName() + ")", unit.getId()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.LEADER, Constants.LEADER_1, Constants.LEADER_2, Constants.LEADER_3, Constants.LEADER_4 -> {
                List<String> leaderIDs = new ArrayList<>();
                if (game == null || game.isFoWMode() || Constants.LEADER_ADD.equals(event.getSubcommandName())) {
                    leaderIDs.addAll(Mapper.getLeaders().keySet());
                } else {
                    leaderIDs.addAll(List.of("agent", "commander", "hero"));
                    for (Player player_ : game.getPlayers().values()) {
                        leaderIDs.addAll(player_.getLeaderIDs());
                    }
                }

                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                List<Command.Choice> options = leaderIDs.stream()
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
                Map<String, TechnologyModel> techs = Mapper.getTechs().entrySet().stream()
                    .filter(entry -> game == null || game.getTechnologyDeck().contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                List<Command.Choice> options = techs.entrySet().stream()
                    .filter(value -> value.getValue().getName().toLowerCase().contains(enteredValue))
                    .limit(25)
                    .map(value -> new Command.Choice(value.getValue().getName() + " (" + value.getValue().getSource() + ")", value.getKey()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.PLANET, Constants.PLANET2, Constants.PLANET3, Constants.PLANET4, Constants.PLANET5, Constants.PLANET6 -> {
                MessageListener.setActiveGame(event.getMessageChannel(), event.getUser().getId(), event.getName(), event.getSubcommandName());
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Set<String> planetIDs;
                Map<String, String> planets = Mapper.getPlanetRepresentations();
                if (game != null && !game.isFoWMode()) {
                    planetIDs = game.getPlanets();
                    List<Command.Choice> options = planets.entrySet().stream()
                        .filter(value -> value.getValue().toLowerCase().contains(enteredValue))
                        .filter(value -> planetIDs.isEmpty() || planetIDs.contains(value.getKey()))
                        .limit(25)
                        .map(value -> new Command.Choice(
                            value.getValue() + " (" + Helper.getPlanetResources(value.getKey(), game) + "/" + Helper.getPlanetInfluence(value.getKey(), game) + ")", value.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                } else if (game != null && game.isFoWMode()) {
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
            case Constants.DECAL_SET -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Mapper.getDecals().stream()
                    .filter(value -> value.contains(enteredValue) || Mapper.getDecalName(value).toLowerCase().contains(enteredValue))
                    .filter(decalID -> ChangeUnitDecal.userMayUseDecal(userID, decalID))
                    .limit(25)
                    .map(value -> new Command.Choice(Mapper.getDecalName(value), value))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.TTPG_FILE_NAME -> {
                String enteredValue = event.getFocusedOption().getValue();
                File exportDirectory = Storage.getTTPGExportDirectory();
                String dir = exportDirectory == null ? "/" : exportDirectory.getPath();

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
            case Constants.CATEGORY -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Category> categories = new ArrayList<>();
                for (Guild guild : AsyncTI4DiscordBot.guilds) {
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
            case Constants.TEXT_SIZE -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("tiny", "small", "medium", "large")
                    .filter(value -> value.contains(enteredValue))
                    .limit(25)
                    .map(value -> new Command.Choice(value, value))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.TECH_TYPE -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("cybernetic", "biotic", "warfare", "propulsion")
                    .filter(value -> value.contains(enteredValue))
                    .limit(25)
                    .map(value -> new Command.Choice(value, value))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.SPECIFIC_PHASE -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of("strategy", "voting", "statusScoring", "statusHomework", "action", "agendaResolve", "playerSetup", "ixthian")
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

                Map<String, AbilityModel> abilities = new HashMap<>();
                try {
                    if (player != null && event.getSubcommandName().equals(Constants.ABILITY_REMOVE)) {
                        for (String abilityID : player.getAbilities()) {
                            abilities.put(abilityID, Mapper.getAbilities().get(abilityID));
                        }
                    } else if (player != null && event.getSubcommandName().equals(Constants.ABILITY_ADD)) {
                        abilities = Mapper.getAbilities();
                        for (String abilityID : player.getAbilities()) {
                            abilities.remove(abilityID);
                        }
                    } else {
                        abilities = Mapper.getAbilities();
                    }
                } catch (Exception e) {
                    BotLogger.log(event, "Ability Autocomplete Setup Error", e);
                    abilities = Mapper.getAbilities();
                }

                List<Command.Choice> options = abilities.entrySet().stream()
                    .filter(value -> value.getValue().search(enteredValue))
                    .limit(25)
                    .map(value -> new Command.Choice(value.getValue().getAutoCompleteName(), value.getKey()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();

            }
            case Constants.LATEST_COMMAND -> {
                if (game == null) {
                    event.replyChoiceStrings("No Active Map for this Channel").queue();
                    return;
                }
                String latestCommand;
                if (game.isFoWMode()) { //!event.getUser().getID().equals(activeMap.getGMID()); //TODO: Validate that the user running the command is the FoW GM, if so, display command.
                    latestCommand = "Game is Fog of War mode - last command is hidden.";
                } else {
                    latestCommand = StringUtils.left(game.getLatestCommand(), 100);
                }
                event.replyChoice(latestCommand, Constants.LATEST_COMMAND).queue();
            }
            case Constants.UNDO_TO_BEFORE_COMMAND -> {
                if (game == null) {
                    event.replyChoiceStrings("No Active Map for this Channel").queue();
                    return;
                }
                if (game.isFoWMode()) {
                    event.replyChoiceStrings("Game is Fog of War mode - you can't see what you are undoing.").queue();
                }
                long datetime = new Date().getTime();
                List<Command.Choice> options = Undo.getAllUndoSavedGames(game).entrySet().stream()
                    .sorted(Map.Entry.<String, Game>comparingByValue(Comparator.comparing(Game::getLastModifiedDate)).reversed())
                    .limit(25)
                    .map(entry -> new Command.Choice(
                        StringUtils.left(
                            entry.getKey() + " (" + Helper.getTimeRepresentationToSeconds(datetime - entry.getValue().getLastModifiedDate()) + " ago):  " + entry.getValue().getLatestCommand(), 100),
                        entry.getKey()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.TILE_NAME, Constants.TILE_NAME_FROM, Constants.TILE_NAME_TO, Constants.HS_TILE_POSITION -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                if (game == null) {
                    event.replyChoiceStrings("No Active Map for this Channel").queue();
                    return;
                }
                if (game.isFoWMode()) {
                    List<String> positions = new ArrayList<>(game.getTileMap().keySet());
                    List<Command.Choice> options = positions.stream()
                        .filter(value -> value.toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                } else {
                    List<Command.Choice> options = game.getTileNameAutocompleteOptionsCache().stream()
                        .filter(value -> value.getKey().toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value.getKey(), value.getValue()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.DECK_NAME -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, DeckModel> decks = Mapper.getDecks();
                List<Command.Choice> options = decks.values().stream()
                    .filter(value -> value.getAlias().contains(enteredValue))
                    .map((deck) -> new Command.Choice(deck.getName(), deck.getAlias()))
                    .limit(25)
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.AC_DECK -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, DeckModel> decks = Mapper.getDecks();
                List<Command.Choice> options = decks.values().stream()
                    .filter(deckModel -> deckModel.getType().equals(Constants.ACTION_CARD))
                    .filter(value -> value.getAlias().contains(enteredValue))
                    .map((deck) -> new Command.Choice(deck.getName(), deck.getAlias()))
                    .limit(25)
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.SO_DECK -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, DeckModel> decks = Mapper.getDecks();
                List<Command.Choice> options = decks.values().stream()
                    .filter(deckModel -> deckModel.getType().equals(Constants.SECRET_OBJECTIVE))
                    .filter(value -> value.getAlias().contains(enteredValue))
                    .map((deck) -> new Command.Choice(deck.getName(), deck.getAlias()))
                    .limit(25)
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.STAGE_1_PUBLIC_DECK -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, DeckModel> decks = Mapper.getDecks();
                List<Command.Choice> options = decks.values().stream()
                    .filter(deckModel -> deckModel.getType().equals(Constants.STAGE_1_PUBLIC))
                    .filter(value -> value.getAlias().contains(enteredValue))
                    .map((deck) -> new Command.Choice(deck.getName(), deck.getAlias()))
                    .limit(25)
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.STAGE_2_PUBLIC_DECK -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, DeckModel> decks = Mapper.getDecks();
                List<Command.Choice> options = decks.values().stream()
                    .filter(deckModel -> deckModel.getType().equals(Constants.STAGE_2_PUBLIC))
                    .filter(value -> value.getAlias().contains(enteredValue))
                    .map((deck) -> new Command.Choice(deck.getName(), deck.getAlias()))
                    .limit(25)
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.RELIC_DECK -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, DeckModel> decks = Mapper.getDecks();
                List<Command.Choice> options = decks.values().stream()
                    .filter(deckModel -> deckModel.getType().equals(Constants.RELIC))
                    .filter(value -> value.getAlias().contains(enteredValue))
                    .map((deck) -> new Command.Choice(deck.getName(), deck.getAlias()))
                    .limit(25)
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.AGENDA_DECK -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, DeckModel> decks = Mapper.getDecks();
                List<Command.Choice> options = decks.values().stream()
                    .filter(deckModel -> deckModel.getType().equals(Constants.AGENDA))
                    .filter(value -> value.getAlias().contains(enteredValue))
                    .map((deck) -> new Command.Choice(deck.getName(), deck.getAlias()))
                    .limit(25)
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.EVENT_DECK -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, DeckModel> decks = Mapper.getDecks();
                List<Command.Choice> options = decks.values().stream()
                    .filter(deckModel -> deckModel.getType().equals(Constants.EVENT))
                    .filter(value -> value.getAlias().contains(enteredValue))
                    .map((deck) -> new Command.Choice(deck.getName(), deck.getAlias()))
                    .limit(25)
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.EXPLORATION_DECKS -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, DeckModel> decks = Mapper.getDecks();
                List<Command.Choice> options = decks.values().stream()
                    .filter(deckModel -> deckModel.getType().equals(Constants.EXPLORE))
                    .filter(value -> value.getAlias().contains(enteredValue))
                    .map((deck) -> new Command.Choice(deck.getName(), deck.getAlias()))
                    .limit(25)
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.TECHNOLOGY_DECK -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, DeckModel> decks = Mapper.getDecks();
                List<Command.Choice> options = decks.values().stream()
                    .filter(deckModel -> deckModel.getType().equals(Constants.TECHNOLOGY))
                    .filter(value -> value.getAlias().contains(enteredValue))
                    .map((deck) -> new Command.Choice(deck.getName(), deck.getAlias()))
                    .limit(25)
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.STRATEGY_CARD_SET -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, StrategyCardSetModel> decks = Mapper.getStrategyCardSets();
                List<Command.Choice> options = decks.values().stream()
                    .filter(scSet -> !"template".equals(scSet.getAlias()))
                    .filter(value -> value.getAlias().contains(enteredValue))
                    .map((scSet) -> new Command.Choice(scSet.getName(), scSet.getAlias()))
                    .limit(25)
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.BORDER_TYPE -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, String> anomalies = Arrays.stream(BorderAnomalyModel.BorderAnomalyType.values()) //Search string:name
                    .filter(anomalyType -> anomalyType != BorderAnomalyModel.BorderAnomalyType.ARROW)
                    .collect(Collectors.toMap(BorderAnomalyModel.BorderAnomalyType::toSearchString,
                        BorderAnomalyModel.BorderAnomalyType::getName));
                List<Command.Choice> options = anomalies.entrySet().stream()
                    .filter(anomaly -> anomaly.getValue().contains(enteredValue))
                    .map(anomaly -> new Command.Choice(anomaly.getValue(), anomaly.getKey()))
                    .limit(25)
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.VERBOSITY -> event.replyChoiceStrings(Constants.VERBOSITY_OPTIONS).queue();
            case Constants.AUTO_ARCHIVE_DURATION -> event.replyChoiceStrings("1_HOUR", "24_HOURS", "3_DAYS", "1_WEEK").queue();
            case Constants.PLANET_TYPE -> {
                List<String> allPlanetTypes = Arrays.stream(PlanetTypeModel.PlanetType.values())
                    .map(PlanetTypeModel.PlanetType::toString)
                    .toList();
                event.replyChoiceStrings(allPlanetTypes).queue();
            }
            case Constants.PLANET_TECH_SKIPS -> {
                List<String> allTechSkips = Arrays.stream(TechSpecialtyModel.TechSpecialty.values())
                    .map(TechSpecialtyModel.TechSpecialty::toString)
                    .toList();
                event.replyChoiceStrings(allTechSkips).queue();
            }
            case Constants.TILE_TYPE -> {
                List<String> allTileTypes = Arrays.stream(ShipPositionModel.ShipPosition.values())
                    .map(ShipPositionModel.ShipPosition::getTypeString)
                    .toList();
                event.replyChoiceStrings(allTileTypes).queue();
            }
            case Constants.TILE_WORMHOLES -> {
                List<String> allWormholeTypes = Arrays.stream(WormholeModel.Wormhole.values())
                    .limit(25)
                    .map(WormholeModel.Wormhole::toString)
                    .toList();
                event.replyChoiceStrings(allWormholeTypes).queue();
            }
            case Constants.ADD_REMOVE -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<String> values = Arrays.asList("add", "remove");
                List<Command.Choice> options = values.stream()
                    .filter(token -> token.contains(enteredValue))
                    .limit(25)
                    .map(token -> new Command.Choice(token, token))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.UNIT_SOURCE -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Mapper.getUnitSources().stream()
                    .filter(token -> token.contains(enteredValue))
                    .limit(25)
                    .map(token -> new Command.Choice(token, token))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.SOURCE -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = Stream.of(Source.ComponentSource.values())
                    .filter(token -> token.toString().contains(enteredValue))
                    .limit(25)
                    .map(token -> new Command.Choice(token.toString(), token.toString()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.GAME_NAME -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<Command.Choice> options = GameManager.getInstance().getGameNames().stream()
                    .filter(token -> token.contains(enteredValue))
                    .limit(25)
                    .map(token -> new Command.Choice(token, token))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.GAME_STATISTIC -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<GameStatistics> stats = Arrays.asList(GameStatistics.values());
                List<Command.Choice> options = stats.stream()
                    .filter(stat -> stat.search(enteredValue))
                    .limit(25)
                    .sorted(Comparator.comparing(GameStatistics::getAutoCompleteName))
                    .map(stat -> new Command.Choice(stat.getAutoCompleteName(), stat.toString()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.PLAYER_STATISTIC -> {
                String enteredValue = event.getFocusedOption().getValue();
                List<PlayerStats.PlayerStatistics> stats = Arrays.asList(PlayerStats.PlayerStatistics.values());
                List<Command.Choice> options = stats.stream()
                    .filter(stat -> stat.search(enteredValue))
                    .limit(25)
                    .sorted(Comparator.comparing(PlayerStats.PlayerStatistics::getAutoCompleteName))
                    .map(stat -> new Command.Choice(stat.getAutoCompleteName(), stat.toString()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.USE_MAP_TEMPLATE, Constants.MAP_TEMPLATE -> {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                List<MapTemplateModel> templates = Mapper.getMapTemplates();
                List<Command.Choice> options = templates.stream()
                    .filter(tmp -> tmp.autoCompleteString().toLowerCase().contains(enteredValue))
                    .limit(25)
                    .map(tmp -> new Command.Choice(tmp.autoCompleteString(), tmp.getAlias()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
            case Constants.DRAFT_MODE -> {
              String enteredValue = event.getFocusedOption().getValue();
              List<FrankenDraftMode> modes = Arrays.asList(FrankenDraftMode.values());
              List<Command.Choice> options = modes.stream()
                  .filter(mode -> mode.search(enteredValue))
                  .limit(25)
                  .sorted(Comparator.comparing(FrankenDraftMode::getAutoCompleteName))
                  .map(mode -> new Command.Choice(mode.getAutoCompleteName(), mode.toString()))
                  .collect(Collectors.toList());
              event.replyChoices(options).queue();
          }
        }
    }

    private static void resolveActionCardAutoComplete(CommandAutoCompleteInteractionEvent event, String subCommandName, String optionName, Game game) {
        switch (subCommandName) {
            case Constants.PICK_AC_FROM_DISCARD, Constants.SHUFFLE_AC_BACK_INTO_DECK -> {
                if (optionName.equals(Constants.ACTION_CARD_ID)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    Map<String, Integer> discardActionCardIDs = game.getDiscardActionCards();
                    List<Command.Choice> options = discardActionCardIDs.entrySet().stream()
                        .map(entry -> Map.entry(Mapper.getActionCard(entry.getKey()), entry.getValue()))
                        .filter(entry -> entry.getKey().getName().toLowerCase().contains(enteredValue))
                        .limit(25)
                        .map(entry -> new Command.Choice(
                            entry.getKey().getName() + " (" + entry.getValue() + ")",
                            entry.getValue()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
        }
    }

    private static void resolveDeveloperCommandAutoComplete(CommandAutoCompleteInteractionEvent event, String subCommandName, String optionName) {
        if (subCommandName.equals(Constants.SET_SETTING)) {
            switch (optionName) {
                case Constants.SETTING_TYPE -> event.replyChoiceStrings("string", "number", "bool").queue();
                case Constants.SETTING_NAME -> {
                    String enteredValue = event.getFocusedOption().getValue();
                    List<GlobalSettings.ImplementedSettings> settings = new ArrayList<>(
                        List.of(GlobalSettings.ImplementedSettings.values()));
                    List<Command.Choice> options = settings.stream()
                        .map(GlobalSettings.ImplementedSettings::toString)
                        .filter(setting -> setting.contains(enteredValue))
                        .limit(25)
                        .map(setting -> new Command.Choice(setting, setting))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
        }
    }

    private static void resolveDiscordantStarsCommandAutoComplete(CommandAutoCompleteInteractionEvent event, String subCommandName, String optionName) {
        if (subCommandName.equals(Constants.SET_POLICY)) {
            switch (optionName) {
                case Constants.SET_PEOPLE -> {
                    String enteredValue = event.getFocusedOption().getValue();
                    List<Command.Choice> options = Stream.of("Connect", "Control", "+", "-")
                        .filter(value -> value.contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
                case Constants.SET_ENVIRONMENT -> {
                    String enteredValue = event.getFocusedOption().getValue();
                    List<Command.Choice> options = Stream.of("Preserve", "Plunder", "+", "-")
                        .filter(value -> value.contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
                case Constants.SET_ECONOMY -> {
                    String enteredValue = event.getFocusedOption().getValue();
                    List<Command.Choice> options = Stream.of("Empower", "Exploit", "+", "-")
                        .filter(value -> value.contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
        }
    }

    private static void resolveSearchCommandAutoComplete(CommandAutoCompleteInteractionEvent event, String subCommandName, String optionName) {
        ComponentSource source = ComponentSource.fromString(event.getOption(Constants.SOURCE, null, OptionMapping::getAsString));
        switch (subCommandName) {
            case Constants.SEARCH_PLANETS -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = TileHelper.getAllPlanets().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_TILES -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = TileHelper.getAllTiles().values().stream()
                        .filter(value -> value.search(enteredValue, source))
                        .limit(25)
                        .map(value -> new Command.Choice("(" + value.getId() + ") " + value.getName(), value.getId()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_FACTIONS -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getFactions().stream()
                        .filter(entry -> entry.search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getAutoCompleteName(), entry.getAlias()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_LEADERS -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getLeaders().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_UNITS -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getUnits().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_TECHS -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getTechs().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_ABILITIES -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getAbilities().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_EXPLORES -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getExplores().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_RELICS -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getRelics().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_AGENDAS -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getAgendas().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_EVENTS -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getEvents().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_ACTION_CARDS -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getActionCards().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_SECRET_OBJECTIVES -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getSecretObjectives().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_PUBLIC_OBJECTIVES -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getPublicObjectives().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_PROMISSORY_NOTES -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getPromissoryNotes().entrySet().stream()
                        .filter(entry -> !entry.getValue().isDupe())
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
            case Constants.SEARCH_DECKS -> {
                if (optionName.equals(Constants.SEARCH)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = Mapper.getDecks().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue, source))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
        }
    }

    private static void resolveFrankenAutoComplete(CommandAutoCompleteInteractionEvent event, String subCommandName, String optionName, Game game) {
        switch (subCommandName) {
            case Constants.FACTION_TECH_ADD, Constants.FACTION_TECH_REMOVE -> {
                switch (optionName) {
                    case Constants.TECH, Constants.TECH2, Constants.TECH3, Constants.TECH4 -> {
                        String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                        Map<String, TechnologyModel> techs = new HashMap<>(Mapper.getTechs());
                        List<Command.Choice> options = techs.entrySet().stream()
                            .filter(entry -> entry.getValue().getFaction().isPresent())
                            .filter(entry -> entry.getValue().search(enteredValue))
                            .limit(25)
                            .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                            .collect(Collectors.toList());
                        event.replyChoices(options).queue();
                    }
                }
            }
            case Constants.LEADER_ADD, Constants.LEADER_REMOVE -> {
                switch (optionName) {
                    case Constants.LEADER, Constants.LEADER_1, Constants.LEADER_2, Constants.LEADER_3, Constants.LEADER_4 -> {
                        String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                        Map<String, LeaderModel> techs = new HashMap<>(Mapper.getLeaders());
                        List<Command.Choice> options = techs.entrySet().stream()
                            .filter(entry -> entry.getValue().search(enteredValue))
                            .limit(25)
                            .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                            .collect(Collectors.toList());
                        event.replyChoices(options).queue();
                    }
                }
            }
        }
    }

    private static void resolveMapAutoComplete(CommandAutoCompleteInteractionEvent event, String subCommandName, String optionName, Game game) {
        if (game == null) {
            event.replyChoiceStrings("No Active Map for this Channel").queue();
            return;
        }

        switch (subCommandName) {
            case Constants.ADD_TILE -> {
                if (optionName.equals(Constants.TILE_NAME)) {
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    List<Command.Choice> options = TileHelper.getAllTiles().entrySet().stream()
                        .filter(entry -> entry.getValue().search(enteredValue))
                        .limit(25)
                        .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                    // case Constants.POSITION -> {
                    //     String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                    //     List<Command.Choice> options = game.getTileMap().entrySet().stream()
                    //         .filter(entry -> entry.getValue().search(enteredValue))
                    //         .limit(25)
                    //         .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                    //         .collect(Collectors.toList());
                    //     event.replyChoices(options).queue();
                    // }
                }
            }
            case Constants.REMOVE_TILE -> {
                switch (optionName) {
                    case Constants.POSITION -> {
                        String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                        List<Command.Choice> options = game.getTileMap().entrySet().stream()
                            .filter(entry -> entry.getValue().search(enteredValue))
                            .limit(25)
                            .map(entry -> new Command.Choice(entry.getValue().getAutoCompleteName(), entry.getKey()))
                            .collect(Collectors.toList());
                        event.replyChoices(options).queue();
                    }
                }
            }
            case Constants.PRESET -> {
                if (optionName.equals(Constants.MAP_TEMPLATE)) {
                    System.out.println("Found autocomplete");
                    String enteredValue = event.getFocusedOption().getValue().toLowerCase();

                    List<Command.Choice> options = Preset.templates.stream()
                        .filter(value -> value.contains(enteredValue))
                        .limit(25)
                        .map(value -> new Command.Choice(value, value))
                        .collect(Collectors.toList());
                    event.replyChoices(options).queue();
                }
            }
        }
    }

    private static void resolveEventAutoComplete(CommandAutoCompleteInteractionEvent event, String subCommandName, String optionName, Game game, Player player) {
        if (subCommandName.equals(Constants.EVENT_PLAY)) {
            if (optionName.equals(Constants.EVENT_ID)) {
                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                Map<String, Integer> techs = new HashMap<>(player.getEvents());
                List<Command.Choice> options = techs.entrySet().stream()
                    .filter(entry -> entry.getKey().contains(enteredValue))
                    .limit(25)
                    .map(entry -> new Command.Choice(entry.getValue() + " " + entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
        }
    }

    private static void resolveExploreAutoComplete(CommandAutoCompleteInteractionEvent event, String subCommandName, String optionName, Game game) {
        if (subCommandName.equals(Constants.USE)) {
            if (optionName.equals(Constants.EXPLORE_CARD_ID)) {
                if (game.isFoWMode()) {
                    event.replyChoice("You can not see the autocomplete in Fog of War", "[error]").queue();
                    return;
                }

                String enteredValue = event.getFocusedOption().getValue().toLowerCase();
                List<String> explores = game.getAllExplores();
                List<Command.Choice> options = explores.stream()
                    .map(Mapper::getExplore)
                    .filter(e -> e.search(enteredValue))
                    .limit(25)
                    .sorted(Comparator.comparing(ExploreModel::getAutoCompleteName))
                    .map(e -> new Command.Choice(e.getAutoCompleteName(), e.getId()))
                    .collect(Collectors.toList());
                event.replyChoices(options).queue();
            }
        }
    }
}