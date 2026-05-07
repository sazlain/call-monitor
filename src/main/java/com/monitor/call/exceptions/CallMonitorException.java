package com.monitor.call.exceptions;

import com.monitor.call.enums.ErrorCodes;
import org.springframework.http.HttpStatus;

public class CallMonitorException extends Exception  {
	
	private static final long serialVersionUID = 2977144086681254609L;
	private final ErrorResponse error;
	
	public CallMonitorException(ErrorCodes errorCode, SupportMessages message, HttpStatus httpStatus, String ... customMessage) {
		super(message.getMessage(errorCode.getCodeTitle()) + ". " + message.getMessage(errorCode.getCodeMessage(), customMessage));
		error = new ErrorResponse();
		error.setId(errorCode.getId());
		error.setTitle(message.getMessage(errorCode.getCodeTitle()));
		error.setMessage(message.getMessage(errorCode.getCodeMessage(), customMessage));
		error.setHttpStatus(httpStatus);
	}
	
	public ErrorResponse getError() {
		return error;
	}
}