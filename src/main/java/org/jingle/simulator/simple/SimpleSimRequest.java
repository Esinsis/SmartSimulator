package org.jingle.simulator.simple;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.jingle.simulator.AbstractSimRequest;
import org.jingle.simulator.SimResponse;
import org.jingle.simulator.util.ReqRespConvertor;
import org.jingle.simulator.util.SimLogger;
import org.jingle.simulator.util.SimUtils;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

@SuppressWarnings("restriction")
public class SimpleSimRequest extends AbstractSimRequest {
	private static final String TOP_LINE_FORMAT = "%s %s %s";
	private static final String HEADER_LINE_FORMAT = "%s: %s";
	private static final String AUTHENTICATION_LINE_FORMAT = "Authentication: %s,%s";
	private HttpExchange httpExchange;
	private String topLine;
	private Map<String, List<String>> headers;
	private String[] authentications = new String[] {"", ""};
	private String body;
	private ReqRespConvertor convertor;
	
	public SimpleSimRequest(HttpExchange exchange, ReqRespConvertor convertor) throws IOException {
		this.httpExchange = exchange;
		this.convertor = convertor;
		String method = exchange.getRequestMethod();
		URI uri = exchange.getRequestURI();
		String protocol = exchange.getProtocol();
		this.topLine = SimUtils.formatString(TOP_LINE_FORMAT, method, SimUtils.decodeURL(uri.toASCIIString()), protocol);
		genAuthentications(exchange);
		genHeaders(exchange);
		genBody(exchange);
	}
	
	protected SimpleSimRequest() {
		
	}
	
	@Override
	public ReqRespConvertor getReqRespConvertor() {
		return this.convertor;
	}
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(this.topLine).append("\n");
		for (String key: headers.keySet()) {
			sb.append(this.getHeaderLine(key)).append("\n");
		}
		sb.append("\n");
		if (body != null) {
			sb.append(body);
		}
		sb.append("\n");
		return sb.toString();
	}
	
	protected void genHeaders(HttpExchange exchange) {
		this.headers = exchange.getRequestHeaders();
	}
	
	protected void genAuthentications(HttpExchange exchange) {
		try {
			List<String> val = exchange.getRequestHeaders().get("Authorization");
			if (val != null) {
				if (val.get(0).startsWith("Basic ")) {
					String base64Str = val.get(0).substring(6);
					String str = new String(Base64.getDecoder().decode(base64Str), "utf-8");
					String[] parts = str.split(":");
					authentications[0] = parts[0];
					authentications[1] = parts[1];
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	protected void genBody(HttpExchange exchange) throws IOException {
		this.body = convertor.rawRequestToBody(exchange);
	}
	
	public String getTopLine() {
		return this.topLine;
	}
	
	public String getHeaderLine(String header) {
		List<String> values = headers.get(header);
		StringBuffer value = new StringBuffer();
		if (values != null) {
			for (int i = 1; i <= values.size(); i++) {
				value.append(values.get(i - 1));
				if (i != values.size())
					value.append(",");
			}
		}
		return SimUtils.formatString(HEADER_LINE_FORMAT, header, value.toString());
	}
	
	public String getAutnenticationLine() {
		return SimUtils.formatString(AUTHENTICATION_LINE_FORMAT, authentications);
	}
	
	public String getBody() {
		return this.body;
	}
	
	@Override
	protected void doFillResponse(SimResponse response) throws IOException {
		Headers respHeaders = httpExchange.getResponseHeaders();

		for (Map.Entry<String, Object> entry : response.getHeaders().entrySet()) {
			respHeaders.add(entry.getKey(), entry.getValue().toString());
		}
		byte[] body = response.getBody();
		long length = body.length;
		List<String> values = respHeaders.get("Transfer-Encoding");
		if (values != null && values.contains("chunked")) {
			SimLogger.getLogger().info("set contentLength to 0 since TransferEncoding is chunked");
			length = 0;
		}
		httpExchange.sendResponseHeaders(response.getCode(), length);
		convertor.fillRawResponse(httpExchange, response);
	}

	@Override
	public List<String> getAllHeaderNames() {
		return new ArrayList<>(headers.keySet());
	}
}
