package co.com.bancolombia.mongo;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

@Configuration
@ConditionalOnProperty(
        prefix = "arka.catalog",
        name = "use-in-memory-repository",
        havingValue = "false",
        matchIfMissing = false
)
public class MongoConfig {

    @Value("${MONGODB_URI:mongodb://localhost:27017/arka_catalog}")
    private String mongoUri;

    @Bean
    @Primary
    public MongoClient reactiveMongoClient() {
        return MongoClients.create(mongoUri);
    }

    @Bean
    @Primary
    public ReactiveMongoTemplate reactiveMongoTemplate(MongoClient mongoClient) {
        return new ReactiveMongoTemplate(mongoClient, "arka_catalog");
    }
}
