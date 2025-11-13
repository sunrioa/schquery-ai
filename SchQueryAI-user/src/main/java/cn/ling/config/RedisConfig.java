package cn.ling.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;

/**
 * Redis配置类
 * 配置RedisTemplate的序列化方式，解决默认序列化导致的数据存储和读取问题
 * 使用String序列化器存储key，Jackson序列化器存储value，确保数据的可读性和正确性
 */
@Slf4j
@Configuration
public class RedisConfig {

    /**
     * 自定义RedisTemplate配置
     * 配置Redis的key和value序列化方式，解决默认JDK序列化导致的乱码问题
     *
     * @param factory Redis连接工厂
     * @return 配置好的RedisTemplate实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        log.info("开始配置RedisTemplate序列化方式");

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 配置Jackson2JsonRedisSerializer序列化器，用于序列化value
        GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer(
                getObjectMapper()
        );

        // 配置StringRedisSerializer序列化器，用于序列化key
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // 配置序列化规则
        // key采用String序列化方式，确保在Redis中可读
        template.setKeySerializer(stringRedisSerializer);
        // hash的key也采用String序列化方式
        template.setHashKeySerializer(stringRedisSerializer);
        // value采用Jackson序列化方式，支持复杂对象存储
        template.setValueSerializer(jackson2JsonRedisSerializer);
        // hash的value也采用Jackson序列化方式
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();

        log.info("RedisTemplate配置完成 - Key:String序列化, Value:Jackson序列化");
        return template;
    }

    /**
     * 配置Jackson ObjectMapper
     * 设置ObjectMapper的序列化规则，解决Redis反序列化时的类型识别问题
     * 启用默认类型信息，确保复杂对象能够正确反序列化
     *
     * @return 配置好的ObjectMapper实例
     */
    private ObjectMapper getObjectMapper() {
        log.debug("配置Jackson ObjectMapper序列化规则");

        ObjectMapper mapper = new ObjectMapper();
        // 设置可见性，所有字段均可序列化和反序列化
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 启用默认类型信息，解决反序列化时的类型识别问题
        // NON_FINAL表示所有非final类型的对象都会包含类型信息
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        log.debug("ObjectMapper配置完成 - 启用默认类型信息，支持复杂对象反序列化");
        return mapper;
    }
}

