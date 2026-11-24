package com.cineconecta.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

// Collections, validators and indexes are created by the database/mongodb/*.js scripts.
// Auditing fills the createdAt/updatedAt fields that the validators require.
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
