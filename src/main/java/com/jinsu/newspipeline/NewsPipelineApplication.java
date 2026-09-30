package com.jinsu.newspipeline;

import com.jinsu.newspipeline.config.AnthropicProperties;
import com.jinsu.newspipeline.config.RssProperties;
import com.jinsu.newspipeline.config.TelegramProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({RssProperties.class, AnthropicProperties.class, TelegramProperties.class})
public class NewsPipelineApplication {

	public static void main(String[] args) {
		SpringApplication.run(NewsPipelineApplication.class, args);
	}

}
