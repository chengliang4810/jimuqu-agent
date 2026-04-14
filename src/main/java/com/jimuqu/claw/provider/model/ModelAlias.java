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
public class ModelAlias {
    private String name;
    private String providerProfile;
    private String model;
    private Double temperature;
    private Long maxTokens;
    private Long maxOutputTokens;
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<String, String>();
}
