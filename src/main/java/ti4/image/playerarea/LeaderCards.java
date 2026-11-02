package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.PaImage;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalText;
import ti4.image.io.Fonts;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.service.VeiledHeartService.VeiledCardType;

final class LeaderCards {

    private static final int CARD_STEP = 48;
    private static final String HIDDEN_COMMANDER_TEXT = "Commander of a player you cannot see.";
    private static final Comparator<Leader> LEADER_ORDER = Comparator.comparingInt(LeaderCards::typeRank)
            .thenComparing(LeaderCards::sortName, String::compareToIgnoreCase);

    private LeaderCards() {}

    static int draw(PlayerAreaContext ctx, Player player, int x, int y) {
        Game game = ctx.game();
        if (player.getLeaders().isEmpty() && !game.isVeiledHeartMode()) {
            return x;
        }
        Graphics2D g2 = ctx.g2();
        g2.setStroke(Strokes.STROKE_2);

        List<Leader> allLeaders = new ArrayList<>(player.getLeaders());
        allLeaders.sort(LEADER_ORDER);

        int deltaX = 0;
        boolean shouldDrawVeiledGenomes = game.isVeiledHeartMode();
        for (Leader leader : allLeaders) {
            if (shouldDrawVeiledGenomes && !Constants.AGENT.equalsIgnoreCase(leader.getType())) {
                deltaX = VeiledCards.draw(g2, x, y, VeiledCardType.GENOME, deltaX, player);
                shouldDrawVeiledGenomes = false;
            }
            deltaX = drawLeader(ctx, leader, x + deltaX, y) - x;
        }

        if (game.isVeiledHeartMode()) {
            if (shouldDrawVeiledGenomes) {
                deltaX = VeiledCards.draw(g2, x, y, VeiledCardType.GENOME, deltaX, player);
            }
            deltaX = VeiledCards.draw(g2, x, y, VeiledCardType.PARADIGM, deltaX, player);
        }

        if (player.hasAbility("imperia") || player.hasAbility("imperia_y")) {
            deltaX = drawImperiaCommanders(ctx, player, x, y, deltaX + 5);
        }

        Player lichPoolOwner = game.getRevenantCommanderOwner(player);
        if (lichPoolOwner != null) {
            deltaX = drawLichCommanders(ctx, lichPoolOwner, x, y, deltaX + 5);
        }

        return x + deltaX + 20;
    }

    private static int typeRank(Leader leader) {
        return switch (leader.getType()) {
            case Constants.AGENT -> 0;
            case Constants.ENVOY -> 1;
            case Constants.COMMANDER -> 2;
            case Constants.HERO -> 3;
            default -> -1;
        };
    }

    private static String sortName(Leader leader) {
        LeaderModel leaderModel = Mapper.getLeader(leader.getId());
        return leaderModel == null ? leader.getId() : leaderModel.getName();
    }

    private static int drawLeader(PlayerAreaContext ctx, Leader leader, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        boolean isExhaustedLocked = leader.isExhausted() || leader.isLocked();
        g2.setColor(isExhaustedLocked ? Color.GRAY : Color.WHITE);

        CardRect.draw(ctx, x - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, Mapper.getLeader(leader.getId()));

        if (Mapper.isValidLeader(leader.getId())) {
            FactionIcons.drawFaction(g2, leaderFactionIcon(leader), x - 1, y + 108, 42, 42);
        }

        if (leader.getTgCount() != 0) {
            g2.setColor(ColorUtil.TradeGoodColor);
            g2.setFont(Fonts.ti(32));
            int offset = 20 - g2.getFontMetrics().stringWidth("" + leader.getTgCount()) / 2;
            g2.drawString(Integer.toString(leader.getTgCount()), x + offset, y + 25);
        } else {
            drawPips(g2, leader, isExhaustedLocked, x, y);
        }

        LeaderModel leaderModel = Mapper.getLeader(leader.getId());
        if (leaderModel == null) {
            g2.setFont(Fonts.ti(14));
            VerticalText.drawOneOrTwoLines(g2, leader.getId(), x + 7, y + 30, 120, true);
            return x + CARD_STEP;
        }
        boolean shrink = game.isTwilightsFallMode() ? leaderModel.getTFShrinkName() : leaderModel.getShrinkName();
        String name = game.isTwilightsFallMode() ? leaderModel.getTFShortName() : leaderModel.getShortName();

        if ("yssarilagent".equalsIgnoreCase(leader.getId()) && !game.isTwilightsFallMode()) {
            VerticalText.draw(g2, "Clever, Clever".toUpperCase(), x + 8, y + 30, Fonts.ti(14), true);
            VerticalText.draw(g2, "Ssruu".toUpperCase(), x + 23, y + 30, Fonts.ti(18), true);
        } else if (shrink) {
            g2.setFont(Fonts.ti(16));
            VerticalText.drawOneOrTwoLines(g2, name, x + 9, y + 30, 120, true);
        } else {
            g2.setFont(Fonts.ti(18));
            VerticalText.drawOneOrTwoLines(g2, name, x + 7, y + 30, 120, true);
        }
        return x + CARD_STEP;
    }

    private static String leaderFactionIcon(Leader leader) {
        if (leader.getId().contains("redcreuss")) {
            return "redcreuss";
        }
        if (leader.getId().contains("orlandohero")) {
            return "bastion";
        }
        return Mapper.getLeader(leader.getId()).getFaction();
    }

    private static void drawPips(Graphics2D g2, Leader leader, boolean isExhaustedLocked, int x, int y) {
        String pipID =
                switch (leader.getType()) {
                    case Constants.AGENT -> "i";
                    case Constants.COMMANDER -> "ii";
                    case Constants.HERO -> "iii";
                    case Constants.ENVOY -> "agenda";
                    default -> "";
                };
        if (pipID.isEmpty()) {
            return;
        }
        String status = isExhaustedLocked ? "_exh" : "_rdy";
        String leaderPipInfo = "pa_leaders_pips_" + pipID;
        if (!isExhaustedLocked && leader.isActive()) {
            leaderPipInfo += "_active" + ".png";
        } else {
            leaderPipInfo += status + ".png";
        }
        PaImage.draw(g2, x, y, leaderPipInfo);
    }

    private static Collection<Player> commanderSources(Game game) {
        return game.isMinorFactionsMode() ? game.getRealPlayers() : game.getRealPlayersNDummies();
    }

    private static int drawImperiaCommanders(PlayerAreaContext ctx, Player player, int x, int y, int deltaX) {
        List<String> mahactCCs = player.getMahactCC();
        for (Player other : commanderSources(ctx.game())) {
            if (other == player || !mahactCCs.contains(other.getColor())) {
                continue;
            }
            Leader commander = other.unsafeGetLeader(Constants.COMMANDER);
            if (commander != null) {
                drawBorrowedCommander(ctx, other, commander, "pa_leaders_imperia", x + deltaX, y);
                deltaX += CARD_STEP;
            }
        }
        return deltaX;
    }

    private static int drawLichCommanders(PlayerAreaContext ctx, Player lichPoolOwner, int x, int y, int deltaX) {
        Game game = ctx.game();
        for (Player other : commanderSources(game)) {
            if (other.equals(lichPoolOwner) || lichPoolOwner.getDebtTokenCount(other.getColor(), "lich") < 1) {
                continue;
            }
            Leader commander = game.getRevenantLichCommander(lichPoolOwner, other);
            if (commander != null) {
                drawBorrowedCommander(ctx, other, commander, "pa_leaders_lich", x + deltaX, y);
                deltaX += CARD_STEP;
            }
        }
        return deltaX;
    }

    private static void drawBorrowedCommander(
            PlayerAreaContext ctx, Player owner, Leader commander, String overlayPrefix, int x, int y) {
        Graphics2D g2 = ctx.g2();
        String status = commander.isLocked() ? "_exh" : "_rdy";
        if (ctx.hidesStatsOf(owner)) {
            CardRect.draw(ctx, x - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, "Commander", HIDDEN_COMMANDER_TEXT);
        } else {
            CardRect.draw(ctx, x - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, Mapper.getLeader(commander.getId()));
            FactionIcons.draw(g2, owner, x - 1, y + 108, 42, 42);
        }
        PaImage.draw(g2, x, y, overlayPrefix + status + ".png");
        PaImage.draw(g2, x, y, "pa_leaders_pips_ii" + status + ".png");
    }
}
