package ti4.commands.special;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FighterConscription extends SpecialSubcommandData {
    public FighterConscription() {
        super(Constants.FIGHTER_CONSCRIPTION, "Fighter conscription +1 fighter in each space area");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        List<String> planets = player.getPlanets();
        String colorID = Mapper.getColorID(player.getColor());
        String playerSD = Mapper.getUnitID("sd", player.getColor());
        String playerCSD = Mapper.getUnitID("csd", player.getColor());
        List<Tile> tilesAffected = new ArrayList<>();
        for (Tile tile : activeGame.getTileMap().values()) {
            boolean hasSD = false;
            boolean hasCap = false;
            boolean blockaded = false;
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                // Planet controlled by the player has a space dock in the system
                if (planets.contains(unitHolder.getName())) {
                    HashMap<String, Integer> units = unitHolder.getUnits();
                    Integer numSd = units.get(playerSD);
                    Integer numCsd = units.get(playerCSD);
                    if ((numCsd != null && numCsd > 0) || (numSd != null && numSd > 0)) {
                        hasSD = true;
                    }
                }
                // Check if space area contains capacity units or another player's units
                if ("space".equals(unitHolder.getName())) {
                    Map<String, Integer> units = unitHolder.getUnits();
                    for (Map.Entry<String, Integer> unit : units.entrySet()) {
                        String name = unit.getKey();
                        Integer quantity = unit.getValue();
                        if (name.startsWith(colorID) && quantity != null && quantity > 0) {
                            String unitType = name.substring(4);
                            // Units that always have capacity (Carrier, Dread, Flagship, Warsun, Space Dock in space)
                            if (unitType.startsWith("cv") || unitType.startsWith("dn") || unitType.startsWith("fs") || unitType.startsWith("sd") || unitType.startsWith("ws")) {
                                hasCap = true;
                            }
                            // Titans, Saturn Engine 2, Cruiser 2
                            if (unitType.startsWith("ca")) {
                                if (player.ownsUnit("titans_cruiser") || player.ownsUnit("titans_cruiser2") || player.hasTech("se2") || player.hasTech("cr2")) {
                                    hasCap = true;
                                }
                            }
                            // Argent, Strike Wing Alpha 2
                            if (unitType.startsWith("dd")) {
                                if (player.ownsUnit("argent_destroyer") || player.ownsUnit("argent_destroyer2") || player.hasTech("swa2")) {
                                    hasCap = true;
                                }
                            }
                        } else if (quantity != null && quantity > 0) {
                            blockaded = true;
                            break;
                        }
                    }
                }

                if (blockaded || hasCap) {
                    break;
                }
            }

            if (!blockaded && (hasCap || hasSD)) {
                new AddUnits().unitParsing(event, player.getColor(), tile, "ff", activeGame);
                tilesAffected.add(tile);
            }
        }

        String msg = "Added " + tilesAffected.size() + " fighters.";
        if (tilesAffected.size() > 0) {
            msg += " Please check fleet size and capacity in each of the systems: ";
        }
        boolean first = true;
      StringBuilder msgBuilder = new StringBuilder(msg);
      for (Tile tile : tilesAffected) {
            if (first) {
                msgBuilder.append("\n> **").append(tile.getPosition()).append("**");
                first = false;
            } else {
                msgBuilder.append(", **").append(tile.getPosition()).append("**");
            }
        }
      msg = msgBuilder.toString();
      MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        SpecialCommand.reply(event);
    }
}
