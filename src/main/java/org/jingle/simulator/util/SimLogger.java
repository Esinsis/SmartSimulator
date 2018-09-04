package org.jingle.simulator.util;

import org.apache.log4j.Logger;

public class SimLogger {
	private static ThreadLocal<Logger> loggerContainer = new ThreadLocal<>();
	
	public static void setLogger(Logger logger) {
		loggerContainer.set(logger);
	}

	public static Logger getLogger() {
		return loggerContainer.get();
	}
}
