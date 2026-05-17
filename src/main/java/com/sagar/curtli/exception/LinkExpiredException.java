package com.sagar.curtli.exception;

public class LinkExpiredException extends RuntimeException {
    public LinkExpiredException(String shortCode) {
        super("The link associated with code '" + shortCode + "' has expired.");
    }
}