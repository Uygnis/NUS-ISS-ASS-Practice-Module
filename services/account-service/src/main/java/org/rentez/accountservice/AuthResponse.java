package org.rentez.accountservice;

public class AuthResponse {

    private final String email;
    private final String name;
    private final String phone;

    public AuthResponse(String email, String name, String phone) {
        this.email = email;
        this.name = name;
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}
