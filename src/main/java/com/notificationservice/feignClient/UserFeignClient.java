package com.notificationservice.feignClient;

import com.notificationservice.commondtos.UserEmailDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "USER-SERVICE")
public interface UserFeignClient {

	@GetMapping("/api/auth/v1/getUser1ById/{userId}")
	UserEmailDto getUser1ById(@PathVariable("userId") Long userId);
}