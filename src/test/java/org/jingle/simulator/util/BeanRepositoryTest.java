package org.jingle.simulator.util;

import static org.junit.Assert.*;

import org.apache.velocity.VelocityContext;
import org.jingle.simulator.SimScript;
import org.jingle.simulator.util.function.SimConstructor;
import org.jingle.simulator.util.function.SimParam;
import org.jingle.simulator.util.function.SimulatorListener;
import org.junit.Test;

public class BeanRepositoryTest {
	public static class C1 {
		public C1() {
			
		}
	}
	
	public static class C2 {
	}
	
	public static class C3 {
		private String name;
		public C3(String name) {
			this.name = name;
		}
		
		public String getName() {
			return this.name;
		}
	}

	public static class C4 {
		private String name;
		@SimConstructor
		public C4(@SimParam("name") String name) {
			this.name = name;
		}
		
		public String getName() {
			return this.name;
		}
	}

	public static class C5 {
		private String name;
		@SimConstructor
		public C5(@SimParam("name") String name) {
			this.name = name;
		}
		
		public C5() {
		}

		public String getName() {
			return this.name;
		}
	}
	
	public static class C6 {
		private int num;
		@SimConstructor
		public C6(@SimParam("num") int num) {
			this.num = num;
		}
		
		public C6() {
		}

		public int getNum() {
			return this.num;
		}
	}

	public static class C7 implements SimulatorListener {
		private int num;
		@SimConstructor
		public C7(@SimParam("num") int num) {
			this.num = num;
		}
		
		public C7() {
		}

		public int getNum() {
			return this.num;
		}

		@Override
		public void onClose(String simulatorName) {
			num = 200;
		}
	}

	@Test
	public void testCreateInstance() {
		assertNotNull(BeanRepository.getInstance().createInstance(BeanRepositoryTest.C1.class, new VelocityContext()));
		assertNotNull(BeanRepository.getInstance().createInstance(BeanRepositoryTest.C2.class, new VelocityContext()));
		try {
			BeanRepository.getInstance().createInstance(BeanRepositoryTest.C3.class, new VelocityContext());
			fail("RuntimeException expected");
		} catch (RuntimeException e) {
			
		}

		VelocityContext vc = new VelocityContext();
		vc.put("name", "name1");
		C4 c4Inst = (C4) BeanRepository.getInstance().createInstance(BeanRepositoryTest.C4.class, vc);
		assertEquals("name1", c4Inst.getName());

		vc = new VelocityContext();
		vc.put("name", "name2");
		C5 c5Inst = (C5) BeanRepository.getInstance().createInstance(BeanRepositoryTest.C5.class, vc);
		assertEquals("name2", c5Inst.getName());

		vc = new VelocityContext();
		vc.put("num", "101");
		C6 c6Inst = (C6) BeanRepository.getInstance().createInstance(BeanRepositoryTest.C6.class, vc);
		assertEquals(101, c6Inst.getNum());
	}
	
	@Test
	public void testRemoveSimulatorBeans() {
		VelocityContext vc = new VelocityContext();
		vc.put("num", "101");
		vc.put(SimScript.PROP_NAME_SIMULATOR_NAME, "dummy");
		C7 c7Inst = (C7)BeanRepository.getInstance().addBean(BeanRepositoryTest.C7.class, vc);
		assertEquals(101, c7Inst.getNum());
		assertEquals(1, BeanRepository.getInstance().beanMap.size());
		assertEquals(1, BeanRepository.getInstance().beanMap.get("dummy").size());
		BeanRepository.getInstance().removeSimulatorBeans("dummy");
		assertEquals(0, BeanRepository.getInstance().beanMap.size());
		assertEquals(200, c7Inst.getNum());
	}

}