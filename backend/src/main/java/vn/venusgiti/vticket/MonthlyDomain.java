package vn.venusgiti.vticket;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class MonthlyDomain {
 private MonthlyDomain() {}
 public enum Tier { SILVER, GOLD, PLATINUM }
 @Entity(name="MonthlyPlan") @Table(name="monthly_plans")
 public static class Plan {
  @Id @Enumerated(EnumType.STRING) @Column(length=16) public Tier tier;
  @Column(nullable=false) public Long ticketProductId;
  @Column(nullable=false,precision=18,scale=0) public BigDecimal weekdayPrice;
  @Column(nullable=false,precision=18,scale=0) public BigDecimal weekendPrice;
  @Column(nullable=false) public boolean active;
 }
 @Entity(name="MonthlyPass") @Table(name="monthly_passes")
 public static class Pass {
  @Id @Column(length=36) public String bookingId;
  @com.fasterxml.jackson.annotation.JsonIgnore @Column(nullable=false,unique=true,length=36) public String qrToken;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) public Tier tier;
  @Column(nullable=false) public Long ticketProductId;
  @Column(nullable=false,length=150) public String holderName;
  @Column(nullable=false,length=150) public String ticketName;
  @Column(length=150) public String mealName;
  @Column(nullable=false) public Instant createdAt;
  @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="monthly_pass_days",joinColumns=@JoinColumn(name="booking_id")) @MapKeyColumn(name="visit_date")
  public Map<LocalDate,Day> days=new TreeMap<>();
 }
 @Embeddable public static class Day {
  @Column(nullable=false,precision=18,scale=0) public BigDecimal price;
  @Column(nullable=false,length=16) public String dayType;
  @Column(length=36) public String issuedBookingId;
  public Instant issuedAt;
  @Column(length=100) public String issuedBy;
 }
}
