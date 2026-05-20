package com.nexusai.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusai.gateway.AbstractIntegrationTest;
import com.nexusai.gateway.auth.dto.LoginRequest;
import com.nexusai.gateway.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void register_validRequest_returns201WithToken() throws Exception {
        var request = new RegisterRequest("Globo Tech", "admin@globotech.com", "secure1234");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.type").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber());
    }

    @Test
    void register_duplicateEmail_returns400() throws Exception {
        var request = new RegisterRequest("Dup Corp", "dup@dupmail.com", "secure1234");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("already registered")));
    }

    @Test
    void register_blankEmail_returns400WithFieldErrors() throws Exception {
        var request = new RegisterRequest("Corp", "", "pass1234");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", hasSize(greaterThan(0))));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        var request = new RegisterRequest("Corp", "user@corp.com", "short");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Login Corp", "login@corp.com", "password99"))))
                .andExpect(status().isCreated());

        var loginReq = new LoginRequest("login@corp.com", "password99");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Wrong Pass Co", "wrongpass@corp.com", "realpassword"))))
                .andExpect(status().isCreated());

        var loginReq = new LoginRequest("wrongpass@corp.com", "notthepassword");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownEmail_returns401() throws Exception {
        var loginReq = new LoginRequest("ghost@nobody.com", "whatever");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_withValidToken_returnsUserProfile() throws Exception {
        var registerResp = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Me Corp", "me@me.com", "password123"))))
                .andExpect(status().isCreated())
                .andReturn();

        String body = registerResp.getResponse().getContentAsString();
        String token = objectMapper.readTree(body).at("/data/token").asText();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me@me.com"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.tenantId").isNotEmpty());
    }

    @Test
    void getMe_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_asAdmin_returnsUsersInSameTenant() throws Exception {
        var registerResp = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("List Corp", "admin@listcorp.com", "password123"))))
                .andExpect(status().isCreated())
                .andReturn();

        String body = registerResp.getResponse().getContentAsString();
        String token = objectMapper.readTree(body).at("/data/token").asText();

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].email").value("admin@listcorp.com"));
    }
}
