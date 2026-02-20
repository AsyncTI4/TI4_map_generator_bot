package ti4.service.franken;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.draft.DraftItem;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.milty.MiltyService;

@UtilityClass
public class FrankenHomeService {

    public String getPlayerHsFaction(Player player) {
        for (String faction : player.getStoredList("draftedHS")) {
            if (!Mapper.isValidFaction(faction)) continue;

            String hs = Mapper.getFaction(faction).getHomeSystem();
            if (hs == null || hs.isBlank()) continue;
            if (player.getGame().getTile(hs) != null) return faction;
        }
        return null;
    }

    public String getPlayerHsRingPosition(Player player) {
        String faction = getPlayerHsFaction(player);
        String hs = Mapper.getFaction(faction).getHomeSystem();
        return player.getGame().getTile(hs).getPosition();
    }

    public Tile getPlayerHs(Player player) {
        Game game = player.getGame();
        return switch (getPlayerHsFaction(player)) {
            case null -> null;
            case "ghost" -> game.getTile("51");
            case "crimson" -> game.getTile("118");
            default -> game.getTileByPosition(getPlayerHsRingPosition(player));
        };
    }

    public void replaceHomeSystem(ButtonInteractionEvent event, Player player, DraftItem newHS) {
        String ringPos = getPlayerHsRingPosition(player);
        if (ringPos == null) {
            String msg = " Could not determine your home system location. Please resolve manually.";
            MessageHelper.sendEphemeralMessageToEventChannel(event, player.getRepresentation() + msg);
            return;
        }
        System.out.println("Replacing home system for player " + player.getUserName() + " at location " + ringPos);

        String oldHomeFaction = getPlayerHsFaction(player);
        String newHomeFaction = newHS.getItemId();
        System.out.println(" - " + oldHomeFaction + " HS -> " + newHomeFaction + " HS");
        // if (Objects.equals(oldHomeFaction, newHomeFaction) && getPlayerHs(player) != null) return;

        // Remove the old tile, removes the planets etc
        player.getGame().removeTile(ringPos);

        String newTileId = Mapper.getFaction(newHomeFaction).getHomeSystem();
        Tile toAdd = new Tile(newTileId, ringPos);
        player.getGame().setTile(toAdd);
        player.setHomeSystemPosition(ringPos);
        player.setPlayerStatsAnchorPosition(ringPos);
        MiltyService.setupExtraFactionTiles(player.getGame(), player, newHomeFaction, ringPos, toAdd);
        refreshHomePlanets(player);

        MessageHelper.sendEphemeralMessageToEventChannel(event, "Set up " + newHS.getShortDescription());
    }

    public void removeHomeSystem(ButtonInteractionEvent event, Player player, DraftItem item) {
        purgeAppliedItem(player, item);
        MessageHelper.sendEphemeralMessageToEventChannel(event, "Removed " + item.getShortDescription());
    }

    private void refreshHomePlanets(Player player) {
        for (Planet p : getPlayerHs(player).getPlanetUnitHolders()) {
            player.addPlanet(p.getName());
            player.refreshPlanet(p.getName());
        }
    }

    public void purgeAppliedItem(Player player, DraftItem item) {
        purgeAppliedItemAlias(player, item.getAlias());
        for (var addl : item.getErrata().getAdditionalComponents()) {
            purgeAppliedItemAlias(player, addl.getAlias());
        }
    }

    private void purgeAppliedItemAlias(Player player, String alias) {
        while (player.getStoredList("frankenAppliedItems").contains(alias)) {
            player.removeFromStoredList("frankenAppliedItems", alias);
        }
    }
}
