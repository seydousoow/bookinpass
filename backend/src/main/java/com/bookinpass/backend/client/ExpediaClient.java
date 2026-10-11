package com.bookinpass.backend.client;

import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class ExpediaClient {

    private final RestClient client;

}
