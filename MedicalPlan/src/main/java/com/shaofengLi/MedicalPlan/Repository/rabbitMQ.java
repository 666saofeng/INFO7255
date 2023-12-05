package com.shaofengLi.MedicalPlan.Repository;

import com.shaofengLi.MedicalPlan.Service.Indexing;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;


@Configuration
public class rabbitMQ {
    public static final String QUEUE_NAME = "indexing-queue";
    public static final String EXCHANGE_NAME = "indexing-exchange";
    @Bean
    public Queue queue(){
        return new Queue(QUEUE_NAME,false);
    }
//    @Bean
//    public TopicExchange exchange(){
//        return new TopicExchange(EXCHANGE_NAME);
//    }
//    @Bean
//    Binding binding(Queue queue,TopicExchange exchange){
//        return BindingBuilder.bind(queue).to(exchange).with(QUEUE_NAME);
//    }
    @Bean
    public MessageListenerAdapter listenerAdapter(Indexing receiver){
        return new MessageListenerAdapter(receiver,"receiveMessage");
    }
    @Bean
    SimpleMessageListenerContainer container(ConnectionFactory connectionFactory,
                                             MessageListenerAdapter listenerAdapter){
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(QUEUE_NAME);
        container.setMessageListener(listenerAdapter);
        return container;
    }
}
