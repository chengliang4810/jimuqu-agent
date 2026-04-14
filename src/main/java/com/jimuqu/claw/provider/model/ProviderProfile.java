package com.jimuqu.claw.provider.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderProfile {
    private String name;
    private String dialect;
    private String baseUrl;
    private String apiKey;
    private String token;
    private Long timeoutMs;
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<String, String>();
}
