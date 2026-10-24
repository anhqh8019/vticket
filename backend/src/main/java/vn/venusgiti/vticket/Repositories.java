package vn.venusgiti.vticket;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
import vn.venusgiti.vticket.Domain.*;
interface Products extends JpaRepository<Product,Long> { List<Product> findByActiveTrueOrderByIdAsc(); }
interface Vouchers extends JpaRepository<Voucher,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from Voucher v where v.code=:code") Optional<Voucher> lockCode(@Param("code") String code);
}
interface Bookings extends JpaRepository<Booking,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from Booking b where b.id=:id") Optional<Booking> lockId(@Param("id") String id);
 Optional<Booking> findByAccessToken(String token);
 List<Booking> findTop200ByOrderByCreatedAtDesc();
 List<Booking> findByStatusAndExpiresAtBefore(Status status,Instant cutoff);
}
interface Tickets extends JpaRepository<Ticket,String> {
 List<Ticket> findByBookingId(String id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from Ticket t where t.qrToken=:token") Optional<Ticket> lockToken(@Param("token") String token);
}
interface Audits extends JpaRepository<Audit,Long> { List<Audit> findTop100ByOrderByHappenedAtDesc(); }
