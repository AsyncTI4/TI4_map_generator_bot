package ti4.spring.service.statistics.matchmaking.queue;

record GameConfig(String playerCount, String victoryPointGoal, String expansion, String pace) {

    int playerCountValue() {
        return Integer.parseInt(playerCount);
    }
}
