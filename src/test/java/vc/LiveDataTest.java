package vc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import vc.live.dto.ChatsRecord;
import vc.live.dto.ConnectionsRecord;
import vc.live.dto.DeathsRecord;

public class LiveDataTest {

//    @Test
    public void deserializeTest() throws JsonProcessingException {
        String connectionRaw = "{\"time\":1704951271.527492800,\"connection\":\"LEAVE\",\"playerName\":\"jmincraft\",\"playerUuid\":\"a7da973a-0f74-497a-80c2-213a4705655a\"}";
        String chatRaw = "{\"time\":1704951353.933700200,\"chat\":\"server lag\",\"playerName\":\"SteveEatsBread\",\"playerUuid\":\"1cf39dee-91c3-4f92-9994-78ab3b10ccdf\"}";
        String deathsRaw = "{\"time\":1704952072.880794600,\"deathMessage\":\"GrandSauce fell and was reduced into a jittering flesh pile.\",\"victimPlayerName\":\"GrandSauce\",\"victimPlayerUuid\":\"511dadf4-6fe3-431d-a03f-df2205e9da2a\",\"killerPlayerName\":null,\"killerPlayerUuid\":null,\"weaponName\":null,\"killerMob\":null}";
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule());
        ConnectionsRecord c = objectMapper.readValue(connectionRaw, ConnectionsRecord.class);
        ChatsRecord chat = objectMapper.readValue(chatRaw, ChatsRecord.class);
        DeathsRecord death = objectMapper.readValue(deathsRaw, DeathsRecord.class);

        System.out.println(c);
        System.out.println(chat);
        System.out.println(death);
    }
}
