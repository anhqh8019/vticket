package vn.venusgiti.vticket;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("app.smile")
public record SmileProperties(boolean enabled,String jdbcUrl,String username,String password,String schema,String table,String hotelId,String clerkId,String codeMapping,boolean workerEnabled) {
 public boolean configured(){return !blank(jdbcUrl)&&!blank(username)&&!blank(password);}
 static boolean blank(String v){return v==null||v.isBlank();}
 public SmileProperties {if(!java.util.Set.of("VN_AS_BARCODE","FIELD_NAMES").contains(codeMapping))throw new IllegalArgumentException("SMILE_CODE_MAPPING cần VN_AS_BARCODE hoặc FIELD_NAMES");if(schema==null||!schema.matches("[A-Za-z_][A-Za-z0-9_]*")||table==null||!table.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("Smile schema/table chỉ được chứa chữ, số và dấu gạch dưới");if(!blank(jdbcUrl)&&(!jdbcUrl.startsWith("jdbc:sqlserver://")||jdbcUrl.toLowerCase(java.util.Locale.ROOT).matches(".*;(?:user|username|password|pwd)=.*")))throw new IllegalArgumentException("SMILE_DB_URL cần JDBC SQL Server; cấu hình tài khoản riêng qua biến môi trường");}
 @Override public String toString(){return "SmileProperties[enabled="+enabled+", credentials=redacted]";}
}
