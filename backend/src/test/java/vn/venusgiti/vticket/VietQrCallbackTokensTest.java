package vn.venusgiti.vticket;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VietQrCallbackTokensTest {
 @Test void validatesSignatureExpiryAndSeparatesScopes(){var p=VietQrApiClientTest.properties();var now=Instant.parse("2026-10-24T00:00:00Z");var tokens=new VietQrCallbackTokens(p,Clock.fixed(now,ZoneOffset.UTC));String value=tokens.create();assertTrue(tokens.valid(value));assertFalse(new VietQrCallbackTokens(p,Clock.fixed(now.plusSeconds(301),ZoneOffset.UTC)).valid(value));assertFalse(new VietQrCallbackTokens(VietQrApiClientTest.properties(),Clock.fixed(now,ZoneOffset.UTC)).valid(value));assertFalse(tokens.valid(value.substring(0,value.lastIndexOf('.')+1)+"invalid-signature"));assertFalse(tokens.valid("not-a-jwt"));}
}
