package io.github.lujian213.simulator.webbit;

import java.io.IOException;

import org.webbitserver.WebSocketConnection;

import io.github.lujian213.simulator.SimResponse;
import io.github.lujian213.simulator.util.ReqRespConvertor;

public class DefaultWebbitWSReqRespConvertor implements ReqRespConvertor {

	@Override
	public String rawRequestToBody(Object rawRequest) throws IOException {
		if (rawRequest == null)
			return null;
		return new String((byte[])rawRequest);
	}

	@Override
	public void fillRawResponse(Object rawResponse, SimResponse simResponse) throws IOException {
		((WebSocketConnection)rawResponse).send(simResponse.getBodyAsString());
	}
}
