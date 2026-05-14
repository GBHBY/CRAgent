package cn.gyb.llm.cr.agent.skill;

import cn.gyb.llm.cr.agent.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class SkillRefreshScheduler {

    @Autowired
    private SkillService skillService;

    //@Scheduled(fixedDelayString = "${skill.refresh-interval:300000}")
    //public void refreshAllSkills() {
    //    log.info("开始定时刷新技能...");
    //    try {
    //        skillService.refreshAllSkills();
    //        log.info("定时刷新技能完成");
    //    } catch (Exception e) {
    //        log.error("定时刷新技能失败: {}", e.getMessage(), e);
    //    }
    //}
}
