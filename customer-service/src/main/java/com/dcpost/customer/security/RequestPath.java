package com.dcpost.customer.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

final class RequestPath {
    private RequestPath() {}
    static String withinApplication(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }
}
