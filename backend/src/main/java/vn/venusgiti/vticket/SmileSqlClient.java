package vn.venusgiti.vticket;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Isolated Smile connection. No Flyway, DDL, UPDATE or DELETE is executed against Smile. */
@Service
public class SmileSqlClient implements SmileGateway {
 private final SmileProperties props;private final Clock clock;
 SmileSqlClient(SmileProperties p,Clock c){props=p;clock=c;}
 private Connection connection() throws SQLException {
  if(!props.enabled()||!props.configured())throw new IllegalStateException("Chưa bật/cấu hình đủ kết nối Smile");
  var options=new Properties();options.setProperty("user",props.username());options.setProperty("password",props.password());options.setProperty("loginTimeout","5");options.setProperty("socketTimeout","15000");options.setProperty("applicationName","Venusgiti-vticket-Smile");return DriverManager.getConnection(props.jdbcUrl(),options);
 }
 private String target(){return "["+props.schema()+"].["+props.table()+"]";}
 private static String key(String s){return s.toLowerCase(Locale.ROOT);}
 private List<Column> columns(Connection c)throws SQLException{
  List<Column> out=new ArrayList<>();try(var rows=c.getMetaData().getColumns(c.getCatalog(),props.schema(),props.table(),null)){while(rows.next())if(props.table().equalsIgnoreCase(rows.getString("TABLE_NAME"))&&props.schema().equalsIgnoreCase(rows.getString("TABLE_SCHEM")))out.add(new Column(rows.getString("COLUMN_NAME"),rows.getString("TYPE_NAME"),rows.getInt("COLUMN_SIZE"),rows.getInt("NULLABLE")!=DatabaseMetaData.columnNoNulls,"YES".equalsIgnoreCase(rows.getString("IS_AUTOINCREMENT")),rows.getString("COLUMN_DEF")!=null));}return out;
 }
 private void validate(List<Column> columns){
  var names=new HashSet<>(columns.stream().map(c->key(c.name())).toList());
  for(String field:List.of("seqno","barcode","serial","pkgcode","fromdate","todate","isweekday","ischild","iscancel","isloss","disabled"))if(!names.contains(field))throw new IllegalStateException("TicketOnsen thiếu cột bắt buộc: "+field);
  var seq=columns.stream().filter(c->key(c.name()).equals("seqno")).findFirst().orElseThrow();if(!seq.identity()&&!seq.hasDefault())throw new IllegalStateException("SeqNo chưa có IDENTITY/DEFAULT. Cần DDL và quy tắc cấp SeqNo của Smile; không tự lấy MAX+1");
  Set<String> supplied=new HashSet<>(List.of("barcode","serial","pkgcode","fromdate","todate","isweekday","ischild","iscancel","isloss","disabled","createtime","lastchangetime","note"));if(!SmileProperties.blank(props.hotelId()))supplied.add("hotelid");if(!SmileProperties.blank(props.clerkId()))supplied.addAll(List.of("createclerkid","lastchangeclerkid"));
  for(var column:columns)if(!column.nullable()&&!column.identity()&&!column.hasDefault()&&!supplied.contains(key(column.name())))throw new IllegalStateException("Cần cấu hình/kiểm tra trường bắt buộc Smile: "+column.name());
 }
 @Override public List<Column> inspect(){try(var c=connection()){var cols=columns(c);validate(cols);return cols;}catch(SQLException e){throw safe(e);}}
 private IllegalStateException safe(SQLException e){return new IllegalStateException("Không kết nối/ghi được Smile (SQLState "+e.getSQLState()+", mã "+e.getErrorCode()+"). Kiểm tra kết nối, kiểu dữ liệu và quyền tài khoản.");}
 @Override public List<Synced> sync(Batch batch){
  try(var c=connection()){
   var cols=columns(c);validate(cols);c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
   try{List<Synced> result=new ArrayList<>();for(var code:batch.codes()){
     var existing=find(c,code);if(existing==null){insert(c,cols,batch,code);existing=find(c,code);}if(existing==null)throw new IllegalStateException("Smile chưa trả lại mã vừa ghi");verify(existing,batch,code);result.add(new Synced(code.stockId(),existing.seq(),code.barcode()));
    }c.commit();return result;
   }catch(Exception e){c.rollback();if(e instanceof SQLException sql)throw safe(sql);if(e instanceof RuntimeException runtime)throw runtime;throw new IllegalStateException("Không hoàn tất đồng bộ Smile");}
  }catch(SQLException e){throw safe(e);}
 }
 private record Existing(String seq,String barcode,String serial,String pkg,LocalDate from,LocalDate until,boolean weekday,boolean child,boolean cancelled,boolean lost,boolean disabled) {}
 private Existing find(Connection c,Code code)throws SQLException{
  String sql="SELECT SeqNo,BarCode,Serial,PkgCode,FromDate,ToDate,IsWeekday,IsChild,IsCancel,IsLoss,Disabled FROM "+target()+" WITH (UPDLOCK,HOLDLOCK) WHERE BarCode=? OR Serial=?";
  try(var st=c.prepareStatement(sql)){st.setQueryTimeout(15);st.setString(1,code.barcode());st.setString(2,code.serial());try(var rs=st.executeQuery()){if(!rs.next())return null;var row=new Existing(rs.getString(1),clean(rs.getString(2)),clean(rs.getString(3)),clean(rs.getString(4)),rs.getDate(5)==null?null:rs.getDate(5).toLocalDate(),rs.getDate(6)==null?null:rs.getDate(6).toLocalDate(),rs.getBoolean(7),rs.getBoolean(8),rs.getBoolean(9),rs.getBoolean(10),rs.getBoolean(11));if(rs.next())throw new IllegalStateException("Nhiều bản ghi Smile trùng BarCode/Serial; cần đối soát");return row;}}
 }
 private String clean(String value){return value==null?null:value.stripTrailing();}
 private void verify(Existing r,Batch b,Code c){if(!Objects.equals(r.barcode(),c.barcode())||!Objects.equals(r.serial(),c.serial())||!Objects.equals(r.pkg(),b.pkgCode())||!Objects.equals(r.from(),b.fromDate())||!Objects.equals(r.until(),b.toDate())||r.weekday()!=b.weekday()||r.child()!=b.child()||r.cancelled()||r.lost()||r.disabled())throw new IllegalStateException("BarCode "+c.barcode()+" / Serial "+c.serial()+" đã có trên Smile nhưng thông tin hoặc trạng thái không khớp; không ghi đè");}
 private void insert(Connection c,List<Column> columns,Batch b,Code code)throws SQLException{
  var values=new LinkedHashMap<String,Object>();values.put("barcode",code.barcode());values.put("serial",code.serial());values.put("pkgcode",b.pkgCode());values.put("fromdate",Timestamp.valueOf(b.fromDate().atStartOfDay()));values.put("todate",Timestamp.valueOf(b.toDate().atStartOfDay()));values.put("isweekday",b.weekday());values.put("ischild",b.child());values.put("iscancel",false);values.put("isloss",false);values.put("disabled",false);var now=Timestamp.valueOf(LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Ho_Chi_Minh")));values.put("createtime",now);values.put("lastchangetime",now);values.put("note","VTICKET "+b.code());if(!SmileProperties.blank(props.hotelId()))values.put("hotelid",props.hotelId());if(!SmileProperties.blank(props.clerkId())){values.put("createclerkid",props.clerkId());values.put("lastchangeclerkid",props.clerkId());}
  List<String> names=new ArrayList<>();List<Object> args=new ArrayList<>();for(var col:columns){String k=key(col.name());if(col.identity()||!values.containsKey(k))continue;Object value=values.get(k);if(value instanceof String str&&col.type().toLowerCase(Locale.ROOT).contains("char")&&str.length()>col.size()){if(k.equals("note"))value=str.substring(0,col.size());else throw new IllegalStateException("Dữ liệu vượt độ dài cột Smile: "+col.name());}names.add("["+col.name()+"]");args.add(value);}
  String sql="INSERT INTO "+target()+" ("+String.join(",",names)+") VALUES ("+String.join(",",Collections.nCopies(args.size(),"?"))+")";try(var st=c.prepareStatement(sql)){st.setQueryTimeout(15);for(int i=0;i<args.size();i++)st.setObject(i+1,args.get(i));st.executeUpdate();}
 }
}
