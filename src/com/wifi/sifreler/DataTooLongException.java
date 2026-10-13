package com.wifi.sifreler;

public class DataTooLongException extends IllegalArgumentException {
    public DataTooLongException(String msg) {
        super(msg);
    }
}
