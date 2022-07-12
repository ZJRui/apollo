/*
 * Copyright 2022 Apollo Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.ctrip.framework.apollo.spring.boot;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.ApolloClientSystemConsts;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.utils.DeferredLogger;
import com.ctrip.framework.apollo.spring.config.CachedCompositePropertySource;
import com.ctrip.framework.apollo.spring.config.ConfigPropertySourceFactory;
import com.ctrip.framework.apollo.spring.config.PropertySourcesConstants;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

/**
 * Initialize apollo system properties and inject the Apollo config in Spring Boot bootstrap phase
 *
 * <p>Configuration example:</p>
 * <pre class="code">
 *   # set app.id
 *   app.id = 100004458
 *   # enable apollo bootstrap config and inject 'application' namespace in bootstrap phase
 *   apollo.bootstrap.enabled = true
 * </pre>
 *
 * or
 *
 * <pre class="code">
 *   # set app.id
 *   app.id = 100004458
 *   # enable apollo bootstrap config
 *   apollo.bootstrap.enabled = true
 *   # will inject 'application' and 'FX.apollo' namespaces in bootstrap phase
 *   apollo.bootstrap.namespaces = application,FX.apollo
 * </pre>
 *
 *
 * If you want to load Apollo configurations even before Logging System Initialization Phase,
 *  add
 * <pre class="code">
 *   # set apollo.bootstrap.eagerLoad.enabled
 *   apollo.bootstrap.eagerLoad.enabled = true
 * </pre>
 *
 *  This would be very helpful when your logging configurations is set by Apollo.
 *
 *  for example, you have defined logback-spring.xml in your project, and you want to inject some attributes into logback-spring.xml.
 *
 */
@SuppressWarnings("all")
public class ApolloApplicationContextInitializer implements
    ApplicationContextInitializer<ConfigurableApplicationContext> , EnvironmentPostProcessor, Ordered {
  public static final int DEFAULT_ORDER = 0;

  private static final Logger logger = LoggerFactory.getLogger(ApolloApplicationContextInitializer.class);
  private static final Splitter NAMESPACE_SPLITTER = Splitter.on(",").omitEmptyStrings()
      .trimResults();
  public static final String[] APOLLO_SYSTEM_PROPERTIES = {ApolloClientSystemConsts.APP_ID,
      ApolloClientSystemConsts.APOLLO_LABEL,
      ApolloClientSystemConsts.APOLLO_CLUSTER,
      ApolloClientSystemConsts.APOLLO_CACHE_DIR,
      ApolloClientSystemConsts.APOLLO_ACCESS_KEY_SECRET,
      ApolloClientSystemConsts.APOLLO_META,
      ApolloClientSystemConsts.APOLLO_CONFIG_SERVICE,
      ApolloClientSystemConsts.APOLLO_PROPERTY_ORDER_ENABLE,
      ApolloClientSystemConsts.APOLLO_PROPERTY_NAMES_CACHE_ENABLE,
      ApolloClientSystemConsts.APOLLO_OVERRIDE_SYSTEM_PROPERTIES};

  /**
   *
   * 这个属性
   *
   *
   */
  private final ConfigPropertySourceFactory configPropertySourceFactory = SpringInjector
      .getInstance(ConfigPropertySourceFactory.class);

  private int order = DEFAULT_ORDER;

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    /**
     *
     * spring.factories文件中，包含两个启动会注入的类。首先看ApolloApplicationContextInitializer。
     */
    ConfigurableEnvironment environment = context.getEnvironment();

    if (!environment.getProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_ENABLED, Boolean.class, false)) {
      logger.debug("Apollo bootstrap config is not enabled for context {}, see property: ${{}}", context, PropertySourcesConstants.APOLLO_BOOTSTRAP_ENABLED);
      return;
    }
    logger.debug("Apollo bootstrap config is enabled for context {}", context);

    initialize(environment);
  }


  /**
   * Initialize Apollo Configurations Just after environment is ready.
   *
   * @param environment
   */
  protected void initialize(ConfigurableEnvironment environment) {

    /**
     * SpringBoot启动的时候被调用。 且 ApplicationContextInitializer 本身不需要 @Component注册到 容器中，
     * 而是在spring.factories配置文件中配置 有哪些ApplicationContextInitialzer实现
     *
     * initialize:70, ApolloApplicationContextInitializer (com.ctrip.framework.apollo.spring.boot)
     * applyInitializers:628, SpringApplication (org.springframework.boot)
     * prepareContext:364, SpringApplication (org.springframework.boot)
     * run:305, SpringApplication (org.springframework.boot)
     * run:1242, SpringApplication (org.springframework.boot)
     * run:1230, SpringApplication (org.springframework.boot)
     * main:41, ChatroomServiceApplication (com.yupaopao.hug.chatroom)
     *
     *-----------
     * 关于ApplicationContextinitializer
     * 在刷新之前初始化Spring ConfigurableApplicationContext的回调接口。
     * 通常用于需要对应用程序上下文进行一些程序化初始化的web应用程序。例如，针对上下文环境注册属性源或激活配置文件。请参见ContextLoader和FrameworkServlet对分别声明“contextInitializerClasses”上下文参数和初始化参数的支持。
     * ApplicationContextInitializer处理器被鼓励检测Spring的Ordered接口是否已经实现，或者是否存在@Order注释，并在调用之前相应地对实例进行排序
     *
     */

    if (environment.getPropertySources().contains(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
      //already initialized, replay the logs that were printed before the logging system was initialized
      DeferredLogger.replayTo();
      return;
    }

    String namespaces = environment.getProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_NAMESPACES, ConfigConsts.NAMESPACE_APPLICATION);
    logger.debug("Apollo bootstrap namespaces: {}", namespaces);
    List<String> namespaceList = NAMESPACE_SPLITTER.splitToList(namespaces);

    CompositePropertySource composite;
    final ConfigUtil configUtil = ApolloInjector.getInstance(ConfigUtil.class);
    if (configUtil.isPropertyNamesCacheEnabled()) {
      composite = new CachedCompositePropertySource(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME);
    } else {
      composite = new CompositePropertySource(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME);
    }
    for (String namespace : namespaceList) {
      Config config = ConfigService.getConfig(namespace);
      /**
       *   		  // 通过ConfigPropertySourceFactory创建ConfigPropertySource
       *   		  注意 ApolloApplicationContextIntializer 中有一个属性
       *
       *
       *   private final ConfigPropertySourceFactory configPropertySourceFactory = SpringInjector
       *       .getInstance(ConfigPropertySourceFactory.class);
       *
       */
      composite.addPropertySource(configPropertySourceFactory.getConfigPropertySource(namespace, config));
    }
    if (!configUtil.isOverrideSystemProperties()) {
      if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
        environment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, composite);
        return;
      }
    }
    /**
     * Environment的getPropertySources中有一个Propertyist 保存 所有加在的propertySource
     * [ConfigurationPropertySourcesPropertySource {
     * 	name = 'configurationProperties'
     * }, StubPropertySource {
     * 	name = 'servletConfigInitParams'
     * }, StubPropertySource {
     * 	name = 'servletContextInitParams'
     * }, MapPropertySource {
     * 	name = 'systemProperties'
     * }, OriginAwareSystemEnvironmentPropertySource {
     * 	name = 'systemEnvironment'
     * }, RandomValuePropertySource {
     * 	name = 'random'
     * }, OriginTrackedMapPropertySource {
     * 	name = 'applicationConfig: [classpath:/application.properties]'
     * }]
     *
     * 最后面一个是application.properties
     *
     * 然后这里 apollo加载的配置 被addFirst，因此优先级上 apollo 加载的配置 高于 application.properties 中的配置属性
     *
     * 在getProperty的时候
     * org.springframework.core.env.PropertySourcesPropertyResolver#getProperty(java.lang.String, java.lang.Class, boolean)
     * 会顺序从propertySource 中读取属性，如果读取到那么后面的propertSource就不会查找了
     *
     *
     * 对于Apollo 他创建的PropertySource在读取属性的时候 最终会执行
     * com.ctrip.framework.apollo.internals.DefaultConfig#getProperty(java.lang.String, java.lang.String)
     *
     *  // *********************************************************************
     * 		// 向容器中注册:PropertySource,而且,还是放在首位,意味着:编写顺序在最后的namespace具有优先查找功能.
     * 		// 比如: namespaces = [TEST1.jdbc, application]
     * 		// 而 environment.getPropertySources().addFirst()后的结果是:  [application, TEST1.jdbc]
     * 		//      key = server.port
     * 		//     先在:application查找,找不到,再到:TEST1.jdbc里查找.
     * 		// *********************************************************************
     *
     *
     */
    environment.getPropertySources().addFirst(composite);
  }

  /**
   * To fill system properties from environment config
   */
  void initializeSystemProperty(ConfigurableEnvironment environment) {
    for (String propertyName : APOLLO_SYSTEM_PROPERTIES) {
      fillSystemPropertyFromEnvironment(environment, propertyName);
    }
  }

  private void fillSystemPropertyFromEnvironment(ConfigurableEnvironment environment, String propertyName) {
    if (System.getProperty(propertyName) != null) {
      return;
    }

    String propertyValue = environment.getProperty(propertyName);

    if (Strings.isNullOrEmpty(propertyValue)) {
      return;
    }

    System.setProperty(propertyName, propertyValue);
  }

  /**
   *
   * In order to load Apollo configurations as early as even before Spring loading logging system phase,
   * this EnvironmentPostProcessor can be called Just After ConfigFileApplicationListener has succeeded.
   *
   * <br />
   * The processing sequence would be like this: <br />
   * Load Bootstrap properties and application properties -----> load Apollo configuration properties ----> Initialize Logging systems
   *
   * @param configurableEnvironment
   * @param springApplication
   */
  @Override
  public void postProcessEnvironment(ConfigurableEnvironment configurableEnvironment, SpringApplication springApplication) {

    // should always initialize system properties like app.id in the first place
    initializeSystemProperty(configurableEnvironment);

    Boolean eagerLoadEnabled = configurableEnvironment.getProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_EAGER_LOAD_ENABLED, Boolean.class, false);

    //EnvironmentPostProcessor should not be triggered if you don't want Apollo Loading before Logging System Initialization
    if (!eagerLoadEnabled) {
      return;
    }

    Boolean bootstrapEnabled = configurableEnvironment.getProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_ENABLED, Boolean.class, false);

    if (bootstrapEnabled) {
      DeferredLogger.enable();
      initialize(configurableEnvironment);
    }

  }

  /**
   * @since 1.3.0
   */
  @Override
  public int getOrder() {
    return order;
  }

  /**
   * @since 1.3.0
   */
  public void setOrder(int order) {
    this.order = order;
  }

}
