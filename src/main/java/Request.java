import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URLEncodedUtils;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Request {
    private final String method;
    private final String path;
    private final Map<String, String> queryParams;
    private final String protocol;

    public Request(String requestLine) {
        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid request line: " + requestLine);
        }

        this.method = parts[0];
        this.protocol = parts[2];

        String[] pathParts = parts[1].split("\\?", 2);
        this.path = pathParts[0];

        if (pathParts.length > 1) {
            List<NameValuePair> params = URLEncodedUtils.parse(pathParts[1], StandardCharsets.UTF_8);
            this.queryParams = params.stream()
                    .collect(Collectors.toMap(
                            NameValuePair::getName,
                            NameValuePair::getValue,
                            (oldValue, newValue) -> newValue));
        } else {
            this.queryParams = Collections.emptyMap();
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getQueryParam(String name) {
        return queryParams.get(name);
    }

    public Map<String, String> getQueryParams() {
        return Collections.unmodifiableMap(queryParams);
    }

    public String getProtocol() {
        return protocol;
    }
}