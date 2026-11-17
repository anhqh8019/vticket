package vn.venusgiti.vticket;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SmileSerialFormatTest {
 @Test void serialAlwaysHasExactlyTenDigitsAfterVn(){
  assertEquals("VN0000022924",SmileGateway.serialForSeq("22924"));
  assertEquals("VN0000000001",SmileGateway.serialForSeq("1"));
  assertEquals("VN9999999999",SmileGateway.serialForSeq("9999999999"));
  assertEquals(12,SmileGateway.serialForSeq("22924").length());
 }
 @Test void invalidOrOversizedSeqIsRejectedInsteadOfTruncated(){
  for(String value:new String[]{null,"","-1","123x","10000000000"})assertThrows(IllegalStateException.class,()->SmileGateway.serialForSeq(value));
 }
}
