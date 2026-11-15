package vn.venusgiti.vticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface Holidays extends JpaRepository<Holiday,LocalDate> {
 List<Holiday> findByDateGreaterThanEqualAndDateLessThanOrderByDate(LocalDate start,LocalDate end);
}
