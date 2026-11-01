package vn.venusgiti.vticket;

import java.net.URI;
import jakarta.mail.internet.InternetAddress;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ticket-email")
public record TicketEmailProperties(boolean enabled, String from, String publicBaseUrl,
        boolean workerEnabled, int maxAttempts, int attachmentLimitBytes) {
    public TicketEmailProperties {
        if (maxAttempts < 1 || maxAttempts > 10 || attachmentLimitBytes < 1024 || attachmentLimitBytes > 20_000_000)
            throw new IllegalArgumentException("Email: max-attempts cần 1..10, attachment-limit-bytes cần 1024..20000000");
        if (enabled) {
            requireAddress(from);
            if (publicBaseUrl == null || publicBaseUrl.isBlank()) throw new IllegalArgumentException("Cần PUBLIC_BASE_URL để tạo đường dẫn tải vé trong email");
            URI uri = URI.create(publicBaseUrl);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))
                throw new IllegalArgumentException("PUBLIC_BASE_URL cần origin website công khai, không chứa path/query/tài khoản");
        }
    }
    static void requireAddress(String value) {
        try {
            if (value == null || value.contains("\r") || value.contains("\n")) throw new IllegalArgumentException();
            var addresses = InternetAddress.parse(value, true);
            if (addresses.length != 1 || addresses[0].getPersonal() != null || !addresses[0].getAddress().contains("@"))
                throw new IllegalArgumentException();
            addresses[0].validate();
        } catch (Exception e) { throw new IllegalArgumentException("Email cần một địa chỉ email hợp lệ"); }
    }
}
