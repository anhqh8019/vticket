package vn.venusgiti.vticket;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name="holidays")
public class Holiday {
 @Id @Column(name="holiday_date") public LocalDate date;
 @Column(nullable=false,length=150) public String name;
 public Holiday(){}
 public Holiday(LocalDate date,String name){this.date=date;this.name=name;}
}
