package org.jingle.simulator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.jingle.simulator.util.SimLogger;
import org.jingle.simulator.util.SimUtils;
import org.jingle.simulator.util.ZipFileVisitor;
import org.jingle.simulator.util.ZipFileVisitor.EntryWrapper;
import org.jingle.simulator.util.ZipFileVisitor.ZipFileVisitorHandler;

public class SimScript {
	public static class TemplatePair {
		private SimRequestTemplate req;
		private SimResponseTemplate[] resps;
		
		public TemplatePair(SimRequestTemplate req, SimResponseTemplate ... resps) {
			this.req = req;
			this.resps = resps;
		}

		public SimRequestTemplate getReq() {
			return req;
		}

		public SimResponseTemplate[] getResps() {
			return resps;
		}
	}
	
	private static final Logger msgLogger = Logger.getLogger("messageLogger");
	
	private static final String SEP_LINE = "------------------------------------------------------------------"; 
	private static final String RESP_START = "HTTP/"; 
	public static final String SCRIPT_EXT = ".sim"; 
	public static final String SCRIPT_ZIP = ".zip"; 
	public static final String INIT_FILE = "init.properties"; 
	public static final String PROP_NAME_SIMULATOR_CLASS = "simulator.class"; 
	public static final String PROP_NAME_SIMULATOR_NAME = "simulator.name"; 
	public static final String PROP_NAME_SIMULATOR_AUTOSTART = "simulator.autostart"; 
	public static final String PROP_NAME_SIMULATOR_URL = "simulator.url"; 
	
	private Logger scriptLogger = null;

	private List<TemplatePair> templatePairs = new ArrayList<>();
	private Map<String, SimScript> subScripts = new HashMap<>();
	private PropertiesConfiguration config = new PropertiesConfiguration();
	
	public SimScript(SimScript parent, File file) throws IOException {
		config.copy(parent.getConfig());
		config.setProperty(PROP_NAME_SIMULATOR_URL, file.toURI());
		templatePairs.addAll(parent.getTemplatePairs());
		loadFolder(file, true); 
	}
	
	public SimScript(SimScript parent, final ZipFile zf, File file) throws IOException {
		config.copy(parent.getConfig());
		config.setProperty(PROP_NAME_SIMULATOR_URL, "jar:" + file.toURI() + "!/");
		templatePairs.addAll(parent.getTemplatePairs());
		ZipFileVisitorHandler<SimScript> handler = new ZipFileVisitorHandler<SimScript>() {

			private EntryWrapper<SimScript> parent;
			@Override
			public void setParent(EntryWrapper<SimScript> parent) {
				this.parent = parent;
			}

			@Override
			public EntryWrapper<SimScript> handleDir(ZipEntry entry) {
				SimScript sim = new SimScript(parent.getTarget(), entry);
				File file = new File(entry.getName());
				subScripts.put(file.getName(), sim);
				return new EntryWrapper<SimScript>(entry, sim);
			}

			@Override
			public void handleFile(ZipEntry entry) throws IOException {
				String name = new File(entry.getName()).getName();
				if (name.equals(INIT_FILE)) {
					SimLogger.getLogger().info("loadint init file [" + name + "] in [" + parent.getEntry() + "]");
					try (BufferedReader reader = new BufferedReader(new InputStreamReader(zf.getInputStream(entry)))) {
						PropertiesConfiguration propConfig = new PropertiesConfiguration();
						propConfig.read(reader);
						parent.getTarget().config.copy(propConfig);
					} catch (ConfigurationException e) {
						throw new IOException(e);
					} 
				} else if (name.endsWith(SCRIPT_EXT)) {
					SimLogger.getLogger().info("loading script file [" + name + "] in [" + parent.getEntry() + "]");
					try (BufferedReader reader = new BufferedReader(new InputStreamReader(zf.getInputStream(entry), "utf-8"))) {
						List<TemplatePair> pairList = load(reader); 
						parent.getTarget().templatePairs.addAll(0, pairList);
					}
				}
			}
		};
		ZipFileVisitor<SimScript> visitor = new ZipFileVisitor<SimScript>(zf, this, handler);
		visitor.visit();
	}

	public SimScript(File file) throws IOException {
		config.setProperty(PROP_NAME_SIMULATOR_URL, file.toURI());
		loadFolder(file, false); 
	}
	
	SimScript(SimScript parent, ZipEntry entry) {
		config.copy(parent.getConfig());
		templatePairs.addAll(parent.getTemplatePairs());
		config.setProperty(PROP_NAME_SIMULATOR_URL, parent.getProperty(PROP_NAME_SIMULATOR_URL) + entry.getName());
	}
	
	protected SimScript() {
		
	}

	public boolean isValid() {
		if (getSimulatorName() == null) {
			return false;
		}
		return true;
	}
	public void prepareLogger() {
		String simulatorName = this.getSimulatorName();

		this.scriptLogger = Logger.getLogger(simulatorName);
		String appenderName = simulatorName + "_file";
		if (scriptLogger.getAppender(appenderName) == null) {
			RollingFileAppender appender = new org.apache.log4j.RollingFileAppender();
			appender.setName(simulatorName + "_file");
			PatternLayout layout = new org.apache.log4j.PatternLayout();
			layout.setConversionPattern("%d{yyyy-MM-dd HH:mm:ss,SSS} [%24F:%-4L:%-5p][%x] -%m%n");
			appender.setLayout(layout);
			try {
				appender.setFile("logs/" + simulatorName + ".log", true, true, 500);
			} catch (IOException e) {
			}
			appender.activateOptions();	
			scriptLogger.addAppender(appender);
		}
		for (SimScript subScript: subScripts.values()) {
			subScript.scriptLogger = scriptLogger;
		}
	}
	
	protected void loadFolder(File folder, boolean includeSubFolder) throws IOException {
		if (!folder.exists() || !folder.isDirectory()) {
			throw new IOException("no folder [" + folder + "] exists");
		}
		File[] files = folder.listFiles(new FileFilter() {

			@Override
			public boolean accept(File file) {
				if (file.isFile() && (file.getName().endsWith(SCRIPT_EXT) || file.getName().equals(INIT_FILE)) || (includeSubFolder && file.isDirectory())) {
					return true;
				}
				return false;
			}
		});
		
		List<File> fileList = Arrays.asList(files);
		Collections.sort(fileList, new Comparator<File>() {
			@Override
			public int compare(File file1, File file2) {
				int n1 = file1.isFile() ? 0 : 1;
				int n2 = file2.isFile() ? 0 : 1;
				return n1 - n2;
			}
		});
		
		int total = 0;
		for (File file: fileList) {
			if (file.isFile()) {
				if (INIT_FILE.equals(file.getName())) {
					SimLogger.getLogger().info("loadint init file [" + file.getName() + "] in [" + folder + "]");
					try {
						config.copy(new Configurations().properties(file));
					} catch (ConfigurationException e) {
						throw new IOException(e);
					}
				} else {
					SimLogger.getLogger().info("loading script file [" + file + "] in [" + folder + "]");
					try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
						List<TemplatePair> pairList = load(reader); 
						templatePairs.addAll(0, pairList);
						total += pairList.size();
					}
				}
			} else {
				subScripts.put(file.getName(), new SimScript(this, file));
			}
		}
		SimLogger.getLogger().info("Total " + total + " req/resp pairs loaded in [" + folder + "]");
	}
	
	public Properties getConfigAsProperties() {
		Properties props = new Properties();
		Iterator<String> keyIt = config.getKeys();
		while (keyIt.hasNext()) {
			String key = keyIt.next();
			props.setProperty(key, config.getString(key));
		}
		return props;
	}
	
	public Configuration getConfig() {
		return this.config;
	}
	
	public String getProperty(String propName) {
		return this.config.getString(propName);
	}

	public String getMandatoryProperty(String propName, String errMsg) {
		String ret = config.getString(propName);
		if (ret == null) {
			throw new RuntimeException(errMsg);
		}
		return ret;
	}

	public int getMandatoryIntProperty(String propName, String errMsg) {
		try {
			return config.getInt(propName);
		} catch (Exception e) {
			throw new RuntimeException(errMsg, e);
		}
	}

	@SuppressWarnings("unchecked")
    public Class<? extends SimSimulator> getSimulatorClass() throws ClassNotFoundException {
		return (Class<? extends SimSimulator>) Class.forName(config.getString(PROP_NAME_SIMULATOR_CLASS));
	}

	public String getSimulatorName() {
		return config.getString(PROP_NAME_SIMULATOR_NAME);
	}

	protected List<TemplatePair> load(BufferedReader reader) throws IOException {
		List<TemplatePair> pairList = new ArrayList<>();
		List<List<String>> block = null;
		int count = 0;
		while ((block = loadReqResp(reader)) != null) {
			SimResponseTemplate[] respTemplates = new SimResponseTemplate[block.size() - 1];
			for (int i = 1; i <= block.size() - 1; i++) {
				respTemplates[i - 1] = new SimResponseTemplate(SimUtils.concatContent(block.get(i)));
			}
			pairList.add(new TemplatePair(new SimRequestTemplate(SimUtils.concatContent(block.get(0))), respTemplates));
			count++;
		}
		SimLogger.getLogger().info(count + " req/resp pairs loaded");
		return pairList;
	}
	
	public List<TemplatePair> getTemplatePairs() {
		return this.templatePairs;
	}
	
	public Logger getLogger() {
		return this.scriptLogger;
	}
	
	protected List<List<String>> loadReqResp(BufferedReader reader) throws IOException {
		List<List<String>> ret = new ArrayList<>();
		List<String> lines = new ArrayList<>();
		String line = null;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith(RESP_START)) {
				addLines(ret, lines);
				lines = new ArrayList<>();
				lines.add(line);
			} else if (SEP_LINE.equals(line)) {
				break;
			} else {
				lines.add(line);
			}
		}
		addLines(ret, lines);
		
		if (ret.size() == 1) {
			throw new IOException("Last request has no response");
		} else if (ret.size() == 0) {
			return null;
		} else {
			return ret;
		} 
	}
	
	private void addLines(List<List<String>> block, List<String> lines) {
		if (lines.size() != 0) {
			if (lines.get(0).isEmpty()) {
				lines.remove(0);
			}
		}
		if (lines.size() != 0) {
			if (lines.get(lines.size() - 1).isEmpty()) {
				lines.remove(lines.size() - 1);
			}
		}
		if (!lines.isEmpty()) {
			block.add(lines);//resp
		}
	}
	
	public Map<String, SimScript> getSubScripts() {
		return subScripts;
	}
	
	public List<SimResponse> genResponse(SimRequest request) throws IOException {
		for (TemplatePair pair: templatePairs) {
			Map<String, Object> context = pair.getReq().match(request);
			if (context != null) {
				SimLogger.getLogger().info("match with template: [" + pair.getReq().getTopLineTemplate() + "]");
				Map<String, Object> allContext = new HashMap<>();
				Iterator<String> keyIt = config.getKeys();
				while (keyIt.hasNext()) {
					String key = keyIt.next();
					allContext.put(key, config.getString(key));
				}
				allContext.putAll(context);
				allContext.putAll(request.getReqRespConvertor().getRespContext());
				List<SimResponse> ret = new ArrayList<>();
				for (SimResponseTemplate respTemplate: pair.getResps()) {
					SimResponse response = new SimResponse(allContext, respTemplate);
					request.fillResponse(response);
					ret.add(response);
				}
				return ret;
			}
		}
		msgLogger.info(request);
		msgLogger.info(SEP_LINE);
		throw new IOException("Can not match request: [" + request.getTopLine() + "]");
	}
}
	
	