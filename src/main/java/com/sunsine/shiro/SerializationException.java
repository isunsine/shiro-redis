package com.sunsine.shiro;
public class SerializationException extends Exception {
	/**
	 * @Fields serialVersionUID : TODO(用一句话描述这个变量表示什么)
	 */
	private static final long serialVersionUID = 1L;
	public SerializationException(String msg) {
		super(msg);
	}
	public SerializationException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
