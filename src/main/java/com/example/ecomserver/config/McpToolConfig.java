package com.example.ecomserver.config;

import com.example.ecomserver.tools.EcommerceTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    /**
     * Spring AI's MCP server auto-configuration picks up ToolCallbackProvider beans
     * and exposes every @Tool-annotated method on the given objects as an MCP tool.
     */
    @Bean
    public ToolCallbackProvider ecommerceToolCallbackProvider(EcommerceTools ecommerceTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ecommerceTools)
                .build();
    }
}
