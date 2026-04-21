package com.authservice.codesync.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 30)
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8)
    private String password;

    private String fullName;

    public RegisterRequest() {}

    public RegisterRequest(String username, String email, String password, String fullName) {
        this.username = username;
        this.email    = email;
        this.password = password;
        this.fullName = fullName;
    }

    public String getUsername()            { return username; }
    public void   setUsername(String v)    { this.username = v; }

    public String getEmail()               { return email; }
    public void   setEmail(String v)       { this.email = v; }

    public String getPassword()            { return password; }
    public void   setPassword(String v)    { this.password = v; }

    public String getFullName()            { return fullName; }
    public void   setFullName(String v)    { this.fullName = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String username, email, password, fullName;
        public Builder username(String v) { this.username = v; return this; }
        public Builder email(String v)    { this.email = v;    return this; }
        public Builder password(String v) { this.password = v; return this; }
        public Builder fullName(String v) { this.fullName = v; return this; }
        public RegisterRequest build()    { return new RegisterRequest(username, email, password, fullName); }
    }
}
