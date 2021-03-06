package in.projecteka.consentmanager.link;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import in.projecteka.consentmanager.clients.DiscoveryServiceClient;
import in.projecteka.consentmanager.clients.LinkServiceClient;
import in.projecteka.consentmanager.clients.UserServiceClient;
import in.projecteka.consentmanager.clients.properties.GatewayServiceProperties;
import in.projecteka.consentmanager.clients.properties.LinkServiceProperties;
import in.projecteka.consentmanager.common.CentralRegistry;
import in.projecteka.consentmanager.common.IdentityService;
import in.projecteka.consentmanager.common.cache.CacheAdapter;
import in.projecteka.consentmanager.common.cache.LoadingCacheAdapter;
import in.projecteka.consentmanager.common.cache.RedisCacheAdapter;
import in.projecteka.consentmanager.link.discovery.Discovery;
import in.projecteka.consentmanager.link.discovery.DiscoveryRepository;
import in.projecteka.consentmanager.link.link.Link;
import in.projecteka.consentmanager.link.link.LinkRepository;
import in.projecteka.consentmanager.user.UserServiceProperties;
import io.lettuce.core.RedisClient;
import io.vertx.pgclient.PgPool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class LinkConfiguration {

    @Bean
    public DiscoveryRepository discoveryRepository(PgPool pgPool) {
        return new DiscoveryRepository(pgPool);
    }

    @Bean
    public LinkRepository linkRepository(PgPool pgPool) {
        return new LinkRepository(pgPool);
    }

    @Bean
    public Link link(WebClient.Builder builder,
                     LinkRepository linkRepository,
                     CentralRegistry centralRegistry,
                     GatewayServiceProperties gatewayServiceProperties,
                     LinkServiceProperties serviceProperties,
                     CacheAdapter<String, String> linkResults) {
        return new Link(
                new LinkServiceClient(builder, centralRegistry, gatewayServiceProperties),
                linkRepository,
                centralRegistry,
                serviceProperties,
                linkResults);
     }

    @Bean
    public DiscoveryServiceClient discoveryServiceClient(WebClient.Builder builder,
                                                         CentralRegistry centralRegistry,
                                                         GatewayServiceProperties gatewayServiceProperties) {
        return new DiscoveryServiceClient(builder, centralRegistry::authenticate, gatewayServiceProperties);
    }

    @Bean
    public UserServiceClient userServiceClient(WebClient.Builder builder,
                                               UserServiceProperties userServiceProperties,
                                               IdentityService identityService,
                                               GatewayServiceProperties gatewayServiceProperties,
                                               CentralRegistry centralRegistry) {
        return new UserServiceClient(builder,
                userServiceProperties.getUrl(),
                identityService::authenticate,
                gatewayServiceProperties,
                centralRegistry);
    }

    @Bean
    public Discovery discovery(DiscoveryRepository discoveryRepository,
                               CentralRegistry centralRegistry,
                               DiscoveryServiceClient discoveryServiceClient,
                               UserServiceClient userServiceClient,
                               LinkServiceProperties linkServiceProperties,
                               CacheAdapter<String, String> linkResults) {
        return new Discovery(userServiceClient,
                discoveryServiceClient,
                discoveryRepository,
                centralRegistry,
                linkServiceProperties,
                linkResults);
    }

    @ConditionalOnProperty(value = "consentmanager.cacheMethod", havingValue = "guava", matchIfMissing = true)
    @Bean({"discoveryResults", "linkResults"})
    public CacheAdapter<String, String> createLoadingCacheAdapter() {
        return new LoadingCacheAdapter(createSessionCache(5));
    }

    public LoadingCache<String, String> createSessionCache(int duration) {
        return CacheBuilder
                .newBuilder()
                .expireAfterWrite(duration, TimeUnit.MINUTES)
                .build(new CacheLoader<String, String>() {
                    public String load(String key) {
                        return "";
                    }
                });
    }

    @ConditionalOnProperty(value = "consentmanager.cacheMethod", havingValue = "redis")
    @Bean({"discoveryResults", "linkResults"})
    public CacheAdapter<String, String> createRedisCacheAdapter(RedisClient redisClient) {
        return new RedisCacheAdapter(redisClient, 5);
    }

}
