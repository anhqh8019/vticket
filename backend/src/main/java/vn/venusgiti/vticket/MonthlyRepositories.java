package vn.venusgiti.vticket;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import vn.venusgiti.vticket.MonthlyDomain.*;
interface MonthlyPlans extends JpaRepository<Plan,Tier> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from MonthlyPlan p where p.tier=:tier") Optional<Plan> lockTier(@Param("tier") Tier tier);
}
interface MonthlyPasses extends JpaRepository<Pass,String> {
 Optional<Pass> findByQrToken(String token);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from MonthlyPass p where p.bookingId=:id") Optional<Pass> lockId(@Param("id") String id);
 List<Pass> findTop200ByOrderByCreatedAtDesc();
}
