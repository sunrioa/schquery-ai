package cn.ling.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus配置类
 * 配置MyBatis Plus的功能插件，包括分页插件等
 * 提供数据库操作的增强功能，简化开发
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    /**
     * 配置MyBatis Plus拦截器
     * 注册分页插件，实现数据库查询的自动分页功能
     *
     * @return MybatisPlusInterceptor 配置好的拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        log.info("开始配置MyBatis Plus拦截器");

        // 创建MyBatis Plus拦截器实例
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加分页插件，指定数据库类型为MySQL
        // 分页插件会自动处理分页查询，包括count查询和分页数据查询
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        interceptor.addInnerInterceptor(paginationInterceptor);

        log.info("MyBatis Plus分页插件配置完成 - 数据库类型: MySQL");

        return interceptor;
    }
}
