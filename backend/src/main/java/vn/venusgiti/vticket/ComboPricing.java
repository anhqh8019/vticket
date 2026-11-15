package vn.venusgiti.vticket;
import org.springframework.stereotype.Service;
import java.time.*;
import java.math.BigDecimal;
import vn.venusgiti.vticket.Domain.*;
@Service
public class ComboPricing {
 private final Holidays holidays;
 ComboPricing(Holidays holidays){this.holidays=holidays;}
 public String dayType(LocalDate date){return holidays.existsById(date)?"HOLIDAY":date.getDayOfWeek()==DayOfWeek.SATURDAY||date.getDayOfWeek()==DayOfWeek.SUNDAY?"WEEKEND":"WEEKDAY";}
 public BigDecimal price(Product product,String dayType){
  if(product.kind==Kind.COMBO){
   if(dayType.equals("HOLIDAY")&&product.holidayPrice!=null)return product.holidayPrice;
   if(dayType.equals("WEEKEND")&&product.weekendPrice!=null)return product.weekendPrice;
  }
  return product.price;
 }
}
