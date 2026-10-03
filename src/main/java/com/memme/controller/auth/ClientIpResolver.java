package com.memme.controller.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    private static final Pattern IPV4_WITH_PORT = Pattern.compile("^([0-9.]+):[0-9]{1,5}$");
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]+$");

    private final boolean trustForwardedFor;

    public ClientIpResolver(@Value("${app.security.trust-x-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String[] addresses = forwardedFor.split(",");
                String lastAddress = addresses[addresses.length - 1].trim();
                String normalized = normalizeAddress(lastAddress);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        String remoteAddress = normalizeAddress(request.getRemoteAddr());
        return remoteAddress == null ? "unknown" : remoteAddress;
    }

    private String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String candidate = address.trim();
        if (candidate.startsWith("[") && candidate.contains("]")) {
            candidate = candidate.substring(1, candidate.indexOf(']'));
        } else {
            var matcher = IPV4_WITH_PORT.matcher(candidate);
            if (matcher.matches()) {
                candidate = matcher.group(1);
            }
        }
        if (!IP_LITERAL.matcher(candidate).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}
