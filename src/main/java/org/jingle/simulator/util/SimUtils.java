package org.jingle.simulator.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.TextMessage;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jingle.simulator.SimRequest;
import org.jingle.simulator.SimResponse;
import org.jingle.simulator.SimScript;
import org.jingle.simulator.SimSimulator;
import org.jingle.simulator.jms.JMSSimRequest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class SimUtils {
    private static VelocityEngine ve = new VelocityEngine();
    static {
    	ve.init();
    }

	
	public static String decodeURL(String url) throws UnsupportedEncodingException {
		String[] parts = url.split("\\?");
		StringBuffer sb = new StringBuffer(parts[0]);
		String queryStr = parts.length > 1 ? parts[1] : null;
		if (queryStr != null) {
			sb.append("?");
			final String[] pairs = queryStr.split("&");
			int count = 1;
			for (String pair : pairs) {
				final int idx = pair.indexOf("=");
				final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
				if (count > 1) {
					sb.append("&");
				}
				sb.append(key).append("=");
				final String value = idx > 0 && pair.length() > idx + 1
						? URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
						: null;
				if (value != null) {
					sb.append(value);
				}
				count++;
			}
		}
		return sb.toString();
	}

	public static String mergeResult(VelocityContext context, String tagName, String templateStr) throws IOException {
		try (StringWriter writer = new StringWriter()) {
			ve.evaluate(context, writer, tagName, templateStr);
			return writer.toString();
		}
	}

	public static String mergeResult(VelocityContext context, String tagName, byte[] template) throws IOException {
		try (Reader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(template)))) {
			try (StringWriter writer = new StringWriter()) {
				ve.evaluate(context, writer, tagName, reader);
				return writer.toString();
			}
		}
	}

	public static String formatString(String format, String ... args) {
		StringWriter sbw = new StringWriter();
		try (PrintWriter pw = new PrintWriter(sbw)) {
			pw.printf(format, args);
		}
		return sbw.toString();
	}
	
	public static String[] parseProperty(String line) {
		int index = line.indexOf(':');
		if (index != -1) {
			String propName = line.substring(0, index);
			String propValue = line.substring(index + 1);
			return new String[] {propName.trim(), propValue.trim()};
		}
		return null;
	}
	
    public static SSLContext initSSL(String keystore, String ksPwd) throws IOException {
    	SimLogger.getLogger().info("Start initializing SSL");

        try (InputStream ksis = new FileInputStream(keystore)){
        	SimLogger.getLogger().info("Initializing SSL from built-in default certificate");
           
            char[] passphrase = ksPwd.toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(ksis, passphrase);
           
            SimLogger.getLogger().info("SSL certificate loaded");
           
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);
            SimLogger.getLogger().info("Key manager factory is initialized");

//            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
//            tmf.init(ks);
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SimLogger.getLogger().info("Trust manager factory is initialized");

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), trustAllCerts, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            SimLogger.getLogger().info("SSL context is initialized");
            return sslContext;
        } catch (Exception ioe) {
        	SimLogger.getLogger().error("error when init SSLContext", ioe);
            throw new IOException(ioe);
        } 
    }
    
    public static SimResponse doHttpProxy(String proxyURL, SimRequest request) throws IOException {
    	SimLogger.getLogger().info("do proxy ...");
    	String topLine = request.getTopLine();
    	int firstIndex = topLine.indexOf(' ');
    	int lastIndex = topLine.lastIndexOf(' ');
    	String method = topLine.substring(0,  firstIndex).trim();
    	String urlStr = proxyURL + topLine.substring(firstIndex + 1, lastIndex).trim();
    	SimLogger.getLogger().info("url=" + urlStr);
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			SimLogger.getLogger().info("encoded url=" + encodeURL(urlStr));
			URL url = new URL(encodeURL(urlStr));
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			for (String headerName: request.getAllHeaderNames()) {
				String headerLine = request.getHeaderLine(headerName);
				String[] headerParts = headerLine.split(":");
				conn.setRequestProperty(headerParts[0].trim(), headerParts[1].trim());
			}
			conn.setRequestMethod(method);
			conn.connect();
			String body = request.getBody();
			if (body != null && !body.isEmpty()) {
				conn.setDoOutput(true);
				try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(conn.getOutputStream()))) {
						pw.print(request.getBody());
				}
			}
			SimLogger.getLogger().info("response ...");
			try (BufferedInputStream bis = new BufferedInputStream(conn.getInputStream())) {
				byte[] buffer = new byte[8 * 1024];
				int count = -1;
				while ((count = bis.read(buffer)) != -1) {
					baos.write(buffer, 0, count);
				}
			}
			Map<String, Object> headers = new HashMap<>();
			for (Map.Entry<String, List<String>> entry: conn.getHeaderFields().entrySet()) {
				if (entry.getKey() != null) {
					headers.put(entry.getKey(), String.join(",", entry.getValue()));
				}
			}
			baos.flush();
			return new SimResponse(conn.getResponseCode(), headers, baos.toByteArray());
		}
    }
    
    public static SimResponse doJMSProxy(String proxyURL, JMSSimRequest request) throws IOException {
    	SimLogger.getLogger().info("do proxy ...");
		Map<String, Object> headers = new HashMap<>();
		try {
			TextMessage message = (TextMessage) request.getMessage();
			Enumeration<String> it = message.getPropertyNames();
			while (it.hasMoreElements()) {
				String propName = it.nextElement();
				headers.put(propName, message.getObjectProperty(propName));
			}
			headers.put(JMSSimRequest.HEADER_NAME_CHANNEL, proxyURL);
			return new SimResponse(200, headers, message.getText().getBytes());
		} catch (JMSException e) {
			SimLogger.getLogger().error(e);
			throw new IOException(e);
		}
    }

    public static String encodeURL(String url) throws UnsupportedEncodingException {
    	int index = url.indexOf('?');
    	if (index < 0) {
    		return url;
    	} else {
    		StringBuffer sb = new StringBuffer(url.substring(0, index + 1));
    		String queryPart = url.substring(index + 1);
    		String[] pairs = queryPart.split("&");
    		for (int i = 1; i <= pairs.length; i++) {
    			int index2 = pairs[i - 1].indexOf('=');
    			if (i != 1) {
    				sb.append("&");
    			}
    			String key = pairs[i - 1].substring(0, index2);
    			String value = pairs[i - 1].substring(index2 + 1);
    			sb.append(URLEncoder.encode(key)).append("=").append(URLEncoder.encode(value));
    		}
    		return sb.toString();
    	}
    }
    
	public static String concatContent(List<String> lines) {
		if (lines.size() > 0) {
			StringBuffer sb = new StringBuffer();
			for (int i = 1; i <= lines.size(); i++) {
				sb.append(lines.get(i - 1));
				if (i != lines.size()) {
					sb.append("\n");
				}
			}
			return sb.toString();
		}
		return null;
	}

	public static Map.Entry<String, String> parseHeaderLine(String headerLine)  {
		Map<String, String> map = new HashMap<>();
		int index = headerLine.indexOf(':');
		String name = headerLine.substring(0, index).trim();
		String value = headerLine.substring(index + 1).trim();
		map.put(name,  value);
		return map.entrySet().iterator().next();
	}
	
	public static void printMismatchInfo(String msg, String s1, String s2) {
		Logger logger = SimLogger.getLogger();
		logger.info(msg);
		logger.info("[" + (s1 == null? null : s1.trim()) + "]");
		logger.info("VS");
		logger.info("[" + (s2 == null? null : s2.trim()) + "]");
	}
	
	public static ReqRespConvertor createMessageConvertor(SimScript script, ReqRespConvertor defaultConvertor) {
		String convertorClassName = script.getProperty(SimSimulator.PROP_NAME_MESSAGE_CONVERTOR);
		if (convertorClassName != null) {
			try {
				return (ReqRespConvertor) Class.forName(convertorClassName).newInstance();
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				throw new RuntimeException("error to create convertor instance [" + convertorClassName + "]", e);
			}
		} else {
			return defaultConvertor;
		}
	}
	
	public static ByteBuf[] parseDelimiters(String delimitersStr) {
		String parts[] = delimitersStr.split(",");
		byte[][] ret = new byte[parts.length][];
		for (int i = 1; i <= ret.length; i++) {
			String[] dels = parts[i - 1].trim().split("0x");
			ret[i - 1] = new byte[dels.length - 1];
			for (int t = 1; t <= dels.length - 1; t++) {
				ret[i - 1][t - 1] = new BigInteger(dels[t], 16).byteValue();
			}
		}
		ByteBuf[] bb = new ByteBuf[ret.length];
		for (int i = 1; i <= ret.length; i++) {
			bb[i - 1] = Unpooled.wrappedBuffer(ret[i - 1]);
		}
		return bb;
	}
	
	public static String getCurrentTime(String format) {
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		return sdf.format(new Date());
	}

	public static String transformTime(String format1, String strTime, String format2) {
		SimpleDateFormat sdf1 = new SimpleDateFormat(format1);
		SimpleDateFormat sdf2 = new SimpleDateFormat(format2);
		try {
			return sdf2.format(sdf1.parse(strTime));
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	public static String transformTime(String format, Date date) {
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		return sdf.format(date);
	}

	public static Date transformTime(String format, String strTime) {
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		try {
			return sdf.parse(strTime);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}
}
