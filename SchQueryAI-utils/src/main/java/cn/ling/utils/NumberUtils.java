package cn.ling.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class NumberUtils {
    public long generateDigitCode(int digit) {
        // 校验位数合法性
        if (digit < 1 || digit > 18) {
            throw new IllegalArgumentException("验证码位数必须在1-18之间");
        }

        // 计算最小值和最大值（例如：3位则是100-999）
        long min = (long) Math.pow(10, digit - 1);
        long max = (long) Math.pow(10, digit) - 1;

        // 特殊处理1位数（0-9）
        if (digit == 1) {
            min = 0;
        }

        // 生成[min, max]范围内的随机数
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}
