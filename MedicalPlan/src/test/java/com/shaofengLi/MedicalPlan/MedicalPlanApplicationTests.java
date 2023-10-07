package com.shaofengLi.MedicalPlan;

import com.shaofengLi.MedicalPlan.Service.MedicalPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import redis.clients.jedis.Jedis;

@SpringBootTest()
class MedicalPlanApplicationTests {
	@Autowired
	private MedicalPlanService medicalPlanService;
	Jedis jedis = new Jedis("localhost",6379);
	@Test
	void contextLoads() {
			medicalPlanService.deleteMedicalPlan("plan:12xvxc345ssdsds-508");
	}

}
