package cn.ling.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 数字生成工具类
 * 提供随机数字码生成功能，支持指定位数的数字验证码生成
 * 使用ThreadLocalRandom确保在多线程环境下的高性能和线程安全
 * 主要用于短信验证码、邮箱验证码等场景的数字串生成
 */
@Slf4j
@Component
public class NumberUtils {

    /**
     * 生成指定位数的随机数字码
     * 生成包含指定位数的纯数字随机验证码，支持1-18位长度
     * 1位数生成0-9的随机数，多位数确保第一位不为零
     * 使用ThreadLocalRandom提供线程安全的随机数生成
     *
     * @param digit 数字码的位数，必须在1-18之间
     * @return 生成的随机数字码；如果位数为1返回0-9，多位数返回相应位数的随机数
     * @throws IllegalArgumentException 当位数不在1-18范围内时抛出异常
     */
    public long generateDigitCode(int digit) {
        log.debug("开始生成{}位随机数字码", digit);

        try {
            // 校验位数合法性，确保在合理范围内
            if (digit < 1 || digit > 18) {
                log.error("数字码位数无效: {}，有效范围: 1-18", digit);
                throw new IllegalArgumentException("验证码位数必须在1-18之间");
            }

            // 计算最小值和最大值
            // 例如：3位数则是100-999，确保多位数的第一位不为零
            long min = (long) Math.pow(10, digit - 1);
            long max = (long) Math.pow(10, digit) - 1;

            // 特殊处理1位数：允许生成0-9的任意数字
            if (digit == 1) {
                min = 0;
                max = 9;
                log.debug("1位数特殊处理，范围: [0-9]");
            } else {
                log.debug("{}位数范围: [{}-{}]", digit, min, max);
            }

            // 使用ThreadLocalRandom生成[min, max]范围内的随机数
            // ThreadLocalRandom在多线程环境下性能优于Math.random()
            long randomCode = ThreadLocalRandom.current().nextLong(min, max + 1);

            log.info("成功生成{}位随机数字码: {}", digit, randomCode);
            log.debug("数字码详情 - 位数: {}, 最小值: {}, 最大值: {}, 生成结果: {}",
                    digit, min, max, randomCode);

            return randomCode;

        } catch (IllegalArgumentException e) {
            // 重新抛出参数异常
            log.error("生成随机数字码参数错误: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // 捕获其他异常并转换为运行时异常
            log.error("生成随机数字码时发生未知异常: {}", e.getMessage(), e);
            throw new RuntimeException("生成随机数字码失败", e);
        }
    }
}
