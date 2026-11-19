package io.github.vedmider.sestacon.http;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

/** Client IP = first X-Forwarded-For hop when present, else the socket peer address. */
public final class ClientIp {

    private ClientIp() {
    }

    /** The hop our proxy appended (rightmost X-Forwarded-For entry); left entries are client-supplied and spoofable. */
    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    /** True if {@code address} equals or falls inside one of {@code proxies} (IPs or IPv4 CIDRs); empty = any. */
    public static boolean isTrustedPeer(String address, java.util.List<String> proxies) {
        if (proxies == null || proxies.isEmpty()) {
            return true;
        }
        for (String p : proxies) {
            String rule = p.trim();
            if (rule.equals(address) || (rule.contains("/") && inCidr(address, rule))) {
                return true;
            }
        }
        return false;
    }

    private static boolean inCidr(String address, String cidr) {
        try {
            String[] parts = cidr.split("/");
            int bits = Integer.parseInt(parts[1]);
            long mask = bits == 0 ? 0 : 0xFFFFFFFFL << (32 - bits) & 0xFFFFFFFFL;
            return (ipv4(address) & mask) == (ipv4(parts[0]) & mask);
        }
        catch (RuntimeException notIpv4) {
            return false;
        }
    }

    private static long ipv4(String address) {
        String[] o = address.split("\\.");
        if (o.length != 4) {
            throw new IllegalArgumentException(address);
        }
        long v = 0;
        for (String octet : o) {
            int n = Integer.parseInt(octet);
            if (n < 0 || n > 255) {
                throw new IllegalArgumentException(address);
            }
            v = v << 8 | n;
        }
        return v;
    }
}
