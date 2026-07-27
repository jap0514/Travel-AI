package com.travel.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 限流规则配置
 *
 * 配置说明：
 * - 使用代码方式配置限流规则（也可通过 Sentinel Dashboard 动态配置）
 * - 规则存储在内存中，服务重启后需重新配置
 * - 生产环境建议配合 Sentinel Dashboard 使用
 *
 * 默认规则：
 * - sendMessage: 100 QPS
 * - createOrder: 50 QPS
 * - getHotelByCity: 200 QPS
 */
@Configuration
@Slf4j
public class SentinelConfig {

    /**
     * 初始化 Sentinel 限流规则
     * 实现 CommandLineRunner 确保在应用启动后执行
     */
    @Component
    public static class SentinelRuleInitializer implements CommandLineRunner {

        @Override
        public void run(String... args) {
            initializeFlowRules();
            log.info("【Sentinel】限流规则初始化完成");
        }

        /**
         * 初始化限流规则
         */
        private void initializeFlowRules() {
            List<FlowRule> rules = new ArrayList<>();

            // 消息发送接口：100 QPS
            rules.add(createRule("ChatController:sendMessage", 100));

            // 订单创建接口：50 QPS
            rules.add(createRule("HotelController:createOrder", 50));

            // 酒店查询接口：200 QPS
            rules.add(createRule("HotelController:getHotelByCity", 200));

            // AI消息回调接口：100 QPS
            rules.add(createRule("ChatController:aiMessageCallback", 100));

            // 加载规则
            FlowRuleManager.loadRules(rules);
        }

        /**
         * 创建限流规则
         *
         * @param resource 资源名（接口方法名）
         * @param count 每秒允许的最大请求数
         * @return FlowRule
         */
        private FlowRule createRule(String resource, int count) {
            FlowRule rule = new FlowRule();
            rule.setResource(resource);                    // 资源名
            rule.setGrade(RuleConstant.FLOW_GRADE_QPS);   //限流模式：QPS
            rule.setCount(count);                          //阈值
            rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
            // 控制行为：
            // - CONTROL_BEHAVIOR_DEFAULT: 直接拒绝
            // - CONTROL_BEHAVIOR_WARM_UP: 冷启动（渐进式放行）
            // - CONTROL_BEHAVIOR_RATE_LIMITTER: 匀速排队
            return rule;
        }
    }
}
