package ti4.commands.special;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class SwordsToPlowsharesTGGain extends SpecialSubcommandData {
    public SwordsToPlowsharesTGGain() {
        super(Constants.SWORDS_TO_PLOWSHARES, "Swords to Plowshares, kill half your infantry to get that many TGs");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        doSwords(player, event, game);
    }

    public void doSwords(Player player, GenericInteractionCreateEvent event, Game game) {
        List<String> planets = player.getPlanets();
        String ident = player.getFactionEmoji();
        StringBuilder message = new StringBuilder();
        int oldTg = player.getTg();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (planets.contains(unitHolder.getName())) {
                    int numInf = 0;
                    String colorID = Mapper.getColorID(player.getColor());
                    UnitKey infKey = Mapper.getUnitKey("gf", colorID);
                    if (unitHolder.getUnits() != null) {
                        if (unitHolder.getUnits().get(infKey) != null) {
                            numInf = unitHolder.getUnits().get(infKey);
                        }
                    }
                    if (numInf > 0) {
                        int numTG = (numInf + 1) / 2;
                        int cTG = player.getTg();
                        int fTG = cTG + numTG;
                        player.setTg(fTG);
                        message.append(ident).append(" removed ").append(numTG).append(" infantry from ").append(Helper.getPlanetRepresentation(unitHolder.getName(), game))
                            .append(" and gained that many TGs (").append(cTG).append("->").append(fTG).append("). \n");
                        tile.removeUnit(unitHolder.getName(), infKey, numTG);
                        if (player.hasInf2Tech()) {
                            ButtonHelper.resolveInfantryDeath(game, player, numTG);
                        }
                        boolean cabalMech = player.hasAbility("amalgamation") && unitHolder.getUnitCount(UnitType.Mech, player.getColor()) > 0 && player.hasUnit("cabal_mech") && !game.getLaws().containsKey("articles_war");
                        if (player.hasAbility("amalgamation") && (ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", player, tile) || cabalMech) && FoWHelper.playerHasUnitsOnPlanet(player, tile, unitHolder.getName())) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, game, player, numTG, "infantry", event);
                        }

                    }
                }
            }
        }
        if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))) {
            ButtonHelperFactionSpecific.offerMahactInfButtons(player, game);
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
        ButtonHelperAgents.resolveArtunoCheck(player, game, player.getTg() - oldTg);
        ButtonHelperAbilities.pillageCheck(player, game);

    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        SpecialCommand.reply(event);
    }
}
