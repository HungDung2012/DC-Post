package com.dcpost.customer.security;

import com.dcpost.customer.common.web.ApiProblems;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SecurityProblemWriter {
    private final ObjectMapper json;
    public SecurityProblemWriter(ObjectMapper json) { this.json = json; }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), ApiProblems.create(request, status, code, detail));
    }
}
