package ti4.discord.interactions.commands.tech;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.tech.PlayerTechService;

class TechAddRemove extends GameStateSubcommand {

    private interface TechAction {
        void apply(SlashCommandInteractionEvent event, Game game, Player player, String techID);
    }

    private final TechAction action;

    private TechAddRemove(String id, String description, boolean suspicious, TechAction action) {
        super(id, description, true, true);
        this.action = action;
        if (suspicious) markSuspicious();
        addOptions(new OptionData(OptionType.STRING, Constants.TECH, "Technology")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH2, "2nd technology").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH3, "3rd technology").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH4, "4th technology").setAutoComplete(true));
        addOptions(CommonOptions.factionColor("Faction or Color with the technology"));
    }

    static TechAddRemove add() {
        return new TechAddRemove(
                Constants.TECH_ADD,
                "Add a technology",
                true,
                (event, game, player, techID) -> PlayerTechService.addTech(event, game, player, techID));
    }

    static TechAddRemove remove() {
        return new TechAddRemove(
                Constants.TECH_REMOVE,
                "Remove a technology",
                false,
                (event, game, player, techID) -> PlayerTechService.removeTech(event, player, techID));
    }

    static TechAddRemove purge() {
        return new TechAddRemove(
                Constants.TECH_PURGE,
                "Purge a technology (permanently removing it from the game)",
                false,
                (event, game, player, techID) -> PlayerTechService.purgeTech(event, player, techID));
    }

    static TechAddRemove unpurge() {
        return new TechAddRemove(
                Constants.TECH_UNPURGE,
                "Un-purge a technology that was previously purged",
                true,
                (event, game, player, techID) -> PlayerTechService.unpurgeTech(event, player, techID));
    }

    static TechAddRemove exhaust() {
        return new TechAddRemove(
                Constants.TECH_EXHAUST, "Exhaust a technology", false, (event, game, player, techID) -> {
                    PlayerTechService.exhaustTechAndResolve(event, game, player, techID);
                    PlayerTechService.checkAndApplyCombatMods(event, player, techID);
                });
    }

    static TechAddRemove refresh() {
        return new TechAddRemove(
                Constants.TECH_REFRESH,
                "Ready a technology",
                true,
                (event, game, player, techID) -> PlayerTechService.refreshTech(event, player, techID));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();

        parseParameter(event, player, event.getOption(Constants.TECH));
        parseParameter(event, player, event.getOption(Constants.TECH2));
        parseParameter(event, player, event.getOption(Constants.TECH3));
        parseParameter(event, player, event.getOption(Constants.TECH4));

        CommanderUnlockCheckService.checkPlayer(player, "nekro", "jolnar");
    }

    private void parseParameter(SlashCommandInteractionEvent event, Player player, OptionMapping techOption) {
        if (techOption != null) {
            String techID = AliasHandler.resolveTech(techOption.getAsString());
            if (Mapper.isValidTech(techID)) {
                doAction(player, techID, event);
            } else {
                Map<String, TechnologyModel> techs = Mapper.getTechs();
                List<String> possibleTechs = techs.entrySet().stream()
                        .filter(value ->
                                value.getValue().getName().toLowerCase().contains(techID))
                        .map(Map.Entry::getKey)
                        .toList();
                List<String> mutableList = new ArrayList<>(possibleTechs);
                if (possibleTechs.isEmpty()) {
                    for (String tech : player.getTechs()) {
                        if (tech.equalsIgnoreCase(techID)) {
                            mutableList.add(techID);
                        }
                    }
                }
                if (mutableList.isEmpty()) {
                    MessageHelper.sendMessageToEventChannel(event, "No matching technology found.");
                    return;
                } else if (mutableList.size() > 1) {
                    MessageHelper.sendMessageToEventChannel(event, "More that one matching technology found.");
                    return;
                }
                doAction(player, mutableList.getFirst(), event);
            }
        }
    }

    private void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        action.apply(event, getGame(), player, techID);
    }
}
