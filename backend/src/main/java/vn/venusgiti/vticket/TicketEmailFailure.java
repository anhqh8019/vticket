package vn.venusgiti.vticket;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import org.springframework.mail.*;
import java.io.IOException;
import java.net.*;
import java.security.cert.CertificateException;
import java.util.*;
import javax.net.ssl.SSLException;

/** Classifies nested JavaMail failures without recording SMTP messages, credentials or ticket URLs. */
enum TicketEmailFailure {
    SMTP_AUTH_FAILED, SMTP_DNS_FAILED, SMTP_CONNECT_FAILED, SMTP_TIMEOUT,
    SMTP_TLS_FAILED, SMTP_ADDRESS_REJECTED, SMTP_SEND_FAILED,
    TICKET_FILE_FAILED, EMAIL_MESSAGE_INVALID, DELIVERY_FAILED_CHECK_SMTP;

    static TicketEmailFailure classify(Throwable error) {
        var pending=new ArrayDeque<Throwable>();pending.add(error);
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(!pending.isEmpty() && seen.size()<64){
            var current=pending.removeFirst();if(!seen.add(current))continue;
            if(current.getCause()!=null)pending.add(current.getCause());
            if(current instanceof MessagingException mail && mail.getNextException()!=null)pending.add(mail.getNextException());
            if(current instanceof MailSendException mail)for(var nested:mail.getMessageExceptions())pending.add(nested);
        }
        if(seen.stream().anyMatch(t->t instanceof MailAuthenticationException || t instanceof AuthenticationFailedException))return SMTP_AUTH_FAILED;
        if(seen.stream().anyMatch(t->t instanceof SSLException || t instanceof CertificateException))return SMTP_TLS_FAILED;
        if(seen.stream().anyMatch(t->t instanceof UnknownHostException))return SMTP_DNS_FAILED;
        if(seen.stream().anyMatch(t->t instanceof SocketTimeoutException))return SMTP_TIMEOUT;
        if(seen.stream().anyMatch(t->t instanceof ConnectException || t instanceof NoRouteToHostException))return SMTP_CONNECT_FAILED;
        if(seen.stream().anyMatch(t->t.getClass().getSimpleName().equals("SMTPAddressFailedException")))return SMTP_ADDRESS_REJECTED;
        if(seen.stream().anyMatch(t->t instanceof MailException))return SMTP_SEND_FAILED;
        if(seen.stream().anyMatch(t->t instanceof IOException))return TICKET_FILE_FAILED;
        if(seen.stream().anyMatch(t->t instanceof MessagingException || t instanceof IllegalArgumentException))return EMAIL_MESSAGE_INVALID;
        return DELIVERY_FAILED_CHECK_SMTP;
    }
}
