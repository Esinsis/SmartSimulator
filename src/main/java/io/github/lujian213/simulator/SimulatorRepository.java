package io.github.lujian213.simulator;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipFile;

import io.github.lujian213.simulator.monitor.MessageCounter;
import io.github.lujian213.simulator.util.SimLogger;

public class SimulatorRepository {
	private Map<String, SimSimulator> simulatorMap = new HashMap<>();
	private File folder;
	private static SimulatorRepository inst = null;

	SimulatorRepository(File folder) throws IOException {
		this.folder = folder;
		refresh();
		inst = this;
	}

	public static SimulatorRepository getInstance() {
		return inst;
	}
	
	protected List<SimScript> loadScripts(File folder) throws IOException {
		List<SimScript> ret = new ArrayList<>();
		SimScript script = new SimScript(folder);
		script.init();
		
		File[] files = folder.listFiles(new FileFilter() {

			@Override
			public boolean accept(File file) {
				if (file.isDirectory() || file.getName().endsWith(SimScript.ZIP_EXT)) {
					return true;
				}
				return false;
			}
		});
		for (File file : files) {
			SimScript simScript = null;
			if (file.isDirectory()) {
				simScript = new SimScript(script, file);
			} else {
				simScript = new SimScript(script, new ZipFile(file), file);
			}
			if (!simScript.isValid()) {
				SimLogger.getLogger().info(file.getName() + " is not a valid script folder/file, skip ...");
			} else if (simScript.isIgnored()) {
				SimLogger.getLogger().info(file.getName() + " is ignored");
			}else {
				simScript.init();
				ret.add(simScript);
			}
		}
		return ret;
	}
	
	public List<SimSimulator> getAllSimulators() {
		List<SimSimulator> ret = new ArrayList<>();

		synchronized (simulatorMap) {
			ret.addAll(simulatorMap.values());
		}
		return ret;
	}

	public SimSimulator getSimulator(String name) {
		SimSimulator simulator = null;
		synchronized (simulatorMap) {
			simulator = simulatorMap.get(name);
		}
		if (simulator == null) {
			throw new RuntimeException("no such simulator [" + name + "]");
		}
		return simulator;
	}

	public SimSimulator startSimulator(String name) throws IOException {
		synchronized (simulatorMap) {
			SimSimulator sim = simulatorMap.get(name);
			if (sim == null) {
				throw new RuntimeException("no such simulator [" + name + "]");
			}
			if (!sim.isRunning()) {
				sim.start();
			}
			SimLogger.getLogger().info("Simulator [" + name + "] is running at " + sim.getRunningURL());
			return sim;
		}
	}

	public SimSimulator stopSimulator(String name) {
		synchronized (simulatorMap) {
			SimSimulator sim = simulatorMap.get(name);
			if (sim == null) {
				throw new RuntimeException("no such simulator [" + name + "]");
			}
			if (sim.isRunning()) {
				sim.stop();
			}
			SimLogger.getLogger().info("Simulator [" + name + "] is stopped ");
			return sim;
		}
	}

	public void refresh() throws IOException {
		List<SimScript> scripts = loadScripts(folder);
		synchronized (simulatorMap) {
			Iterator<Map.Entry<String, SimSimulator>> it = simulatorMap.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, SimSimulator> entry = it.next();
				if (!entry.getValue().isRunning()) {
					SimLogger.getLogger().info("Simulator [" + entry.getKey() + "] is not running, remove ...");
					it.remove();
				}
			}
			for (SimScript script : scripts) {
				SimSimulator simulator = simulatorMap.get(script.getSimulatorName());
				if (simulator == null) {
					simulator = SimSimulator.createSimulator(script);
					simulator.addListener(MessageCounter.getInstance());
					simulatorMap.put(simulator.getName(), simulator);
					SimLogger.getLogger().info("Simulator [" + simulator.getName() + "] loaded");
				}
			}
		}
	}

	public SimSimulator restartSimulator(String name) throws IOException {
		List<SimScript> scripts = loadScripts(folder);
		synchronized (simulatorMap) {
			SimSimulator sim = simulatorMap.get(name);
			if (sim != null) {
				if (sim.isRunning()) {
					sim.stop();
					SimLogger.getLogger().info("wait for few seconds ...");
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
					}
				}
			}
			Optional<SimScript> opSc = scripts.stream().filter((script) -> name.equals(script.getSimulatorName())).findFirst();
			if (!opSc.isPresent()) {
				throw new RuntimeException("no such simulator [" + name + "]");
			}
			sim = SimSimulator.createSimulator(opSc.get());
			sim.addListener(MessageCounter.getInstance());
			simulatorMap.put(sim.getName(), sim);
			sim.start();
			SimLogger.getLogger().info("Simulator [" + name + "] is running at " + sim.getRunningURL());
			return sim;
		}
	}

	public SimScript getSimulatorScript(String simulatorFolder) throws IOException {
		SimScript root = new SimScript(this.folder);
		if (simulatorFolder == null) {
			return root;
		} else {
			File[] files = this.folder.listFiles(file -> file.isDirectory() && file.getName().equals(simulatorFolder) || file.isFile() && file.getName().equals(simulatorFolder + ".zip"));
			if (files != null && files.length > 0) {
				if (files[0].isDirectory())
					return new SimScript(root, files[0]);
				else
					return new SimScript(root, new ZipFile(files[0]), files[0]);
			}
		}
		throw new RuntimeException("no such simulator folder [" + simulatorFolder + "]");
	}
}
