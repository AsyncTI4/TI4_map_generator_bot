package ti4.service.leader.agent.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.fow.FogTokenRemovalService;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;

public final class MahactAgent extends AgentModule<Integer> {

    static final String ID = "mahactagent";
    private static final String LEGACY_FOLLOW = "mahactA_follow_";
    private static final String CONSTRUCTION_PLACEMENT = "placeHolderOfConInSystem_";
    private static final int CONSTRUCTION = 4;

    public MahactAgent() {
        super(ID, "Jae Mir Kan, the Mahact");
    }

    public static Button followButton(Player mahact, int strategyCard) {
        return Buttons.green(
                ownedButtonId(mahact, ID, String.valueOf(strategyCard)),
                useLabel(mahact, ID, "Mahact Agent"),
                FactionEmojis.Mahact);
    }

    @Override
    public Optional<Integer> decode(Game game, Player user, String payload) {
        try {
            return Optional.of(Integer.parseInt(payload));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<Integer> use) {
        return " has exhausted " + use.agentName() + " to follow **" + Helper.getSCName(use.payload(), use.game())
                + "**.";
    }

    @Override
    public Optional<MessageChannel> announcementChannel(AgentUse<Integer> use) {
        if (use.game().isFowMode()) {
            return Optional.empty();
        }
        return Optional.of(ButtonHelper.getSCFollowChannel(use.game(), use.user(), use.payload()));
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_FOLLOW)
    static void legacyFollow(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, StringUtils.substringAfterLast(buttonID, "_"));
    }

    @Override
    public AgentOutcome resolve(AgentUse<Integer> use) {
        Game game = use.game();
        Player mahact = use.user();
        int strategyCard = use.payload();
        if (game.isFowMode()) {
            FogTokenRemovalService.startMahactAgent(use.event(), game, mahact, strategyCard);
            return AgentOutcome.none().deletingPressedMessage();
        }
        if (!mahact.getFollowedSCs().contains(strategyCard)) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(mahact, strategyCard, game, use.event());
        }
        mahact.addFollowedSC(strategyCard, use.event());
        offerTokenRemoval(use, strategyCard);
        ButtonHelperSCs.reactToFollowedStrategyCard(
                game, mahact, ButtonHelper.getSCFollowChannel(game, mahact, strategyCard));
        return AgentOutcome.none().deletingPressedMessage();
    }

    private static void offerTokenRemoval(AgentUse<Integer> use, int strategyCard) {
        Game game = use.game();
        Player mahact = use.user();
        MessageChannel channel = ButtonHelper.getSCFollowChannel(game, mahact, strategyCard);
        for (Player holder : game.getPlayers().values()) {
            if (!holder.getSCs().contains(strategyCard)) {
                continue;
            }
            List<Button> buttons = new ArrayList<>();
            for (Tile tile : ButtonHelper.getTilesWithYourCC(holder, game, use.event())) {
                buttons.add(Buttons.green(
                        mahact.factionButtonChecker() + "removeCCFromBoard_mahactAgent" + holder.getFaction() + "_"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, mahact)));
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    channel, mahact.getRepresentationUnfogged() + " Use buttons to remove a command token.", buttons);
        }
    }

    @ButtonHandler(CONSTRUCTION_PLACEMENT)
    static void placeConstructionHolderToken(
            GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.replace(CONSTRUCTION_PLACEMENT, "");
        String tileID = AliasHandler.resolveTile(planet.toLowerCase());
        Tile tile = game.getTile(tileID);
        if (tile == null) {
            tile = game.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }
        Player constructionPlayer = player;
        for (Player p2 : game.getRealPlayers()) {
            if (p2.getSCs().contains(CONSTRUCTION)) {
                constructionPlayer = p2;
            }
        }
        CommandCounterHelper.addCC(event, constructionPlayer, tile);

        String colorName = Mapper.getColor(constructionPlayer.getColor()).getDisplayName();
        String message = player.getRepresentation() + " placed 1 " + colorName + " command token in the "
                + Helper.getPlanetRepresentation(planet, game) + " system due to use of "
                + AgentLifecycle.agentNameForHolder(game, player, ID) + " on **Construction**.";
        ButtonHelper.sendMessageToRightStratThread(player, game, message, "construction");
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(game, event);
        }
        ButtonHelper.deleteMessage(event);
    }
}
