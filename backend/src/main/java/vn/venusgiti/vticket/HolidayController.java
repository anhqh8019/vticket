package vn.venusgiti.vticket;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/api")
public class HolidayController {
 private final Holidays holidays;private final BookingService bookings;
 HolidayController(Holidays holidays,BookingService bookings){this.holidays=holidays;this.bookings=bookings;}
 record Day(@NotNull LocalDate date,@NotBlank @Size(max=150) String name){}
 record Calendar(@NotNull @Size(max=366) List<@NotNull @Valid Day> days){}
 private LocalDate start(int year){if(year<2000||year>2100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Năm phải từ 2000 đến 2100");return LocalDate.of(year,1,1);}
 @GetMapping({"/public/holidays","/admin/holidays"}) public List<Holiday> list(@RequestParam int year){var start=start(year);return holidays.findByDateGreaterThanEqualAndDateLessThanOrderByDate(start,start.plusYears(1));}
 @PutMapping("/admin/holidays/{year}") @Transactional public List<Holiday> replace(@PathVariable int year,@Valid @RequestBody Calendar in,Authentication auth){
  var start=start(year);Set<LocalDate> dates=new HashSet<>();
  for(var day:in.days())if(day.date().getYear()!=year||!dates.add(day.date()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ngày lễ phải thuộc năm đã chọn và không được trùng");
  holidays.deleteAll(holidays.findByDateGreaterThanEqualAndDateLessThanOrderByDate(start,start.plusYears(1)));holidays.flush();
  holidays.saveAll(in.days().stream().map(day->new Holiday(day.date(),day.name().strip())).toList());holidays.flush();
  bookings.audit(auth.getName(),"HOLIDAYS_UPDATED",Integer.toString(year));return list(year);
 }
}
