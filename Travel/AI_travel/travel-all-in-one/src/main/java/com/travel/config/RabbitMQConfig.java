//package com.travel.config;
//
//import org.springframework.amqp.core.Binding;
//import org.springframework.amqp.core.BindingBuilder;
//import org.springframework.amqp.core.DirectExchange;
//import org.springframework.amqp.core.Queue;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class RabbitMQConfig {
//    @Bean
//    public Queue travelTaskQueue(){
//        //队列名为travel.task.queue  并且设置持久化
//        return new Queue("travel.task.queue",true);
//    }
//
//    @Bean
//    public DirectExchange travelTaskExchange(){
//        //交换机名
//        return new DirectExchange("travel.task.exchange",true,false);
//    }
//
//    @Bean
//    public Binding travelTaskBinding(){
//        //将队列和交换机绑定，并指定路由键
//        return BindingBuilder.bind(travelTaskQueue())
//                .to(travelTaskExchange())
//                .with("travel.task");
//    }
//}
