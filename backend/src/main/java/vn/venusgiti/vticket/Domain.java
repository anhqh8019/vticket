package vn.venusgiti.vticket;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class Domain {
 private Domain() {}
 public enum Kind { TICKET, SERVICE, COMBO }
 public enum StockState { AVAILABLE, RESERVED, SOLD, USED }
 public enum Channel { ONLINE, COUNTER }
 public enum Status { PENDING, PAID, CANCELLED, EXPIRED }
 @Entity(name="Product") @Table(name="products")
 public static class Product {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false,unique=true,length=40) public String code;
  @Column(nullable=false,length=150) public String name;
  @Column(length=1000) public String description;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) public Kind kind;
  @Column(nullable=false,precision=18,scale=0) public BigDecimal price;
  public Long comboTicketId;
  public Integer comboBuyQuantity;
  public Integer comboGiftQuantity;
  @Column(nullable=false) public boolean active=true;
  @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="product_services",joinColumns=@JoinColumn(name="product_id")) @Column(name="service_id",nullable=false) public Set<Long> includedServiceIds=new HashSet<>();
 }
 @Entity(name="Voucher") @Table(name="vouchers")
 public static class Voucher {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false,unique=true,length=64) public String code;
  @Column(nullable=false,length=150) public String customerName;
  @Column(nullable=false,length=20) public String phone;
  @Column(nullable=false) public int percentage;
  @Column(nullable=false) public Instant validFrom;
  @Column(nullable=false) public Instant validUntil;
  @Column(nullable=false) public boolean revoked;
  @Column(length=36) public String reservedOrderId;
  public Instant reservedUntil;
  @Column(length=36) public String usedOrderId;
  public Instant usedAt;
  @Column(length=500) public String note;
 }
 @Entity(name="Booking") @Table(name="bookings")
 public static class Booking {
  @Id @Column(length=36) public String id;
  @Column(nullable=false,unique=true,length=36) public String accessToken;
  @Column(nullable=false,length=150) public String customerName;
  @Column(nullable=false,length=20) public String phone;
  @Column(length=254) public String email;
  @Column(nullable=false) public LocalDate visitDate;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) public Channel channel;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) public Status status;
  @Column(length=64) public String voucherCode;
  @Column(nullable=false) public int discountPercent;
  @Column(nullable=false,precision=18,scale=0) public BigDecimal subtotal;
  @Column(nullable=false,precision=18,scale=0) public BigDecimal discount;
  @Column(nullable=false,precision=18,scale=0) public BigDecimal total;
  @Column(nullable=false) public Instant createdAt;
  @Column(nullable=false) public Instant expiresAt;
  public Instant paidAt;
  @Column(length=30) public String paymentMethod;
  @Column(length=128) public String paymentReference;
  @Column(length=100) public String soldBy;
  @OneToMany(cascade=CascadeType.ALL,fetch=FetchType.EAGER) @JoinColumn(name="booking_id",nullable=false) public List<BookingLine> lines=new ArrayList<>();
 }
 @Entity(name="BookingLine") @Table(name="booking_lines")
 public static class BookingLine {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long productId;
  @Column(nullable=false,length=150) public String name;
  @Column(nullable=false,length=1000) public String includedServices;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) public Kind kind;
  @Column(nullable=false,precision=18,scale=0) public BigDecimal unitPrice;
  @Column(nullable=false) public int quantity;
  public Long ticketProductId;
  public Integer ticketQuantity;
  @Column(length=150) public String ticketProductName;
 }
 @Entity(name="Ticket") @Table(name="tickets")
 public static class Ticket {
  @Id @Column(length=36) public String id;
  @Column(nullable=false,length=36) public String bookingId;
  @Column(nullable=false,unique=true,length=36) public String qrToken;
  @Column(length=36) public String stockId;
  @Column(length=64) public String ticketCode;
  @Column(length=40) public String batchCode;
  @Column(length=20) public String source;
  @Column(length=128) public String smileGateBarcode;
  @Column(length=64) public String smileSeqNo;
  @Column(length=64) public String smileSerial;
  @Column(length=128) public String smileBarcode;
  @Column(length=64) public String smileTicketTypeCode;
  @Column(length=150) public String smileTicketTypeName;
  public Boolean smileIsWeekday;
  public Boolean smileIsChild;
  @Column(nullable=false,length=150) public String productName;
  @Column(nullable=false,length=1000) public String includedServices;
  @Column(nullable=false) public LocalDate visitDate;
  public Instant checkedInAt;
  @Column(length=100) public String checkedInBy;
 }

 @Entity(name="TicketBatch") @Table(name="ticket_batches")
 public static class TicketBatch {
  @Id @Column(length=36) public String id;
  @Column(nullable=false,unique=true,length=40) public String code;
  @Column(nullable=false) public Long productId;
  @Column(nullable=false) public int quantity;
  @Column(nullable=false) public LocalDate validFrom;
  @Column(nullable=false) public LocalDate validUntil;
  @Column(nullable=false) public boolean active=true;
  @Column(nullable=false,length=20) public String source="VTICKET";
  @Column(length=20) public String smileSyncState;
  @Column(length=20) public String smileCodeMapping;
  public Instant smileSyncedAt;
  @Column(length=64) public String smileTicketTypeCode;
  @Column(length=150) public String smileTicketTypeName;
  public Boolean smileIsWeekday;
  public Boolean smileIsChild;
  @Column(nullable=false) public Instant createdAt;
  @Column(nullable=false,length=100) public String createdBy;
 }
 @Entity(name="StockCode") @Table(name="ticket_stock")
 public static class StockCode {
  @Id @Column(length=36) public String id;
  @Column(nullable=false,length=36) public String batchId;
  @Column(nullable=false) public Long productId;
  @Column(nullable=false,unique=true,length=64) public String code;
  @Column(nullable=false,unique=true,length=36) public String qrToken;
  @Column(length=128) public String smileGateBarcode;
  @Column(length=64) public String smileSeqNo;
  @Column(length=64) public String smileSerial;
  @Column(length=128) public String smileBarcode;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) public StockState state=StockState.AVAILABLE;
  @Column(length=36) public String reservedOrderId;
  public Instant reservedUntil;
  @Column(length=36) public String soldOrderId;
  public Instant soldAt;
  public Instant usedAt;
 }

 public enum IntentState { CREATING, READY, FAILED, PAID, CLOSED }
 public enum ReceiptState { APPLIED, REVIEW }
 @Entity(name="PaymentIntent") @Table(name="payment_intents")
 public static class PaymentIntent {
  @Id @Column(length=13) public String id;
  @Column(nullable=false,unique=true,length=36) public String bookingId;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) public IntentState state;
  @Column(nullable=false,precision=18,scale=0) public BigDecimal amount;
  @Column(nullable=false,length=20) public String bankCode;
  @Column(nullable=false,length=64) public String bankAccount;
  @Column(nullable=false,length=150) public String accountName;
  @Column(nullable=false,length=500) public String content;
  @Column(length=4000) public String qrCode;
  @Column(length=2000) public String qrLink;
  @Column(length=512) public String transactionRefId;
  @Column(length=64) public String virtualAccount;
  @Column(nullable=false) public Instant createdAt;
  @Column(nullable=false) public Instant expiresAt;
  @Column(length=36) public String attemptId;
  public Instant attemptStartedAt;
  @Column(length=300) public String lastError;
 }
 @Entity(name="PaymentReceipt") @Table(name="payment_receipts")
 public static class PaymentReceipt {
  @Id @Column(length=36) public String id;
  @Column(nullable=false,unique=true,length=128) public String transactionId;
  @Column(nullable=false,unique=true,length=64) public String receiptKey;
  @Column(nullable=false,length=128) public String referenceNumber;
  @Column(nullable=false,length=64) public String providerOrderId;
  @Column(length=36) public String bookingId;
  @Column(nullable=false,length=64) public String bankAccount;
  @Column(nullable=false,precision=18,scale=0) public BigDecimal amount;
  @Column(nullable=false,length=1) public String transType;
  @Column(nullable=false,length=2000) public String content;
  @Column(nullable=false) public Instant transactionAt;
  @Column(nullable=false) public Instant receivedAt;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) public ReceiptState state;
  @Column(length=64) public String reason;
 }
 @Entity(name="PaymentCallbackLock") @Table(name="payment_callback_lock")
 public static class PaymentCallbackLock { @Id public Integer id; }
 @Entity(name="TicketIdentifierCounter") @Table(name="ticket_identifier_counter")
 public static class TicketIdentifierCounter { @Id public Integer id; @Column(nullable=false) public long nextValue; }
 @Entity(name="SmileJob") @Table(name="smile_sync_jobs")
 public static class SmileJob {
  @Id @Column(length=36) public String batchId;
  @Column(nullable=false,length=20) public String state;
  @Column(nullable=false) public int attempts;
  @Column(nullable=false) public Instant createdAt;
  @Column(nullable=false) public Instant nextAttemptAt;
  public Instant leaseUntil;
  public Instant syncedAt;
  @Column(length=36) public String attemptId;
  @Column(length=500) public String lastError;
 }
 @Entity(name="Audit") @Table(name="audit_events")
 public static class Audit {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Instant happenedAt;
  @Column(nullable=false,length=100) public String actor;
  @Column(nullable=false,length=60) public String action;
  @Column(nullable=false,length=100) public String target;
 }
}
