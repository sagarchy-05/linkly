package com.sagar.linkly.exception;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String shortCode) {
        super("Link not found for code: " + shortCode);
    }
}