package com.example.backend.service;

import com.example.backend.dto.HelloResponse;
import com.example.backend.repository.HelloRepository;
import org.springframework.stereotype.Service;

@Service
public class HelloService {

  private final HelloRepository helloRepository;

  public HelloService(HelloRepository helloRepository) {
    this.helloRepository = helloRepository;
  }

  public HelloResponse getHello() {
    String message = helloRepository.fetchHelloMessage();
    String source = "backend";
    long timestamp = System.currentTimeMillis();

    return new HelloResponse(message, source, timestamp);
  }

  public HelloResponse postHello(String message) {
    String response = helloRepository.sendHelloRequest(message);
    String source = "backend";
    long timestamp = System.currentTimeMillis();

    return new HelloResponse(response, source, timestamp);
  }
}
