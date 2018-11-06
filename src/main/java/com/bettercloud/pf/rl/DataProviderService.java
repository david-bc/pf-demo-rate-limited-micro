package com.bettercloud.pf.rl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javafaker.Faker;
import com.google.common.base.Joiner;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class DataProviderService {

    public static final String USER_PATH = "/users";
    public static final String POST_PATH = "/posts";
    public static final String COMMENT_PATH = "/comments";
    public static final Pattern PATH_PATTERN = Pattern.compile(String.format("(?:%s|%s|%s)", USER_PATH, POST_PATH, COMMENT_PATH));


    private final Joiner SENTENCE_JOINER = Joiner.on(". ");
    private final ObjectMapper jsonObjectMapper;
    private final Faker faker;

    public DataProviderService(ObjectMapper jsonObjectMapper, Faker faker) {
        this.jsonObjectMapper = jsonObjectMapper;
        this.faker = faker;
    }

    public boolean supports(String path) {
        return PATH_PATTERN.matcher(path).find();
    }

    public JsonNode get(String path) {
        switch (path) {
            case USER_PATH:
                return this.getUser();
            case POST_PATH:
                return this.getPost();
            case COMMENT_PATH:
                return this.getComment();
        }
        return MissingNode.getInstance();
    }

    protected JsonNode getUser() {
        String email = faker.internet().emailAddress();
        ObjectNode user = jsonObjectMapper.createObjectNode()
                .put("email", email)
                .put("id", faker.number().numberBetween(100, 1000))
                .put("name", faker.name().fullName())
                .put("phone", faker.phoneNumber().phoneNumber())
                .put("username", email)
                .put("website", faker.internet().domainName());
        user.set("address", jsonObjectMapper.createObjectNode()
                        .put("city", faker.address().cityName())
                        .put("street", faker.address().streetAddress())
                        .put("zipcode", faker.address().zipCode())
                        .set("geo", jsonObjectMapper.createObjectNode()
                                .put("lat", faker.address().latitude())
                                .put("lon", faker.address().longitude())
                        )
                );
        user.set("compoany", jsonObjectMapper.createObjectNode()
                .put("bs", faker.superhero().power())
                .put("cathPhrase", faker.chuckNorris().fact())
                .put("name", faker.superhero().name())
        );
        return user;
    }

    protected JsonNode getPost() {
        return jsonObjectMapper.createObjectNode()
                .put("body", SENTENCE_JOINER.join(faker.lorem().sentences((int) (Math.random() * 2) + 2)))
                .put("id", faker.number().numberBetween(100000, 1000000))
                .put("title", faker.lorem().sentence())
                .put("userId", faker.number().numberBetween(10000, 100000));
    }

    protected JsonNode getComment() {
        return jsonObjectMapper.createObjectNode()
                .put("body", SENTENCE_JOINER.join(faker.lorem().sentences((int) (Math.random() * 2) + 2)))
                .put("email", faker.internet().emailAddress())
                .put("id", faker.number().numberBetween(100, 1000))
                .put("name", faker.lorem().sentence())
                .put("postId", faker.number().numberBetween(10000, 100000));
    }
}
