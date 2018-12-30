package org.jingle.simulator.monitor;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.jingle.simulator.SimRequest;
import org.jingle.simulator.SimResponse;
import org.jingle.simulator.SimulatorListener;

public class MessageCounter implements SimulatorListener {
	public static class Counter {
		private String name;
		private long startTime = System.currentTimeMillis();
		private int successfulMessages = 0;
		private int failedMessages = 0;
		
		public Counter(String name) {
			this.name = name;
		}
		
		
		public String getName() {
			return name;
		}

		public long getStartTime() {
			return startTime;
		}
		
		public long getDuration() {
			return System.currentTimeMillis() - startTime;
		}
		
		public int getSuccessfulMessages() {
			return successfulMessages;
		}
		
		public int getFailedMessages() {
			return failedMessages;
		}
		
		public synchronized void addSuccessfullMessage() {
			successfulMessages += 1;
		}

		public synchronized void addFailedMessage() {
			failedMessages += 1;
		}
	}
	
	private static MessageCounter instance = new MessageCounter();
	
	private ConcurrentHashMap<String, Counter> counterMap = new ConcurrentHashMap<>();
	
	MessageCounter() {
		
	}
	
	public static MessageCounter getInstance() {
		return instance;
	}
	
	@Override
	public void onStart(String simulatorName) {
		Counter counter = new Counter(simulatorName);
		counterMap.put(simulatorName, counter);
	}

	@Override
	public void onStop(String simulatorName) {
		counterMap.remove(simulatorName);
	}

	@Override
	public void onHandleMessage(String simulatorName, SimRequest request, List<SimResponse> responseList,
			boolean status) {
		Counter counter = counterMap.get(simulatorName);
		if (status) {
			counter.addSuccessfullMessage();
		} else {
			counter.addFailedMessage();
		}
	}
	
	public Counter getCounter(String simulatorName) {
		return counterMap.get(simulatorName);
	}
}
