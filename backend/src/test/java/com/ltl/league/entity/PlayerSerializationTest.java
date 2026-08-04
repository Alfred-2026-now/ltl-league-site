package com.ltl.league.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlayerSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializingPlayerDoesNotExposePassword() throws Exception {
        Player player = new Player();
        player.setId(1L);
        player.setName("test-player");
        player.setPassword("e10adc3949ba59abbe56e057f20f883e");

        JsonNode json = objectMapper.valueToTree(player);

        assertEquals("test-player", json.get("name").asText());
        assertFalse(json.has("password"));
    }
}
