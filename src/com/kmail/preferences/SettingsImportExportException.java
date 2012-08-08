package com.kmail.preferences;

public class SettingsImportExportException extends Exception {

    public SettingsImportExportException() {
        super();
    }

    public SettingsImportExportException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public SettingsImportExportException(String detailMessage) {
        super(detailMessage);
    }

    public SettingsImportExportException(Throwable throwable) {
        super(throwable);
    }

}
