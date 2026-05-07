package com.monitor.call.enums;

public enum ErrorCodes {
    ERR001_UNSUPPORTED_ENTITY_TYPE("ERR001", "crossDataCoreException.error.unsupported.entity.type.title", "crossDataCoreException.error.unsupported.entity.type.message"),
    ERR002_ENTITY_CONSTRAINS_VALIDATION("ERR002", "crossDataCoreException.error.unsupported.entity.constrain.field.title", "crossDataCoreException.error.unsupported.entity.constrain.field.message"),
    ERR003_ENTITY_NOT_FOUND("ERR003", "crossDataCoreException.error.entity.not.found.title", "crossDataCoreException.error.entity.not.found.message");
    String id;
    String codeTitle;
    String codeMessage;

    private ErrorCodes(String id, String codeTitle, String codeMessage) {
        this.id = id;
        this.codeTitle = codeTitle;
        this.codeMessage = codeMessage;
    }

    public static ErrorCodes getEnum(String id) {
        for (ErrorCodes codigo : ErrorCodes.values()) {
            if (codigo.getId().equals(id)) {
                return codigo;
            }
        }
        return null;
    }

    public String getId() {
        return id;
    }

    public String getCodeMessage() {
        return codeMessage;
    }

    public String getCodeTitle() {
        return codeTitle;
    }
}

