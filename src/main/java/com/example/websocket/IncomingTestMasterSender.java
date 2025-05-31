// src/main/java/com/example/websocket/IncomingTestMasterSender.java
package com.example.websocket;

import com.google.gson.JsonObject;

public interface IncomingTestMasterSender {
    void sendErrorToIncomingTestMaster(String requestId, String nodeId, String errorMessage);
    void forwardResponseToIncomingTestMaster(String nodeId, JsonObject response, String requestId);
}