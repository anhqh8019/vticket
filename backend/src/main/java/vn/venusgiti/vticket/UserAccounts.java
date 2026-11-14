package vn.venusgiti.vticket;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface UserAccounts extends JpaRepository<UserAccount,Long> {
 Optional<UserAccount> findByUsername(String username);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from UserAccount u order by u.id")
 List<UserAccount> lockAccounts();
}
