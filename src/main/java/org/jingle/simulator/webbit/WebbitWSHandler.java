package org.jingle.simulator.webbit;

import org.apache.log4j.Logger;
import org.jingle.simulator.SimScript;
import org.jingle.simulator.util.SimLogger;
import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebSocketConnection;

public class WebbitWSHandler extends BaseWebSocketHandler {
	private final static String TYPE_OPEN = "OPEN";
	private final static String TYPE_CLOSE = "CLOSE";
	private final static String TYPE_MESSAGE = "MESSAGE";
	
    private String name;
    private SimScript script;
    private WebSocketConnection connection;
    private WebbitWSHandlerBundle bundle;
    
    public WebbitWSHandler(String name, SimScript script, WebbitWSHandlerBundle bundle) {
    	this.name = name;
    	this.script = script;
    	this.bundle = bundle;
    	bundle.addHandler(this);
    }

    public String getName() {
		return name;
	}

	public void onOpen(WebSocketConnection connection) {
		SimLogger.setLogger(script.getLogger());
    	try {
    		this.connection = connection;
	    	WebbitWSSimRequest request = new WebbitWSSimRequest(bundle, name, TYPE_OPEN, null);
	    	script.genResponse(request);
    	} catch (Exception e) {
    		SimLogger.getLogger().error("error when open WS [" + name + "]", e);
    	}
    }

    public void onClose(WebSocketConnection connection) {
		SimLogger.setLogger(script.getLogger());
    	try {
	    	WebbitWSSimRequest request = new WebbitWSSimRequest(bundle, name, TYPE_CLOSE, null);
	    	script.genResponse(request);
    	} catch (Exception e) {
    		SimLogger.getLogger().error("error when close WS [" + name + "]", e);
    	}
    }

    public void onMessage(WebSocketConnection connection, String message) {
		SimLogger.setLogger(script.getLogger());
    	try {
	    	WebbitWSSimRequest request = new WebbitWSSimRequest(bundle, name, TYPE_MESSAGE, message);
	    	script.genResponse(request);
    	} catch (Exception e) {
    		SimLogger.getLogger().error("error when handle message in WS [" + name + "]", e);
    	}
    }

    public void sendMessage(String message) {
    	connection.send(message);
    }
}