package ti4.spring.service.tournament;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.game.persistence.DatabasePersistenceGate;
import ti4.spring.context.SpringContext;

@Service
public class TournamentAdminRoleService {

    private final TournamentAdminRoleRepository roles;
    private volatile Set<String> cachedRoleIds;

    TournamentAdminRoleService(TournamentAdminRoleRepository roles) {
        this.roles = roles;
    }

    public static TournamentAdminRoleService getBean() {
        return SpringContext.getBean(TournamentAdminRoleService.class);
    }

    public Set<String> roleIds() {
        if (DatabasePersistenceGate.isDisabled()) return Set.of();
        Set<String> cached = cachedRoleIds;
        if (cached != null) return cached;
        Set<String> loaded =
                roles.findAll().stream().map(TournamentAdminRole::getRoleId).collect(Collectors.toUnmodifiableSet());
        cachedRoleIds = loaded;
        return loaded;
    }

    public List<TournamentAdminRole> list() {
        return roles.findAll();
    }

    @Transactional
    public boolean add(String guildId, String roleId, String addedBy) {
        if (roles.findByRoleId(roleId).isPresent()) return false;
        roles.save(new TournamentAdminRole(guildId, roleId, addedBy));
        cachedRoleIds = null;
        return true;
    }

    @Transactional
    public boolean remove(String roleId) {
        var existing = roles.findByRoleId(roleId);
        existing.ifPresent(roles::delete);
        cachedRoleIds = null;
        return existing.isPresent();
    }
}
