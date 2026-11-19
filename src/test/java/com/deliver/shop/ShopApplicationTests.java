package com.deliver.shop;

import com.deliver.shop.application.interfaces.repository.CategoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ShopApplicationTests {

	@Autowired
	private MeterRegistry registry;

	@Autowired
	private CategoryRepository categoryRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void databasePerformanceMetricsAreRegistered() {
		categoryRepository.count();

		assertThat(registry.find("hikaricp.connections.max").gauge()).isNotNull();
		assertThat(registry.find("spring.data.repository.invocations")
				.tags("repository", "CategoryRepository", "method", "count").timer()).isNotNull();
	}

}
