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
package com.ctrip.framework.apollo.spring.config;

public interface PropertySourcesConstants {
  String APOLLO_PROPERTY_SOURCE_NAME = "ApolloPropertySources";
  String APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME = "ApolloBootstrapPropertySources";

  /**
   *这里需要注意下，apollo.bootstrap.enabled这个参数设不设置均可。
   *
   * apollo.bootstrap.enabled=true，代表springboot在启动阶段就会加载，具体在SpringApplication类prepareContext方法中执行
   * applyInitializers(context)时会加载。会在创建Bean之前。
   *
   * apollo.bootstrap.enabled=false，代表在springboot启动时不会加载配置，而是通过apollo注入PropertySourcesProcessor对象
   * 时开始加载，这时加载的配置可能并不一定是apollo的配置信息了，因为其他Bean对象可能提前加载，注入的Value属性就有可能是application.yml中的。
   *
   */
  String APOLLO_BOOTSTRAP_ENABLED = "apollo.bootstrap.enabled";
  String APOLLO_BOOTSTRAP_EAGER_LOAD_ENABLED = "apollo.bootstrap.eagerLoad.enabled";
  String APOLLO_BOOTSTRAP_NAMESPACES = "apollo.bootstrap.namespaces";
}
