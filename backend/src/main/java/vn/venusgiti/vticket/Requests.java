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
 public record BatchActiveInput(boolean active) {}
 public record CheckInInput(@NotBlank @Size(max=64) String qrToken) {}
}
