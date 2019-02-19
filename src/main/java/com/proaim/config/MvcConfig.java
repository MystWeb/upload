package com.proaim.config;

import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

//实现WebMvcConfigurer接口可以来扩展SpringMVC的功能
//@EnableWebMvc    不要接管SpringMVC
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    //定制嵌入式的Servlet容器相关的规则
    @Bean
    public WebServerFactoryCustomizer<ConfigurableWebServerFactory> webServerFactoryCustomizer() {
        return new WebServerFactoryCustomizer<ConfigurableWebServerFactory>() {
            @Override
            public void customize(ConfigurableWebServerFactory factory) {
                factory.setPort(8080);
            }
        };
    }

    // 日期转换器
    /*@Bean
    public Converter<String, Date> addNewConvert() {
        return new Converter<String, Date>() {
            @Override
            public Date convert(String s) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date date = null;
                try {
                    date = dateFormat.parse(s);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                return date;
            }
        };
    }*/

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        //浏览器发送 /index 请求来到 index页面 拦截器不会拦截！
        registry.addViewController("/").setViewName("index");
        registry.addViewController("/index").setViewName("index");
    }

}
