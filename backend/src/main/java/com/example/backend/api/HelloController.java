package com.example.backend.api;

import com.example.backend.dto.HelloRequest;
import com.example.backend.dto.HelloResponse;
import com.example.backend.service.HelloService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class HelloController {

  private final HelloService helloService;

  public HelloController(HelloService helloService) {
    this.helloService = helloService;
  }

  @GetMapping("/api/getHello")
  public HelloResponse getHello() {
    return helloService.getHello();
  }

  @PostMapping("/api/postHello")
  public HelloResponse postHello(@RequestBody HelloRequest request) {
    log.info("Received message: {}", request.message());
    return helloService.postHello(request.message());
  }
}
