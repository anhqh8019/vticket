package vn.venusgiti.vticket;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Set;
import vn.venusgiti.vticket.Domain.*;
@Component @ConditionalOnProperty(name="app.seed-data",havingValue="true")
public class DemoData implements ApplicationRunner {
 private final Products products; private final InventoryService inventory;
 DemoData(Products p,InventoryService i){products=p;inventory=i;}
 @Transactional public void run(ApplicationArguments args){if(products.count()>0)return;Product spa=add("SPA30","Massage 30 phút",Kind.SERVICE,200000,"Thư giãn với liệu trình massage 30 phút");add("BED","Thuê giường nghỉ",Kind.SERVICE,100000,"Giường nghỉ trong ngày");Product mineral=add("MINERAL","Tắm khoáng thiên nhiên",Kind.TICKET,250000,"Hồ khoáng nóng và hồ bơi ngoài trời");add("MUD","Tắm bùn thư giãn",Kind.TICKET,350000,"Tắm bùn khoáng, tắm tráng và hồ bơi");Product combo=add("WELLNESS","Wellness trọn ngày",Kind.COMBO,650000,"Tắm khoáng và massage 30 phút");combo.includedServiceIds=Set.of(spa.id);combo.comboTicketId=mineral.id;combo.comboBuyQuantity=1;combo.comboGiftQuantity=0;var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));for(var p:products.findAll())if(p.kind==Kind.TICKET)inventory.createBatch(new Requests.BatchInput("DEMO-"+p.code,p.id,100,today,today.plusYears(1)),"system");}
 private Product add(String code,String name,Kind kind,int price,String description){Product p=new Product();p.code=code;p.name=name;p.kind=kind;p.price=BigDecimal.valueOf(price);p.description=description;return products.save(p);}
}
