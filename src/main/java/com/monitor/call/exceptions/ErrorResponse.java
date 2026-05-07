package com.monitor.call.exceptions;
import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse implements Serializable {
	private static final long serialVersionUID = -1187310565357890122L;
	private String id;
	private String title;
	private String message;
	private String data;
	private transient HttpStatus httpStatus;

	@Override
	public String toString() {
		return new Gson().toJson(this);
	}
}
