package vn.venusgiti.vticket;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import java.time.Instant;
import vn.venusgiti.vticket.Domain.*;
interface Products extends JpaRepository<Product,Long> { List<Product> findByActiveTrueOrderByIdAsc();
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Product p where p.id=:id") Optional<Product> lockId(@Param("id") Long id); }
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
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from Ticket t where t.qrToken=:token or t.ticketCode=:code") Optional<Ticket> lockTokenOrCode(@Param("token") String token,@Param("code") String code);
}
interface Audits extends JpaRepository<Audit,Long> { List<Audit> findTop100ByOrderByHappenedAtDesc(); }

interface TicketBatches extends JpaRepository<TicketBatch,String> {
 List<TicketBatch> findTop200ByOrderByCreatedAtDesc();
}
interface TicketStock extends JpaRepository<StockCode,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from StockCode s where s.productId=:product and s.state=:state and s.reservedUntil<=:now") List<StockCode> expired(@Param("product") Long product,@Param("state") StockState state,@Param("now") Instant now);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from StockCode s, TicketBatch b where s.batchId=b.id and s.productId=:product and s.state=:state and b.active=true and b.validFrom<=:date and b.validUntil>=:date order by b.createdAt,s.code") List<StockCode> available(@Param("product") Long product,@Param("state") StockState state,@Param("date") java.time.LocalDate date,org.springframework.data.domain.Pageable page);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from StockCode s where s.reservedOrderId=:orderId order by s.code") List<StockCode> reserved(@Param("orderId") String orderId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from StockCode s where s.id=:id") Optional<StockCode> lockId(@Param("id") String id);
 @Query("select s.batchId,s.state,count(s) from StockCode s where s.batchId in :ids group by s.batchId,s.state") List<Object[]> counts(@Param("ids") List<String> ids);
 org.springframework.data.domain.Page<StockCode> findByBatchIdOrderByCodeAsc(String id,org.springframework.data.domain.Pageable page);
 org.springframework.data.domain.Page<StockCode> findByBatchIdAndStateOrderByCodeAsc(String id,StockState state,org.springframework.data.domain.Pageable page);
 long countByBatchIdAndState(String id,StockState state);
}
