package ti4.service.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.model.ActionCardModel;
import ti4.model.AgendaModel;
import ti4.model.SecretObjectiveModel;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class MonumentsService {

    public static void applyMonuments(Game game) {
        if (!game.isMonumentsMode()) {
            return;
        }

        game.setHomebrew(true);
        game.setStrategyCardSet("monuments");

        List<String> actionCards = Mapper.getActionCards().values().stream()
                .filter(card -> card.getSource() == ComponentSource.monuments)
                .map(ActionCardModel::getAlias)
                .filter(card -> !game.getActionCards().contains(card))
                .toList();
        if (!actionCards.isEmpty()) {
            game.getActionCards().addAll(actionCards);
            Collections.shuffle(game.getActionCards());
        }

        List<String> secretObjectives = Mapper.getSecretObjectives().values().stream()
                .filter(objective -> objective.getSource() == ComponentSource.monuments)
                .map(SecretObjectiveModel::getAlias)
                .filter(objective -> !game.getSecretObjectives().contains(objective))
                .toList();
        if (!secretObjectives.isEmpty()) {
            game.getSecretObjectives().addAll(secretObjectives);
            Collections.shuffle(game.getSecretObjectives());
        }

        List<String> agendas = Mapper.getAgendas().values().stream()
                .filter(agenda -> agenda.getSource() == ComponentSource.monuments)
                .map(AgendaModel::getAlias)
                .filter(agenda -> !game.getAgendas().contains(agenda))
                .toList();
        if (!agendas.isEmpty()) {
            game.getAgendas().addAll(agendas);
            Collections.shuffle(game.getAgendas());
        }
    }

    public static void addFactionMonument(Player player, Game game) {
        if (!game.isMonumentsMode() || game.isFrankenGame()) {
            return;
        }

        Mapper.getUnits().values().stream()
                .filter(unit -> unit.getSource() == ComponentSource.monuments)
                .filter(unit ->
                        unit.getFaction().filter(player.getFaction()::equals).isPresent())
                .filter(unit -> !player.ownsUnit(unit.getId()))
                .findFirst()
                .ifPresent(unit -> player.addOwnedUnitByID(unit.getId()));
    }

    public static List<Button> getPlanetsInOrAdjacentToPlayerMonumentButtons(
            Game game, Player player, String buttonPrefix) {
        if (!game.isMonumentsMode()) {
            return List.of();
        }

        LinkedHashSet<String> systemPositions = new LinkedHashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getUnitHolders().values().stream()
                    .anyMatch(holder -> holder.getUnitCount(UnitType.Monument, player) > 0)) {
                systemPositions.add(tile.getPosition());
                systemPositions.addAll(
                        FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false));
            }
        }

        LinkedHashSet<String> planetNames = new LinkedHashSet<>();
        for (String position : systemPositions) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null) {
                continue;
            }
            for (UnitHolder holder : tile.getPlanetUnitHolders()) {
                planetNames.add(holder.getName());
            }
        }

        List<Button> buttons = new ArrayList<>();
        for (String planetName : planetNames) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + buttonPrefix + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }
        return buttons;
    }

    @ButtonHandler("scoreToppleAMonument")
    public static void scoreToppleAMonument(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode()) {
            return;
        }
        Integer secretIndex = player.getSecretsUnscored().get("tam");
        if (secretIndex == null) {
            return;
        }

        if (SecretObjectiveHelper.scoreSO(event, game, player, secretIndex, player.getCorrectChannel())) {
            ButtonHelper.deleteMessage(event);
        }
    }
}
