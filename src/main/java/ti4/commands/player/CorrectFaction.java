package ti4.commands.player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;

class CorrectFaction extends GameStateSubcommand {

    public CorrectFaction() {
        super(Constants.CORRECT_FACTION, "FOR BOTHELPER USE ONLY", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "New faction")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (CommandHelper.acceptIfHasRoles(event, AsyncTI4DiscordBot.bothelperRoles)) {
            String newFaction = AliasHandler.resolveColor(
                    event.getOption(Constants.FACTION).getAsString().toLowerCase());
            newFaction = AliasHandler.resolveFaction(newFaction);
            if (!Mapper.isValidFaction(newFaction)) {
                MessageHelper.sendMessageToEventChannel(event, "Faction not valid");
                return;
            }

            changeFactionSheetAndComponents(event, game, getPlayer(), newFaction);
        }
    }

    public void changeFactionSheetAndComponents(
            GenericInteractionCreateEvent event, Game game, Player player, String newFaction) {
        Map<String, Player> players = game.getPlayers();
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (newFaction.equals(playerInfo.getFaction())) {
                    MessageHelper.sendMessageToEventChannel(
                            event, "Player:" + playerInfo.getUserName() + " already uses faction:" + newFaction);
                    return;
                }
            }
        }

        List<String> laws = new ArrayList<>(game.getLawsInfo().keySet());
        for (String law : laws) {
            if (game.getLawsInfo().get(law).equalsIgnoreCase(player.getFaction())) {
                game.reviseLaw(game.getLaws().get(law), newFaction);
            }
        }
        player.setFaction(newFaction);
        FactionModel setupInfo = player.getFactionSetupInfo();

        if (!player.getFaction().contains("franken")) {
            player.getFactionTechs().clear();
            Set<String> playerOwnedUnits = new HashSet<>(setupInfo.getUnits());
            player.setUnitsOwned(playerOwnedUnits);
            player.setCommoditiesBase(setupInfo.getCommodities());
        }

        // STARTING COMMODITIES

        for (String tech : setupInfo.getFactionTech()) {
            if (tech.trim().isEmpty()) {
                continue;
            }
            if (!player.getTechs().contains(tech)) {
                player.addFactionTech(tech);
            }
        }
        List<String> techs = new ArrayList<>(player.getTechs());
        for (String tech : techs) {
            player.removeTech(tech);
            player.addTech(tech);
        }
        player.setFactionEmoji(null);
    }
}
