package com.monitor.call.enums;

public enum ErrorCodes {

    // Existentes
    ERR001_UNSUPPORTED_ENTITY_TYPE(
        "ERR001",
        "crossDataCoreException.error.unsupported.entity.type.title",
        "crossDataCoreException.error.unsupported.entity.type.message"),
    ERR002_ENTITY_CONSTRAINS_VALIDATION(
        "ERR002",
        "crossDataCoreException.error.unsupported.entity.constrain.field.title",
        "crossDataCoreException.error.unsupported.entity.constrain.field.message"),
    ERR003_ENTITY_NOT_FOUND(
        "ERR003",
        "crossDataCoreException.error.entity.not.found.title",
        "crossDataCoreException.error.entity.not.found.message"),

    // Autenticacion
    ERR004_INVALID_CREDENTIALS(
        "ERR004",
        "auth.error.invalid.credentials.title",
        "auth.error.invalid.credentials.message"),
    ERR005_EMAIL_ALREADY_REGISTERED(
        "ERR005",
        "auth.error.email.already.registered.title",
        "auth.error.email.already.registered.message"),
    ERR006_USER_NOT_FOUND(
        "ERR006",
        "auth.error.user.not.found.title",
        "auth.error.user.not.found.message"),
    ERR007_WRONG_PASSWORD(
        "ERR007",
        "auth.error.wrong.password.title",
        "auth.error.wrong.password.message"),

    // Agentes
    ERR008_EXTENSION_EXISTS(
        "ERR008",
        "agent.error.extension.exists.title",
        "agent.error.extension.exists.message"),
    ERR009_AGENT_NOT_FOUND(
        "ERR009",
        "agent.error.not.found.title",
        "agent.error.not.found.message"),
    ERR010_GROUP_NOT_FOUND(
        "ERR010",
        "agent.error.group.not.found.title",
        "agent.error.group.not.found.message"),

    // Leads
    ERR011_LEAD_NOT_FOUND(
        "ERR011",
        "lead.error.not.found.title",
        "lead.error.not.found.message"),
    ERR012_CSV_PARSE_ERROR(
        "ERR012",
        "lead.error.csv.parse.title",
        "lead.error.csv.parse.message"),

    // Tipificacion
    ERR013_ALREADY_TYPIFIED(
        "ERR013",
        "typification.error.already.typified.title",
        "typification.error.already.typified.message"),
    ERR014_TYPIFICATION_NOT_FOUND(
        "ERR014",
        "typification.error.not.found.title",
        "typification.error.not.found.message"),

    // Webhook
    ERR015_IP_NOT_ALLOWED(
        "ERR015",
        "webhook.error.ip.not.allowed.title",
        "webhook.error.ip.not.allowed.message");

    private final String id;
    private final String codeTitle;
    private final String codeMessage;

    ErrorCodes(String id, String codeTitle, String codeMessage) {
        this.id = id;
        this.codeTitle = codeTitle;
        this.codeMessage = codeMessage;
    }

    public static ErrorCodes getEnum(String id) {
        for (ErrorCodes code : ErrorCodes.values()) {
            if (code.getId().equals(id)) return code;
        }
        return null;
    }

    public String getId() { return id; }
    public String getCodeTitle() { return codeTitle; }
    public String getCodeMessage() { return codeMessage; }
}
