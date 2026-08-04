package org.rentez.accountservice;

public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}
