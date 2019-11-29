package com.wp.miaoshaproject.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * @author 罗叶妮
 * @version 1.0
 * @date 2019/11/25 18:35
 */
//定制化内嵌tomcat的开发
//当Spring容器中没有TomcatEmbeddedServletContainerFactory这个bean时
    //会把此bean加载进spring
@Component
public class WebServerConfiguration implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    //使用工厂类提供给我们的接口定制化我们的tomcat connector
    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        ((TomcatServletWebServerFactory)factory).addConnectorCustomizers(new TomcatConnectorCustomizer() {
            @Override
            public void customize(Connector connector) {
                Http11NioProtocol protocol =(Http11NioProtocol)connector.getProtocolHandler();
                //定制化keepalivetimeout,设置30秒内没有请求则服务的自动断开keepalive连接
                protocol.setKeepAliveTimeout(30000);
                //当客服端发送超过10000个请求则自动端口keepalive连接
                protocol.setMaxKeepAliveRequests(10000);
            }
        });
    }
}
