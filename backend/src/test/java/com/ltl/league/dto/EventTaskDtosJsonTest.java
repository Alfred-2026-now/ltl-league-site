package com.ltl.league.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EventTaskDtosJsonTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void taskWriteRequestReadsCamelCasePReward() throws Exception {
        String json = "{\"title\":\"测试任务\",\"requirements\":\"玩一把大虫子\","
                + "\"pReward\":100,\"bountyReward\":0,\"budgetNote\":\"最多5个人\"}";

        EventTaskDtos.TaskWriteRequest request = objectMapper.readValue(
                json, EventTaskDtos.TaskWriteRequest.class);

        assertEquals(100, request.getPReward());
        assertEquals(0, request.getBountyReward());
    }

    @Test
    void taskResponsesWriteCamelCasePReward() throws Exception {
        EventTaskDtos.TaskVO task = new EventTaskDtos.TaskVO();
        task.setPReward(100);
        EventTaskDtos.ClaimVO claim = new EventTaskDtos.ClaimVO();
        claim.setPReward(80);

        JsonNode taskJson = objectMapper.readTree(objectMapper.writeValueAsString(task));
        JsonNode claimJson = objectMapper.readTree(objectMapper.writeValueAsString(claim));

        assertEquals(100, taskJson.get("pReward").asInt());
        assertEquals(80, claimJson.get("pReward").asInt());
        assertFalse(taskJson.has("preward"));
        assertFalse(claimJson.has("preward"));
    }
}
