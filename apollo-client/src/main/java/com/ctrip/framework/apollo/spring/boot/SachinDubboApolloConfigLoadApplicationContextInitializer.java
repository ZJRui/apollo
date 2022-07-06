package com.ctrip.framework.apollo.spring.boot;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.spring.config.ConfigPropertySourceFactory;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.ctrip.framework.foundation.Foundation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

public class SachinDubboApolloConfigLoadApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {


    private static final Logger LOGGER = LoggerFactory.getLogger(ApolloApplicationContextInitializer.class);
    public static String CONFIG_NAMESPACE = "dubbo.middleware";

    private final ConfigPropertySourceFactory configPropertySourceFactory = SpringInjector.getInstance(ConfigPropertySourceFactory.class);
    private final static String dubboConfigNameSpace = "dubbo.config.namespace";


    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        String enabled = environment.getProperty("dubbo.apollo.disable", "false");
        if (Boolean.parseBoolean(enabled)) {
           // LOGGER.info("Dubbo禁用Apollo远程配置");
            return;
        }
        String namespace = System.getProperty(dubboConfigNameSpace, CONFIG_NAMESPACE);
        String beanName = "ApolloDubboPropertySources#" + namespace;
        if (environment.getPropertySources().contains(beanName)) {
            return;
        }
        CompositePropertySource composite = new CompositePropertySource(beanName);
        LOGGER.info("从Apollo[{}]读取Dubbo配置", namespace);
        Config config = StringUtils.isEmpty(namespace) ? ConfigService.getAppConfig() : ConfigService.getConfig(namespace);
        // 用户未设置dubbo.application.name时使用apollo app id
        if (StringUtils.isEmpty(config.getProperty("dubbo.application.name", null))) {
            String appId = Foundation.app().getAppId();
            if (StringUtils.isEmpty(appId)) {
                LOGGER.warn("未设置dubbo.application.name");
            } else {
                LOGGER.info("使用apollo app id作为dubbo.application.name:{}", appId);
                System.setProperty("dubbo.application.name", appId);
            }
        }
        int dubboShutdownWait = config.getIntProperty("dubbo.service.shutdown.wait", 90000);
        System.setProperty("dubbo.service.shutdown.wait", dubboShutdownWait + "");
        LOGGER.info("注入来自Apollo的Dubbo配置");
        composite.addPropertySource(configPropertySourceFactory.getConfigPropertySource(namespace, config));
        //addLast 方便本地覆盖
        environment.getPropertySources().addLast(composite);
    }
}

