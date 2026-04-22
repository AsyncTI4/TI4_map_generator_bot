package ti4.spring.service.deploy;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActiveLeaseRepository extends JpaRepository<ActiveLeaseEntity, String> {

    @Modifying
    @Query(value = """
                    insert into active_lease (lease_key, instance_id, lease_expires_at, heartbeat_at)
                    values (:leaseKey, :instanceId, :leaseExpiresAt, :heartbeatAt)
                    on conflict (lease_key) do nothing
                    """, nativeQuery = true)
    int insertLeaseRowIfAbsent(
            @Param("leaseKey") String leaseKey,
            @Param("instanceId") String instanceId,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("heartbeatAt") Instant heartbeatAt);

    @Modifying
    @Query("""
            update ActiveLeaseEntity lease
            set lease.instanceId = :instanceId,
                lease.leaseExpiresAt = :leaseExpiresAt,
                lease.heartbeatAt = :heartbeatAt
            where lease.leaseKey = :leaseKey
              and (
                lease.instanceId = :instanceId
                or lease.leaseExpiresAt is null
                or lease.leaseExpiresAt < :now
              )
            """)
    int claimLease(
            @Param("leaseKey") String leaseKey,
            @Param("instanceId") String instanceId,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("heartbeatAt") Instant heartbeatAt,
            @Param("now") Instant now);

    @Modifying
    @Query("""
            update ActiveLeaseEntity lease
            set lease.leaseExpiresAt = :leaseExpiresAt,
                lease.heartbeatAt = :heartbeatAt
            where lease.leaseKey = :leaseKey
              and lease.instanceId = :instanceId
            """)
    int updateLeaseIfOwned(
            @Param("leaseKey") String leaseKey,
            @Param("instanceId") String instanceId,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("heartbeatAt") Instant heartbeatAt);
}
