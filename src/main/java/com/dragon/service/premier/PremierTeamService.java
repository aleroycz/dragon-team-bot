package com.dragon.service.premier;

import com.dragon.dto.premier.PremierTeamResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class PremierTeamService {

    @Value("${henrikdev.api.key}")
    private String apiKey;

    @Value("${valorant.team.name:DRAGON}")
    private String teamName;

    @Value("${valorant.team.tag:UWU}")
    private String teamTag;

    private static final String BASE_URL = "https://api.henrikdev.xyz";

    private final RestTemplate restTemplate;

    public PremierTeamResponse.PremierTeam fetchTeam() {
        String url = BASE_URL + "/valorant/v1/premier/" + teamName + "/" + teamTag;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);

        ResponseEntity<PremierTeamResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PremierTeamResponse.class
        );

        PremierTeamResponse body = response.getBody();
        if (body == null || body.data() == null) {
            log.error("Henrik API returned empty response for team {}/{}.", teamName, teamTag);
            return null;
        }

        log.info("Fetched premier team: {} #{} — division {} place {} pts {}",
                body.data().name(),
                body.data().tag(),
                body.data().placement().division(),
                body.data().placement().place(),
                body.data().placement().points());

        return body.data();
    }
}