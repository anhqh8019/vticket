package vn.venusgiti.vticket;
import org.springframework.stereotype.Service;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

@Service
public class VietQrCallbackTokens {
 private static final String ISSUER="vticket-vietqr-callback",SCOPE="vietqr:transaction-sync";
 private final VietQrProperties properties;private final Clock clock;
 VietQrCallbackTokens(VietQrProperties p,Clock c){properties=p;clock=c;}
 public String create(){try{var now=clock.instant();var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),new JWTClaimsSet.Builder().issuer(ISSUER).audience("vticket").subject(properties.callback().username()).claim("scope",SCOPE).issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(properties.callback().tokenExpirationSeconds()))).jwtID(UUID.randomUUID().toString()).build());jwt.sign(new MACSigner(key()));return jwt.serialize();}catch(JOSEException e){throw new IllegalStateException("Không phát hành được callback token",e);}}
 public boolean valid(String value){if(!properties.enabled())return false;try{var jwt=SignedJWT.parse(value);if(!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())||!jwt.verify(new MACVerifier(key())))return false;var c=jwt.getJWTClaimsSet();var now=clock.instant();return ISSUER.equals(c.getIssuer())&&c.getAudience().contains("vticket")&&properties.callback().username().equals(c.getSubject())&&SCOPE.equals(c.getStringClaim("scope"))&&c.getExpirationTime()!=null&&c.getExpirationTime().toInstant().isAfter(now)&&c.getIssueTime()!=null&&!c.getIssueTime().toInstant().isAfter(now)&&!c.getExpirationTime().toInstant().isAfter(c.getIssueTime().toInstant().plusSeconds(properties.callback().tokenExpirationSeconds()));}catch(Exception e){return false;}}
 private byte[] key(){return properties.callback().jwtSecret().getBytes(StandardCharsets.UTF_8);}
}
