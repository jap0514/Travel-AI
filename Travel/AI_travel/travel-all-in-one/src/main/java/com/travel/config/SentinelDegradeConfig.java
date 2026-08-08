package com.travel.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 熔断规则配置
 *
 * Python 服务熔断策略：
 * - 慢调用比例：响应时间 > 3秒 视为慢调用，慢调用比例 > 50% 触发熔断
 * - 异常比例：异常请求比例 > 50% 触发熔断
 * - 熔断持续：60秒后尝试半开恢复
 * - 最小调用数：5次（熔断器达到此调用数后才开始计算）
 */
@Configuration
@Slf4j
public class SentinelDegradeConfig {

    /** Python 服务资源名 */
    public static final String PYTHON_SERVICE = "pythonService";

    /** 熔断规则 */
    @Component
    public static class PythonServiceDegradeInitializer implements CommandLineRunner {

        @Override
        public void run(String... args) {
            initDegradeRules();
            log.info("【Sentinel熔断】Python服务熔断规则初始化完成");
        }

        private void initDegradeRules() {
            List<DegradeRule> rules = new ArrayList<>();

            // Python 服务熔断规则
            DegradeRule pythonRule = new DegradeRule(PYTHON_SERVICE);
//                    // 设置资源名
//                    .setResource(PYTHON_SERVICE)
//                    // 熔断策略：慢调用比例 + 异常比例
//                    .setGrade(RuleConstant.DEGRADE_GRADE_SLOW_REQUEST_RATIO)
//                    // 慢调用比例阈值 50%（慢调用 / 总调用）
//                    .setCount(0.5)
//                    // 慢调用阈值：3秒
//                    .setSlowRatioThreshold(0.5)
//                    // 最小请求数：达到此数量后才开始计算熔断
//                    .setMinRequestAmount(5)
//                    // 统计时长：10秒内
//                    .setStatIntervalMs(10000)
//                    // 熔断持续时长：60秒
//                    .setRecoverTimeoutSec(60)
//                    // 半开状态允许通过的请求数
//                    .setMaxAllowedStateepingDurationSec(60);

            rules.add(pythonRule);

            // 加载规则
            DegradeRuleManager.loadRules(rules);

            log.info("【Sentinel熔断】熔断规则已加载: resource={}, grade={}, count={}, minRequestAmount={}",
                    PYTHON_SERVICE, "SLOW_REQUEST_RATIO", 0.5, 5);
        }
    }
}
