package com.whu.software.athena.cognitionagent.model;

/**
 * Single model transport boundary shared by every cognition workflow node.
 * Node-specific prompts and parsers live outside this gateway.
 */
public interface ModelGateway {

    String providerName();

    String modelName();

    ModelResponse complete(ModelRequest request);
}
