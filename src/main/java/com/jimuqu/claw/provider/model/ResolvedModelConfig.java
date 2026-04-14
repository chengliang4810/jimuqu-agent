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
public class ResolvedModelConfig {
    private String modelAlias;
    private String providerName;
    private String dialect;
    private String baseUrl;
    private String apiKey;
    private String token;
    private String model;
    private Long timeoutMs;
    private Double temperature;
    private Long maxTokens;
    private Long maxOutputTokens;
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<String, String>();
}
