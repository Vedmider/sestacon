package com.example.rag.http;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

/** Client IP = first X-Forwarded-For hop when present, else the socket peer address. */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
