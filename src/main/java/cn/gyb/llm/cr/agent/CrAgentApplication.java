package cn.gyb.llm.cr.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.gyb.llm.cr.agent.mapper")
public class CrAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrAgentApplication.class, args);
    }
}
