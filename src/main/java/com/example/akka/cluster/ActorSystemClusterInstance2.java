package com.example.akka.cluster;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.Cluster;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.SingletonActor;
import com.example.akka.actor.BehaviorConfig;
import com.example.akka.actor.FilterActor;
import com.example.akka.actor.ProcessActor;
import com.example.akka.message.DevicePropertyMessage;
import com.example.akka.message.IDeviceMessage;
import com.example.akka.message.StopMessage;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import scala.collection.Set;

import java.util.HashMap;
import java.util.Map;

/**
 * akka集群实例1
 *
 * @author mujiang
 * @version 2021/07/02 17:43
 */
@Slf4j
public class ActorSystemClusterInstance2 {

    public static class RootBehaviorFilterGroupRouter {
        static Behavior create() {
            return Behaviors.setup(context -> {

                // actor的配置
                BehaviorConfig filterConfig = new BehaviorConfig("power > 2000");
                // 创建filter behavior
                Behavior<IDeviceMessage> filterBehavior = FilterActor.create(filterConfig, null);

                // 注册到filter Pool Router 到某个key
                ServiceKey<IDeviceMessage> filterServiceKey = ServiceKey.create(IDeviceMessage.class, "FilterGroupRouterKey");
                for(int i = 4; i <= 5; i++){
                    ActorRef<IDeviceMessage> filterActorRef = context.spawn(filterBehavior, "FilterActor" + i);
                    context.getSystem().receptionist().tell(Receptionist.register(filterServiceKey, filterActorRef.narrow()));
                }

                ServiceKey<IDeviceMessage> filterServiceKey2 = ServiceKey.create(IDeviceMessage.class, "FilterGroupRouterKey");
                DevicePropertyMessage message = DevicePropertyMessage.builder()
                        .thingId("D007")
                        .current(3.58)
                        .voltage(380.00)
                        .build();
                Behavior<IDeviceMessage> filterGroupBehavior = Routers.group(filterServiceKey2).withRoundRobinRouting();
//                Behavior<IDeviceMessage> filterGroupBehavior = Routers.group(filterServiceKey2);
                ActorRef<IDeviceMessage> filterGroupRouterRef = context.spawn(filterGroupBehavior, "FilterGroupRouter");

                Thread.sleep(3 * 1000);
                log.info("-----------");
                // 给 router发送消息
                for (int i = 0; i < 10; i++) {
                    filterGroupRouterRef.tell(message);
                }
                return Behaviors.empty();
            });
        }
    }

    public static class RootBehaviorWithGroupRouter {
        static Behavior create() {
            return Behaviors.setup(context -> {

                ClusterSingleton singleton = ClusterSingleton.get(context.getSystem());

                BehaviorConfig filterConfig = new BehaviorConfig("power > 2000");

                // 创建filter的behavior
                Behavior<IDeviceMessage> filterBehavior = FilterActor.create(filterConfig, null);
                // 创建单例actor
                SingletonActor<IDeviceMessage> filterSingletonActor = SingletonActor.of(filterBehavior, "GlobalFilter");
                ActorRef globalFilter = singleton.init(filterSingletonActor);
                DevicePropertyMessage message = DevicePropertyMessage.builder()
                        .thingId("D007")
                        .current(3.58)
                        .voltage(380.00)
                        .build();

                Thread.sleep(10 * 1000);

                log.info("=== ********** ===");
                globalFilter.tell(message);

                System.out.println(context.getSystem().printTree());

//                SingletonActor<IDeviceMessage> filterActor = SingletonActor.apply(FilterActor.create(filterConfig, null), "GlobalFilter").withStopMessage(StopMessage.INSTANCE);

//                ActorRef globalFilter = singleton.init(filterActor);

//                ActorRef globalFilter2 =   singleton.init(
//                        SingletonActor.of(
//                                Behaviors.supervise(FilterActor.create(filterConfig, null))
//                                        .onFailure(
//                                                SupervisorStrategy.restartWithBackoff(
//                                                        Duration.ofSeconds(1), Duration.ofSeconds(10), 0.2)),
//                                "GlobalFilter"));

//                Thread.sleep(10 * 1000);
//
//                BehaviorConfig processConfig = new BehaviorConfig("power = current * voltage");
//                // 创建process的behavior(下一个节点指向 pool router 而不是 普通actor)
//                Behavior<IDeviceMessage> processBehavior = ProcessActor.create(processConfig, null);
//                // 实例化actor
//                ActorRef<IDeviceMessage> processActorRef = context.spawn(processBehavior, "Process");
//
//                DevicePropertyMessage message = DevicePropertyMessage.builder()
//                        .thingId("D007")
//                        .current(3.58)
//                        .voltage(380.00)
//                        .build();
//
//                Thread.sleep(2 * 1000);
//
//                for(int i=0; i < 10; i++){
//                    message.setMsgId(i);
//                    processActorRef.tell(message);
//                    Thread.sleep(3 * 1000);
//                }
//
//                log.info("=== {} ===", context.getSystem().printTree());

                return Behaviors.empty();
            });
        }
    }

    public static void main(String[] args) {
//      akka系统启动的端口
        String port = "5002";

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("akka.remote.artery.canonical.port", port);

        Config config = ConfigFactory.parseMap(overrides)
                .withFallback(ConfigFactory.load("cluster"));

//        ActorSystem<Void> system = ActorSystem.create(RootBehaviorFilterGroupRouter.create(), "cluster-example", config);
        ActorSystem<Void> system = ActorSystem.create(RootBehaviorWithGroupRouter.create(), "cluster-example", config);

//
//        try {
//            Thread.sleep(5000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        log.info("=== {} ===", system.printTree());
//        Cluster cluster = Cluster.get(system);
//        log.info("=== {} ===", cluster.readView().leader());
//        log.info("=== {} ===", cluster.readView().members());
        log.info("=== actor system started ===");


    }

}
