package vn.venusgiti.vticket;

import org.junit.jupiter.api.Test;
import org.springframework.mail.*;
import jakarta.mail.*;
import java.io.*;
import java.net.*;
import java.util.Map;
import javax.net.ssl.SSLHandshakeException;
import static org.junit.jupiter.api.Assertions.*;

class TicketEmailFailureTest {
    @Test void authenticationFailureNeverExposesProviderText(){
        assertEquals("SMTP_AUTH_FAILED",TicketEmailFailure.classify(new MailAuthenticationException("secret-password smtp-user@example.com private-token")).name());
    }
    @Test void traversesJavaMailNextExceptionAndFailedMessageCollections(){
        var next=new MessagingException("private provider reply");next.setNextException(new UnknownHostException("smtp.private.test"));
        assertEquals(TicketEmailFailure.SMTP_DNS_FAILED,TicketEmailFailure.classify(new MailSendException("send failed",next)));
        assertEquals(TicketEmailFailure.SMTP_TIMEOUT,TicketEmailFailure.classify(new MailSendException(Map.of("message",new SocketTimeoutException("private host")))));
    }
    @Test void distinguishesTlsConnectionAndFileFailures(){
        assertEquals(TicketEmailFailure.SMTP_TLS_FAILED,TicketEmailFailure.classify(new MailSendException("failed",new SSLHandshakeException("certificate"))));
        assertEquals(TicketEmailFailure.SMTP_CONNECT_FAILED,TicketEmailFailure.classify(new MailSendException("failed",new ConnectException("private host"))));
        assertEquals(TicketEmailFailure.TICKET_FILE_FAILED,TicketEmailFailure.classify(new IOException("file with token")));
        assertEquals(TicketEmailFailure.EMAIL_MESSAGE_INVALID,TicketEmailFailure.classify(new IllegalArgumentException("bad recipient")));
        assertEquals(TicketEmailFailure.SMTP_SEND_FAILED,TicketEmailFailure.classify(new MailSendException("private provider text")));
    }
}
