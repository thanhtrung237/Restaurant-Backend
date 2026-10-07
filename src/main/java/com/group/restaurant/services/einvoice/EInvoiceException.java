package com.group.restaurant.services.einvoice;


public class EInvoiceException extends Exception {

    private final String providerName;

    public EInvoiceException(String providerName, String message) {
        super(message);
        this.providerName = providerName;
    }

    public EInvoiceException(String providerName, String message, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }
}
