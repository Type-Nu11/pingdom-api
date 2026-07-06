package com.typenull.pingdom.notification.infrastructure.email;

public class PostmarkConfigurationException extends IllegalStateException {

    public PostmarkConfigurationException(String message) {
        super(message);
    }
}
