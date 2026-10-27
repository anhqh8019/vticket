package vn.venusgiti.vticket;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import vn.venusgiti.vticket.Domain.*;
public final class Requests {
 public record ProductInput(@NotBlank @Size(max=40) @Pattern(regexp="[A-Z0-9_-]+") String code,@NotBlank @Size(max=150) String name,@Size(max=1000) String description,@NotNull Kind kind,@NotNull @DecimalMin("0") @DecimalMax("1000000000") @Digits(integer=10,fraction=0) BigDecimal price,boolean active,Set<Long> includedServiceIds) {}
 public record VoucherInput(@NotBlank @Size(max=150) String customerName,@NotBlank @Pattern(regexp="[+0-9 -]{9,20}") String phone,@Min(1) @Max(100) int percentage,@NotNull Instant validFrom,@NotNull Instant validUntil,@Size(max=500) String note) {}
 public record LineInput(@NotNull Long productId,@Min(1) @Max(100) int quantity) {}
 public record OrderInput(@NotBlank @Size(max=150) String customerName,@NotBlank @Pattern(regexp="[+0-9 -]{9,20}") String phone,@Email @Size(max=254) String email,@NotNull LocalDate visitDate,@Size(max=64) String voucherCode,@NotEmpty @Size(max=30) List<@Valid LineInput> lines) {}
 public record BatchInput(@NotBlank @Size(max=40) @Pattern(regexp="[A-Z0-9_-]+") String code,@NotNull Long productId,@Min(1) @Max(10000) int quantity,@NotNull LocalDate validFrom,@NotNull LocalDate validUntil) {}
 public record SmileCodeInput(@NotBlank @Size(max=64) @Pattern(regexp="[A-Za-z0-9._/-]+") String serial,@NotBlank @Size(max=128) @Pattern(regexp="[A-Za-z0-9._/-]+") String barcode,@Size(max=64) @Pattern(regexp="[0-9]{1,19}") String seqNo,@Size(max=64) String pkgCode,LocalDate fromDate,LocalDate toDate,Boolean isWeekday,Boolean isChild,Boolean isCancel,Boolean isLoss) {
  public SmileCodeInput(String serial,String barcode){this(serial,barcode,null,null,null,null,null,null,null,null);}
 }
 public record SmileBatchInput(@NotBlank @Size(max=40) @Pattern(regexp="[A-Z0-9_-]+") String code,@NotNull Long productId,@NotBlank @Size(max=64) @Pattern(regexp="[A-Za-z0-9._/-]+") String smileTicketTypeCode,@NotBlank @Size(max=150) String smileTicketTypeName,@NotNull LocalDate validFrom,@NotNull LocalDate validUntil,@NotEmpty @Size(max=10000) List<@NotNull @Valid SmileCodeInput> codes,boolean smileIsWeekday,boolean smileIsChild) {
  public SmileBatchInput(String code,Long productId,String typeCode,String typeName,LocalDate from,LocalDate until,List<SmileCodeInput> codes){this(code,productId,typeCode,typeName,from,until,codes,false,false);}
 }
 public record BatchActiveInput(boolean active) {}
 public record CheckInInput(@NotBlank @Size(max=64) String qrToken) {}
}
