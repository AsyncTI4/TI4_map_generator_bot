package ti4.spring.api.community;

import java.util.List;

record CommunityGameSample(String id, String name, int round, int vpTarget, List<String> factions) {}
